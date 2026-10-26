package io.github.jihu1130.ticketonline.domain.queue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.mockito.BDDMockito.given;

import io.github.jihu1130.ticketonline.domain.member.entity.Member;
import io.github.jihu1130.ticketonline.domain.product.entity.Product;
import io.github.jihu1130.ticketonline.domain.product.entity.ProductType;
import io.github.jihu1130.ticketonline.domain.product.repository.ProductRepository;
import io.github.jihu1130.ticketonline.domain.queue.dto.QueueResponse;
import io.github.jihu1130.ticketonline.domain.queue.dto.QueueStatus;
import io.github.jihu1130.ticketonline.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 대기열 순번·입장·빠지기를 실제 Redis(localhost:6379, 15번 DB)로 확인한다.
 * Redis 가 꺼져 있으면 건너뛴다 (C:\redis 에서 redis-server.exe redis.windows.conf).
 * 시각은 직접 넘겨서 1초마다 입장, 30초 미확인, 5분 입장 시간을 기다리지 않고 확인한다.
 */
class QueueServiceTest {

    private static final Long SELLER_ID = 1L;
    private static final Long PRODUCT_ID = 10L;
    private static final long T0 = 1_000_000_000L;

    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate redis;

    private final ProductRepository productRepository = Mockito.mock(ProductRepository.class);
    private final LocalDateTime now = LocalDateTime.now();

    @BeforeAll
    static void connect() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration("localhost", 6379);
        config.setDatabase(15);
        factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();
        factory.start();
        redis = new StringRedisTemplate(factory);
    }

    @AfterAll
    static void disconnect() {
        factory.destroy();
    }

    @BeforeEach
    void setUp() {
        boolean up;
        try {
            up = "PONG".equals(redis.execute(c -> c.ping(), true));
        } catch (Exception e) {
            up = false;
        }
        assumeThat(up).as("Redis 가 꺼져 있어 대기열 테스트를 건너뜀").isTrue();
        clear();
        product(now.minusDays(1), now.plusDays(1));
    }

    @AfterEach
    void tearDown() {
        try {
            clear();
        } catch (Exception ignored) {
            // Redis 가 꺼져 있어 건너뛴 경우
        }
    }

    @Test
    void enter_givesRankInArrivalOrder() {
        QueueService queue = queue(50);

        QueueResponse first = queue.enter(2L, PRODUCT_ID, T0);
        QueueResponse second = queue.enter(3L, PRODUCT_ID, T0 + 1);

        assertThat(first.status()).isEqualTo(QueueStatus.WAITING);
        assertThat(first.rank()).isEqualTo(1);
        assertThat(second.rank()).isEqualTo(2);
        assertThat(second.totalWaiting()).isEqualTo(2);
        assertThat(second.estimatedSeconds()).isEqualTo(1);
    }

    @Test
    void enterAgain_keepsFirstRank() {
        QueueService queue = queue(50);
        queue.enter(2L, PRODUCT_ID, T0);
        queue.enter(3L, PRODUCT_ID, T0 + 1);

        QueueResponse again = queue.enter(2L, PRODUCT_ID, T0 + 5);

        assertThat(again.rank()).isEqualTo(1);
        assertThat(again.totalWaiting()).isEqualTo(2);
    }

    @Test
    void admit_letsFrontPeopleInEachTick() {
        QueueService queue = queue(2);
        queue.enter(2L, PRODUCT_ID, T0);
        queue.enter(3L, PRODUCT_ID, T0 + 1);
        queue.enter(4L, PRODUCT_ID, T0 + 2);
        assertThat(queue.status("4", PRODUCT_ID, T0 + 3).estimatedSeconds()).isEqualTo(2);

        queue.admit(T0 + 1000);

        assertThat(queue.status("2", PRODUCT_ID, T0 + 1001).status()).isEqualTo(QueueStatus.ADMITTED);
        assertThat(queue.status("2", PRODUCT_ID, T0 + 1001).admittedSeconds()).isEqualTo(300);
        assertThat(queue.status("3", PRODUCT_ID, T0 + 1001).status()).isEqualTo(QueueStatus.ADMITTED);
        QueueResponse third = queue.status("4", PRODUCT_ID, T0 + 1001);
        assertThat(third.rank()).isEqualTo(1);
        assertThat(third.estimatedSeconds()).isEqualTo(1);

        queue.admit(T0 + 2000);
        assertThat(queue.status("4", PRODUCT_ID, T0 + 2001).status()).isEqualTo(QueueStatus.ADMITTED);
    }

    @Test
    void checkAdmitted_onlyAfterAdmission() {
        QueueService queue = queue(50);
        queue.enter(2L, PRODUCT_ID, System.currentTimeMillis());

        assertThatThrownBy(() -> queue.checkAdmitted(2L, PRODUCT_ID))
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_ADMITTED);

        queue.admit(System.currentTimeMillis());
        assertThatCode(() -> queue.checkAdmitted(2L, PRODUCT_ID)).doesNotThrowAnyException();
        // 다른 상품은 따로 줄을 서야 한다
        assertThatThrownBy(() -> queue.checkAdmitted(2L, PRODUCT_ID + 1))
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_ADMITTED);
    }

    @Test
    void admission_expires() {
        QueueService queue = queue(50);
        queue.enter(2L, PRODUCT_ID, T0);
        queue.admit(T0 + 1000);

        long afterFiveMinutes = T0 + 1000 + 300_000;
        assertThat(queue.status("2", PRODUCT_ID, afterFiveMinutes).status()).isEqualTo(QueueStatus.NOT_IN_QUEUE);

        // 다시 들어오면 맨 뒤에 선다
        queue.enter(3L, PRODUCT_ID, afterFiveMinutes);
        assertThat(queue.enter(2L, PRODUCT_ID, afterFiveMinutes + 1).rank()).isEqualTo(2);
    }

    @Test
    void closedPage_isRemovedAndOthersMoveUp() {
        QueueService queue = queue(1);
        queue.enter(2L, PRODUCT_ID, T0);          // 이후 확인 없음 (화면을 닫음)
        queue.enter(3L, PRODUCT_ID, T0 + 1);
        queue.enter(4L, PRODUCT_ID, T0 + 2);
        queue.status("3", PRODUCT_ID, T0 + 25_000);
        queue.status("4", PRODUCT_ID, T0 + 25_000);

        queue.admit(T0 + 31_000);

        assertThat(queue.status("2", PRODUCT_ID, T0 + 31_001).status()).isEqualTo(QueueStatus.NOT_IN_QUEUE);
        assertThat(queue.status("3", PRODUCT_ID, T0 + 31_001).status()).isEqualTo(QueueStatus.ADMITTED);
        assertThat(queue.status("4", PRODUCT_ID, T0 + 31_001).rank()).isEqualTo(1);
    }

    @Test
    void concurrentEnter_givesEveryoneDistinctRank() throws Exception {
        QueueService queue = queue(50);
        int people = 300;
        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<QueueResponse>> results = new ArrayList<>();
        for (long member = 100; member < 100 + people; member++) {
            long id = member;
            results.add(pool.submit(() -> {
                start.await();
                return queue.enter(id, PRODUCT_ID);
            }));
        }
        start.countDown();

        List<Long> ranks = new ArrayList<>();
        for (Future<QueueResponse> result : results) {
            ranks.add(result.get().rank());
        }
        pool.shutdown();

        assertThat(ranks).doesNotHaveDuplicates().allMatch(r -> r >= 1 && r <= people);
        assertThat(redis.opsForZSet().zCard(QueueService.waitingKey(PRODUCT_ID))).isEqualTo(people);
    }

    @Test
    void enter_sellerCanQueueForOwnProduct() {
        QueueService queue = queue(50);

        assertThat(queue.enter(SELLER_ID, PRODUCT_ID, T0).status()).isEqualTo(QueueStatus.WAITING);
    }

    @Test
    void enter_rejectsWhenNotOnSale() {
        QueueService queue = queue(50);

        product(now.plusDays(1), now.plusDays(2));
        assertThatThrownBy(() -> queue.enter(2L, PRODUCT_ID, T0))
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_ON_SALE);
    }

    @Test
    void disabled_everyoneIsAdmittedWithoutRedis() {
        QueueService queue = new QueueService(null, productRepository, false, 50, 300, 30);

        assertThat(queue.enter(2L, PRODUCT_ID).status()).isEqualTo(QueueStatus.ADMITTED);
        assertThat(queue.status(2L, PRODUCT_ID).status()).isEqualTo(QueueStatus.ADMITTED);
        assertThatCode(() -> queue.checkAdmitted(2L, PRODUCT_ID)).doesNotThrowAnyException();
    }

    @Test
    void estimateSeconds_roundsUpByAdmitRate() {
        assertThat(QueueService.estimateSeconds(1, 50)).isEqualTo(1);
        assertThat(QueueService.estimateSeconds(50, 50)).isEqualTo(1);
        assertThat(QueueService.estimateSeconds(51, 50)).isEqualTo(2);
        assertThat(QueueService.estimateSeconds(1230, 50)).isEqualTo(25);
    }

    private QueueService queue(int admitPerSecond) {
        return new QueueService(redis, productRepository, true, admitPerSecond, 300, 30);
    }

    private void product(LocalDateTime saleStart, LocalDateTime saleEnd) {
        Member seller = Member.builder().email("seller@test.com").password("pw").name("판매자").build();
        ReflectionTestUtils.setField(seller, "id", SELLER_ID);
        Product product = Product.builder().seller(seller).type(ProductType.GOODS).title("상품").maxPerPerson(2)
                .saleStartAt(saleStart).saleEndAt(saleEnd).build();
        ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
    }

    private static void clear() {
        redis.execute(c -> {
            c.serverCommands().flushDb();
            return null;
        }, true);
    }
}

package io.github.jihu1130.ticketonline.concurrency;

import io.github.jihu1130.ticketonline.domain.member.entity.Member;
import io.github.jihu1130.ticketonline.domain.member.repository.MemberRepository;
import io.github.jihu1130.ticketonline.domain.order.dto.OrderRequest;
import io.github.jihu1130.ticketonline.domain.order.service.PurchaseFacade;
import io.github.jihu1130.ticketonline.domain.order.service.PurchaseProcessor;
import io.github.jihu1130.ticketonline.domain.product.entity.DeliveryType;
import io.github.jihu1130.ticketonline.domain.product.entity.GoodsDetail;
import io.github.jihu1130.ticketonline.domain.product.entity.Performance;
import io.github.jihu1130.ticketonline.domain.product.entity.Product;
import io.github.jihu1130.ticketonline.domain.product.entity.ProductType;
import io.github.jihu1130.ticketonline.domain.product.entity.SaleItem;
import io.github.jihu1130.ticketonline.domain.product.entity.SeatGrade;
import io.github.jihu1130.ticketonline.domain.product.entity.TicketDetail;
import io.github.jihu1130.ticketonline.domain.product.repository.GoodsDetailRepository;
import io.github.jihu1130.ticketonline.domain.product.repository.PerformanceRepository;
import io.github.jihu1130.ticketonline.domain.product.repository.ProductRepository;
import io.github.jihu1130.ticketonline.domain.product.repository.SaleItemRepository;
import io.github.jihu1130.ticketonline.domain.product.repository.SeatGradeRepository;
import io.github.jihu1130.ticketonline.domain.product.repository.TicketDetailRepository;
import io.github.jihu1130.ticketonline.global.exception.CustomException;
import io.github.jihu1130.ticketonline.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 동시 구매 테스트의 공통 틀. 실제 MySQL 테스트 DB(ticketonline_test)를 쓴다.
 * 구매 방식(A/B/C)마다 이 클래스를 상속해 같은 시나리오로 비교한다.
 *
 * - 테스트마다 회원·상품을 새로 만들어 서로 섞이지 않게 한다 (이메일에 고유 접두어)
 * - 모든 요청을 준비시킨 뒤 신호 한 번에 동시에 출발시킨다
 * - 결과는 DB에서 직접 세어 확인한다 (서비스 코드를 거치지 않음)
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class ConcurrencyTestSupport {

    /** 동시에 요청을 보내는 스레드 수 (DB 연결은 Hikari 기본 10개를 나눠 쓴다) */
    protected static final int THREADS = 32;

    /** 실제 API와 같은 입구 (방식 B의 다시 시도 포함) */
    @Autowired protected PurchaseFacade purchaseFacade;
    @Autowired protected PurchaseProcessor purchaseProcessor;
    @Autowired protected JdbcTemplate jdbc;
    /** 방식 C 테스트용 (테스트 설정에서 Redis 15번 DB) */
    @Autowired protected StringRedisTemplate redis;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private GoodsDetailRepository goodsDetailRepository;
    @Autowired private TicketDetailRepository ticketDetailRepository;
    @Autowired private SeatGradeRepository seatGradeRepository;
    @Autowired private PerformanceRepository performanceRepository;
    @Autowired private SaleItemRepository saleItemRepository;

    // ---------- 준비 ----------

    /** 구매자 n명 (비밀번호는 쓰지 않으므로 아무 값) */
    protected List<Long> createMembers(int count) {
        String prefix = UUID.randomUUID().toString().substring(0, 8);
        List<Member> members = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            members.add(Member.builder().email(prefix + "-" + i + "@load.test").password("x").name("구매자" + i).build());
        }
        return memberRepository.saveAll(members).stream().map(Member::getId).toList();
    }

    /** 판매 중인 굿즈 (현장 수령이라 주소 없음). 반환: 상품 번호와 판매 단위 번호 */
    protected GoodsFixture createGoods(int stock, int maxPerPerson) {
        return createGoods(stock, maxPerPerson, DeliveryType.PICKUP);
    }

    protected GoodsFixture createGoods(int stock, int maxPerPerson, DeliveryType deliveryType) {
        Product product = productRepository.save(onSaleProduct(ProductType.GOODS, maxPerPerson));
        goodsDetailRepository.save(GoodsDetail.builder().product(product).description("동시성 테스트")
                .deliveryType(deliveryType).build());
        SaleItem item = saleItemRepository.save(SaleItem.builder().product(product).price(10000)
                .totalQuantity(stock).build());
        return new GoodsFixture(product.getId(), item.getId());
    }

    /** 판매 중인 티켓 (회차 1개, 등급별 좌석 수). 반환: 상품 번호와 등급별 판매 단위 번호 */
    protected TicketFixture createTicket(int maxPerPerson, int... seatCounts) {
        Product product = productRepository.save(onSaleProduct(ProductType.TICKET, maxPerPerson));
        ticketDetailRepository.save(TicketDetail.builder().product(product).venue("테스트홀")
                .closeBeforeHours(1).build());
        Performance performance = performanceRepository.save(Performance.builder().product(product)
                .eventAt(LocalDateTime.now().plusDays(3)).build());
        List<Long> itemIds = new ArrayList<>();
        for (int i = 0; i < seatCounts.length; i++) {
            SeatGrade grade = seatGradeRepository.save(SeatGrade.builder().product(product).name("등급" + i)
                    .price(10000 * (i + 1)).seatCount(seatCounts[i]).build());
            itemIds.add(saleItemRepository.save(SaleItem.builder().product(product).performance(performance)
                    .seatGrade(grade).price(grade.getPrice()).totalQuantity(seatCounts[i]).build()).getId());
        }
        return new TicketFixture(product.getId(), performance.getId(), itemIds);
    }

    /**
     * 판매 중인 티켓 (회차 2개 x 등급 2개, 좌석 수는 모두 seatCount).
     * saleItemIds 순서: 1회차 등급0, 1회차 등급1, 2회차 등급0, 2회차 등급1 (등급0 = 10000원, 등급1 = 20000원)
     */
    protected TwoPerformanceTicket createTicketTwoPerformances(int maxPerPerson, int seatCount) {
        Product product = productRepository.save(onSaleProduct(ProductType.TICKET, maxPerPerson));
        ticketDetailRepository.save(TicketDetail.builder().product(product).venue("테스트홀")
                .closeBeforeHours(1).build());
        List<SeatGrade> grades = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            grades.add(seatGradeRepository.save(SeatGrade.builder().product(product).name("등급" + i)
                    .price(10000 * (i + 1)).seatCount(seatCount).build()));
        }
        List<Long> performanceIds = new ArrayList<>();
        List<Long> itemIds = new ArrayList<>();
        for (int day = 3; day <= 4; day++) {
            Performance performance = performanceRepository.save(Performance.builder().product(product)
                    .eventAt(LocalDateTime.now().plusDays(day)).build());
            performanceIds.add(performance.getId());
            for (SeatGrade grade : grades) {
                itemIds.add(saleItemRepository.save(SaleItem.builder().product(product).performance(performance)
                        .seatGrade(grade).price(grade.getPrice()).totalQuantity(seatCount).build()).getId());
            }
        }
        return new TwoPerformanceTicket(product.getId(), performanceIds, itemIds);
    }

    private Product onSaleProduct(ProductType type, int maxPerPerson) {
        Long sellerId = createMembers(1).get(0);
        return Product.builder()
                .seller(memberRepository.getReferenceById(sellerId))
                .type(type)
                .title("동시성 테스트 " + type)
                .maxPerPerson(maxPerPerson)
                .saleStartAt(LocalDateTime.now().minusDays(1))
                .saleEndAt(LocalDateTime.now().plusDays(1))
                .build();
    }

    protected record GoodsFixture(Long productId, Long saleItemId) {
    }

    protected record TicketFixture(Long productId, Long performanceId, List<Long> saleItemIds) {
    }

    protected record TwoPerformanceTicket(Long productId, List<Long> performanceIds, List<Long> saleItemIds) {
    }

    // ---------- 동시 실행 ----------

    /** 구매 요청 하나 */
    protected record Purchase(Long memberId, Long productId, Long saleItemId, int quantity) {
    }

    /**
     * 모든 요청을 준비시킨 뒤 한 번에 출발시키고 결과를 모은다.
     * 성공은 수량을 더하고, 실패는 오류 코드별로 센다. 예상하지 못한 예외(데드락 등)는 따로 모은다.
     */
    protected Result runConcurrently(String name, List<Purchase> purchases) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        // 스레드 수보다 요청이 많으면 나머지는 대기열에 있다가 앞 요청이 끝나는 대로 출발한다.
        // 그래서 "스레드가 모두 준비"되면 출발 신호를 보낸다.
        CountDownLatch ready = new CountDownLatch(Math.min(purchases.size(), THREADS));
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(purchases.size());
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger successQuantity = new AtomicInteger();
        Map<ErrorCode, AtomicInteger> failures = new ConcurrentHashMap<>();
        List<Throwable> unexpected = new CopyOnWriteArrayList<>();

        for (Purchase p : purchases) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    purchaseFacade.purchase(p.memberId(), p.productId(), new OrderRequest(p.saleItemId(), p.quantity(), null));
                    successCount.incrementAndGet();
                    successQuantity.addAndGet(p.quantity());
                } catch (CustomException e) {
                    failures.computeIfAbsent(e.getErrorCode(), k -> new AtomicInteger()).incrementAndGet();
                } catch (Throwable e) {
                    unexpected.add(e);
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await(30, TimeUnit.SECONDS);
        long begin = System.nanoTime();
        start.countDown();
        boolean finished = done.await(120, TimeUnit.SECONDS);
        long elapsedMs = (System.nanoTime() - begin) / 1_000_000;
        pool.shutdownNow();

        Map<ErrorCode, Integer> failureCounts = new EnumMap<>(ErrorCode.class);
        failures.forEach((code, n) -> failureCounts.put(code, n.get()));
        Result result = new Result(name, purchases.size(), successCount.get(), successQuantity.get(),
                Collections.unmodifiableMap(failureCounts), List.copyOf(unexpected), elapsedMs, finished);
        System.out.println(result.summary());
        return result;
    }

    /** 동시 실행 결과 */
    protected record Result(String name, int requests, int successCount, int successQuantity,
                            Map<ErrorCode, Integer> failures, List<Throwable> unexpected,
                            long elapsedMs, boolean finished) {

        int failure(ErrorCode code) {
            return failures.getOrDefault(code, 0);
        }

        String summary() {
            return String.format("[동시성] %s | 요청 %d건, 성공 %d건(수량 %d), 실패 %s, 예상 밖 예외 %d건%s, %dms",
                    name, requests, successCount, successQuantity, failures, unexpected.size(),
                    unexpected.isEmpty() ? "" : " " + unexpectedTypes(), elapsedMs);
        }

        /** 예상 밖 예외를 종류별로 센다. 예) {CannotAcquireLockException=12} */
        private Map<String, Long> unexpectedTypes() {
            return unexpected.stream().collect(java.util.stream.Collectors.groupingBy(
                    e -> e.getClass().getSimpleName() + (e.getCause() == null ? ""
                            : "(" + rootCause(e).getClass().getSimpleName() + ": " + rootCause(e).getMessage() + ")"),
                    java.util.TreeMap::new, java.util.stream.Collectors.counting()));
        }

        private static Throwable rootCause(Throwable e) {
            Throwable cause = e;
            while (cause.getCause() != null && cause.getCause() != cause) {
                cause = cause.getCause();
            }
            return cause;
        }
    }

    // ---------- 방식 C: Redis ----------

    /**
     * Redis 가 꺼져 있으면 테스트를 건너뛰고, 켜져 있으면 테스트용 DB(15번)를 비운다.
     * 테스트 DB 는 실행마다 새로 만들어 번호가 1부터 다시 시작하므로, 지난 실행의 stock:1 같은 값이 남아 있으면 안 된다.
     */
    protected void cleanRedisOrSkip() {
        boolean up;
        try {
            up = "PONG".equals(redis.execute(c -> c.ping(), true));
        } catch (Exception e) {
            up = false;
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(up, "Redis 가 꺼져 있어 방식 C 테스트를 건너뜀");
        redis.execute(c -> {
            c.serverCommands().flushDb();
            return null;
        }, true);
    }

    /** Redis 의 남은 수량 (아직 불러오지 않았으면 null) */
    protected Integer redisStock(Long saleItemId) {
        String value = redis.opsForValue().get("stock:" + saleItemId);
        return value == null ? null : Integer.valueOf(value);
    }

    protected Integer redisBought(Long productId, Long scopeId, Long memberId) {
        String value = redis.opsForValue().get("bought:" + productId + ":" + scopeId + ":" + memberId);
        return value == null ? null : Integer.valueOf(value);
    }

    // ---------- DB에서 직접 확인 ----------

    protected int soldQuantity(Long saleItemId) {
        return jdbc.queryForObject("select sold_quantity from sale_item where sale_item_id = ?", Integer.class, saleItemId);
    }

    protected int totalQuantity(Long saleItemId) {
        return jdbc.queryForObject("select total_quantity from sale_item where sale_item_id = ?", Integer.class, saleItemId);
    }

    /** 이 판매 단위로 저장된 주문 수량 합계 (취소된 주문 제외) */
    protected int orderedQuantity(Long saleItemId) {
        return jdbc.queryForObject("select coalesce(sum(quantity), 0) from product_order"
                + " where sale_item_id = ? and status = 'SUCCESS'", Integer.class, saleItemId);
    }

    /** 취소된 주문 제외 */
    protected int orderCount(Long saleItemId) {
        return jdbc.queryForObject("select count(*) from product_order where sale_item_id = ? and status = 'SUCCESS'",
                Integer.class, saleItemId);
    }

    /** 1인 한도 기록 (회원 + 상품 + 범위) */
    protected int boughtQuantity(Long memberId, Long productId, Long scopeId) {
        List<Integer> rows = jdbc.queryForList(
                "select bought_quantity from purchase_count where member_id = ? and product_id = ? and scope_id = ?",
                Integer.class, memberId, productId, scopeId);
        return rows.isEmpty() ? 0 : rows.get(0);
    }
}

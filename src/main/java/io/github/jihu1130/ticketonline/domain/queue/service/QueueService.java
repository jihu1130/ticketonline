package io.github.jihu1130.ticketonline.domain.queue.service;

import io.github.jihu1130.ticketonline.domain.product.entity.Product;
import io.github.jihu1130.ticketonline.domain.product.repository.ProductRepository;
import io.github.jihu1130.ticketonline.domain.queue.dto.QueueResponse;
import io.github.jihu1130.ticketonline.global.exception.CustomException;
import io.github.jihu1130.ticketonline.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * F-07 대기열. 구매하기 -> 대기열 -> 입장한 사람만 예매 화면에서 구매한다.
 *
 * Redis 키 (상품마다, 값은 회원 id)
 *  queue:{상품}          줄 서 있는 사람. 점수 = 들어온 순서 번호(queue:{상품}:seq 에서 1, 2, 3 ...) -> 점수 순서가 곧 순번
 *                        (시각을 점수로 쓰면 같은 ms 에 들어온 사람끼리 회원 id 순으로 섞여 앞사람 순번이 밀린다)
 *  queue:{상품}:seen     마지막으로 순번을 확인한 시각. 오래 확인이 없으면 화면을 닫은 것으로 보고 줄에서 뺀다
 *  queue:{상품}:admitted 입장한 사람. 점수 = 입장이 끝나는 시각
 *  queue:active          줄이 있는 상품 목록 (1초마다 입장시킬 상품을 찾는 데 쓴다)
 *
 * app.queue.enabled=false 이면 Redis 를 쓰지 않고 누구나 바로 입장한 것으로 본다 (k6 비교, 구매 테스트).
 */
@Slf4j
@Service
public class QueueService {

    static final String ACTIVE_KEY = "queue:active";
    /** 줄도 입장한 사람도 없이 이 시간이 지나면 queue:active 에서 뺀다 */
    private static final long IDLE_MILLIS = 60_000;
    /** 줄에 없을 때만 순서 번호를 받아 줄에 넣는다. 번호 받기와 넣기 사이에 다른 사람이 끼지 않게 한 번에 실행 */
    private static final RedisScript<Long> ENTER_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('ZSCORE', KEYS[1], ARGV[1]) == false then
              redis.call('ZADD', KEYS[1], redis.call('INCR', KEYS[2]), ARGV[1])
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final ProductRepository productRepository;
    private final boolean enabled;
    private final int admitPerSecond;
    private final long admittedMillis;
    private final long staleMillis;

    public QueueService(StringRedisTemplate redis, ProductRepository productRepository,
                        @Value("${app.queue.enabled}") boolean enabled,
                        @Value("${app.queue.admit-per-second}") int admitPerSecond,
                        @Value("${app.queue.admitted-seconds}") long admittedSeconds,
                        @Value("${app.queue.stale-seconds}") long staleSeconds) {
        this.redis = redis;
        this.productRepository = productRepository;
        this.enabled = enabled;
        this.admitPerSecond = admitPerSecond;
        this.admittedMillis = admittedSeconds * 1000;
        this.staleMillis = staleSeconds * 1000;
    }

    /** 줄 서기. 이미 줄에 있으면 처음 순번 그대로, 이미 입장했으면 입장 상태를 돌려준다 */
    public QueueResponse enter(Long memberId, Long productId) {
        return enter(memberId, productId, System.currentTimeMillis());
    }

    QueueResponse enter(Long memberId, Long productId, long now) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
        if (product.isOwnedBy(memberId)) {
            throw new CustomException(ErrorCode.OWN_PRODUCT);
        }
        LocalDateTime today = LocalDateTime.now();
        if (today.isBefore(product.getSaleStartAt()) || !today.isBefore(product.getSaleEndAt())) {
            throw new CustomException(ErrorCode.NOT_ON_SALE);
        }
        if (!enabled) {
            return QueueResponse.admitted(null);
        }

        String member = memberId.toString();
        Long left = admittedMillisLeft(member, productId, now);
        if (left != null) {
            return QueueResponse.admitted(toSeconds(left));
        }
        ZSetOperations<String, String> zset = redis.opsForZSet();
        // 상품 목록을 먼저 올려 둔다 (줄만 있고 목록에 없으면 아무도 입장하지 못함)
        zset.add(ACTIVE_KEY, productId.toString(), now);
        redis.execute(ENTER_SCRIPT, List.of(waitingKey(productId), seqKey(productId)), member);
        zset.add(seenKey(productId), member, now);
        return status(member, productId, now);
    }

    /** 내 순번 확인. 화면이 1초마다 부르며, 부를 때마다 "아직 화면을 보고 있음"으로 기록한다 */
    public QueueResponse status(Long memberId, Long productId) {
        if (!enabled) {
            return QueueResponse.admitted(null);
        }
        return status(memberId.toString(), productId, System.currentTimeMillis());
    }

    QueueResponse status(String member, Long productId, long now) {
        Long left = admittedMillisLeft(member, productId, now);
        if (left != null) {
            return QueueResponse.admitted(toSeconds(left));
        }
        ZSetOperations<String, String> zset = redis.opsForZSet();
        Long index = zset.rank(waitingKey(productId), member);
        if (index == null) {
            return QueueResponse.notInQueue();
        }
        zset.add(seenKey(productId), member, now);
        zset.add(ACTIVE_KEY, productId.toString(), now);
        Long total = zset.zCard(waitingKey(productId));
        long rank = index + 1;
        return QueueResponse.waiting(rank, total == null ? rank : total, estimateSeconds(rank, admitPerSecond));
    }

    /** 구매 API 에서 부른다. 입장하지 않았거나 입장 시간이 끝났으면 NOT_ADMITTED */
    public void checkAdmitted(Long memberId, Long productId) {
        if (!enabled) {
            return;
        }
        if (admittedMillisLeft(memberId.toString(), productId, System.currentTimeMillis()) == null) {
            throw new CustomException(ErrorCode.NOT_ADMITTED);
        }
    }

    /** 1초마다 줄 앞에서부터 admitPerSecond 명을 입장시킨다 */
    @Scheduled(fixedRate = 1000)
    public void admitScheduled() {
        if (!enabled) {
            return;
        }
        try {
            admit(System.currentTimeMillis());
        } catch (DataAccessException e) {
            log.warn("대기열 입장 처리 실패 (Redis 가 켜져 있는지 확인): {}", e.getMessage());
        }
    }

    void admit(long now) {
        ZSetOperations<String, String> zset = redis.opsForZSet();
        Set<TypedTuple<String>> products = zset.rangeWithScores(ACTIVE_KEY, 0, -1);
        if (products == null) {
            return;
        }
        for (TypedTuple<String> entry : products) {
            Long productId = Long.valueOf(entry.getValue());
            String waiting = waitingKey(productId);
            String seen = seenKey(productId);
            String admitted = admittedKey(productId);

            // 화면을 닫은 사람은 줄에서 뺀다 (뒷사람 순번이 그만큼 당겨진다)
            Set<String> stale = zset.rangeByScore(seen, 0, now - staleMillis);
            if (stale != null && !stale.isEmpty()) {
                Object[] members = stale.toArray();
                zset.remove(waiting, members);
                zset.remove(seen, members);
            }
            // 입장 시간이 끝난 사람
            zset.removeRangeByScore(admitted, 0, now);

            Set<TypedTuple<String>> popped = zset.popMin(waiting, admitPerSecond);
            if (popped != null) {
                for (TypedTuple<String> person : popped) {
                    zset.add(admitted, person.getValue(), now + admittedMillis);
                    zset.remove(seen, person.getValue());
                }
            }

            boolean idle = entry.getScore() != null && entry.getScore() < now - IDLE_MILLIS;
            if (idle && zero(zset.zCard(waiting)) && zero(zset.zCard(admitted))) {
                zset.remove(ACTIVE_KEY, entry.getValue());
                redis.delete(seqKey(productId));
            }
        }
    }

    /** 앞에서부터 1초에 perSecond 명씩 입장하므로 rank 번째는 올림(rank / perSecond)초 뒤에 입장한다 */
    static long estimateSeconds(long rank, int perSecond) {
        return (rank + perSecond - 1) / perSecond;
    }

    /** 입장한 상태면 남은 시간(ms), 아니면 null */
    private Long admittedMillisLeft(String member, Long productId, long now) {
        Double until = redis.opsForZSet().score(admittedKey(productId), member);
        if (until == null || until <= now) {
            return null;
        }
        return until.longValue() - now;
    }

    private static long toSeconds(long millis) {
        return (millis + 999) / 1000;
    }

    private static boolean zero(Long count) {
        return count == null || count == 0;
    }

    static String waitingKey(Long productId) {
        return "queue:" + productId;
    }

    static String seqKey(Long productId) {
        return "queue:" + productId + ":seq";
    }

    static String seenKey(Long productId) {
        return "queue:" + productId + ":seen";
    }

    static String admittedKey(Long productId) {
        return "queue:" + productId + ":admitted";
    }
}

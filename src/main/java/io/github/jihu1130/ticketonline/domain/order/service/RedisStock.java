package io.github.jihu1130.ticketonline.domain.order.service;

import io.github.jihu1130.ticketonline.domain.order.entity.PurchaseCountId;
import io.github.jihu1130.ticketonline.domain.order.repository.PurchaseCountRepository;
import io.github.jihu1130.ticketonline.domain.product.entity.Performance;
import io.github.jihu1130.ticketonline.domain.product.entity.Product;
import io.github.jihu1130.ticketonline.domain.product.entity.ProductType;
import io.github.jihu1130.ticketonline.domain.product.entity.TicketDetail;
import io.github.jihu1130.ticketonline.domain.product.repository.PerformanceRepository;
import io.github.jihu1130.ticketonline.domain.product.repository.ProductRepository;
import io.github.jihu1130.ticketonline.domain.product.repository.SaleItemKey;
import io.github.jihu1130.ticketonline.domain.product.repository.SaleItemRepository;
import io.github.jihu1130.ticketonline.domain.product.repository.TicketDetailRepository;
import io.github.jihu1130.ticketonline.global.exception.CustomException;
import io.github.jihu1130.ticketonline.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 구매 방식 C 의 Redis 재고와 1인 구매 수량.
 *
 * Redis 키
 *  stock:{판매단위}                남은 수량
 *  bought:{상품}:{범위}:{회원}      산 수량 (범위 = 티켓은 회차 번호, 굿즈는 0)
 * 처음 필요할 때 DB 값으로 채운다(SET NX: 이미 있으면 덮어쓰지 않음). Redis 를 비워도 DB 기준으로 다시 맞춰진다.
 *
 * DB 가 기준이고 Redis 는 앞에서 거절을 빨리 하는 용도다. 둘이 잠깐 어긋나도 DB 의 조건부 UPDATE 가 초과 판매를 막는다.
 * app.purchase.strategy 가 redis 가 아니면 이 클래스의 되돌리기·옮기기는 아무 일도 하지 않는다 (주문 취소·변경에서 부름).
 */
@Slf4j
@Component
public class RedisStock {

    /**
     * 한도 확인 + 남은 수량 확인 + 차감을 한 번에 (Redis 는 스크립트 하나를 다른 명령과 섞지 않고 끝까지 실행한다).
     * 매진은 산 수량보다 먼저 본다: 처음 오는 구매자의 산 수량을 DB 에서 읽지 않고도 매진이면 바로 거절하기 위해.
     * 반환 {결과, 값}: 1 = 성공(값: 남은 수량), -1 = 재고 키 없음, -2 = 산 수량 키 없음,
     * -3 = 한도 초과(값: 더 살 수 있는 수량), -4 = 매진, -5 = 수량 부족(값: 남은 수량)
     */
    private static final RedisScript<List> RESERVE = new DefaultRedisScript<>("""
            local stock = redis.call('GET', KEYS[1])
            if not stock then return {-1, 0} end
            stock = tonumber(stock)
            if stock <= 0 then return {-4, 0} end
            local bought = redis.call('GET', KEYS[2])
            if not bought then return {-2, 0} end
            bought = tonumber(bought)
            local quantity = tonumber(ARGV[1])
            local limit = tonumber(ARGV[2])
            if bought + quantity > limit then return {-3, math.max(0, limit - bought)} end
            if quantity > stock then return {-5, stock} end
            redis.call('DECRBY', KEYS[1], quantity)
            redis.call('INCRBY', KEYS[2], quantity)
            return {1, stock - quantity}
            """, List.class);

    /** 되돌리기·옮기기: 키가 있을 때만 더한다 (없으면 다음에 DB 값으로 채워지므로 건드리지 않음) */
    private static final RedisScript<Long> ADD_IF_EXISTS = new DefaultRedisScript<>("""
            for i, key in ipairs(KEYS) do
              if redis.call('EXISTS', key) == 1 then redis.call('INCRBY', key, tonumber(ARGV[i])) end
            end
            return 1
            """, Long.class);

    private static final int MAX_LOAD_TRIES = 3;

    private final StringRedisTemplate redis;
    private final SaleItemRepository saleItemRepository;
    private final PurchaseCountRepository purchaseCountRepository;
    private final ProductRepository productRepository;
    private final PerformanceRepository performanceRepository;
    private final TicketDetailRepository ticketDetailRepository;
    private final boolean enabled;

    public RedisStock(StringRedisTemplate redis, SaleItemRepository saleItemRepository,
                      PurchaseCountRepository purchaseCountRepository, ProductRepository productRepository,
                      PerformanceRepository performanceRepository, TicketDetailRepository ticketDetailRepository,
                      @Value("${app.purchase.strategy:pessimistic}") String strategy) {
        this.redis = redis;
        this.saleItemRepository = saleItemRepository;
        this.purchaseCountRepository = purchaseCountRepository;
        this.productRepository = productRepository;
        this.performanceRepository = performanceRepository;
        this.ticketDetailRepository = ticketDetailRepository;
        this.enabled = "redis".equals(strategy);
    }

    // ---------- DB 를 쓰기 전에 차감 (PurchaseFacade) ----------

    /**
     * 방식 C: DB 연결을 잡기 전에 Redis 만으로 확인하고 차감한다. 매진·한도 초과·판매 기간 아님은 여기서 끝나 DB 를 쓰지 않는다.
     * 판매 단위 정보(item:{판매단위})는 처음 한 번만 DB 에서 읽어 둔다. 판매가 시작되면 바뀌지 않는 값이라 다시 읽지 않는다
     * (상품은 판매 시작 전에만 수정할 수 있고, 수정하면 판매 단위 번호가 새로 만들어진다).
     *
     * @return 차감한 내용 (DB 단계가 실패하면 release 로 되돌린다). 방식 C 가 아니거나 판매 단위를 고르지 않았으면 null
     */
    public PurchaseCommand reserveAhead(Long memberId, Long productId, Long saleItemId, int quantity) {
        if (!enabled || saleItemId == null) {
            return null;
        }
        ItemInfo item = itemInfo(saleItemId);
        if (!item.productId().equals(productId)) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "이 상품의 구매 항목이 아닙니다.");
        }
        long now = System.currentTimeMillis();
        if (now < item.saleStartAt()) {
            throw new CustomException(ErrorCode.NOT_ON_SALE, "아직 판매가 시작되지 않았습니다.");
        }
        if (now >= item.saleEndAt()) {
            throw new CustomException(ErrorCode.NOT_ON_SALE, "판매가 종료된 상품입니다.");
        }
        if (now >= item.closeAt()) {
            throw new CustomException(ErrorCode.NOT_ON_SALE, "예매가 마감된 회차입니다.");
        }
        PurchaseCommand command = new PurchaseCommand(memberId, productId, saleItemId, item.scopeId(),
                item.maxPerPerson(), quantity, null, item.ticket(), true);
        reserve(command);
        return command;
    }

    /** 판매 단위 정보: 상품 번호, 한도 범위(회차 또는 0), 1인 한도, 티켓 여부, 판매 시작·종료, 예매 마감 (시각은 ms) */
    record ItemInfo(Long productId, Long scopeId, int maxPerPerson, boolean ticket,
                    long saleStartAt, long saleEndAt, long closeAt) {
    }

    ItemInfo itemInfo(Long saleItemId) {
        String key = itemKey(saleItemId);
        Map<Object, Object> cached = redis.opsForHash().entries(key);
        if (cached.isEmpty()) {
            cached = loadItemInfo(saleItemId);
            redis.opsForHash().putAll(key, cached);
        }
        return new ItemInfo(
                Long.valueOf((String) cached.get("productId")),
                Long.valueOf((String) cached.get("scopeId")),
                Integer.parseInt((String) cached.get("maxPerPerson")),
                "1".equals(cached.get("ticket")),
                Long.parseLong((String) cached.get("saleStartAt")),
                Long.parseLong((String) cached.get("saleEndAt")),
                Long.parseLong((String) cached.get("closeAt")));
    }

    private Map<Object, Object> loadItemInfo(Long saleItemId) {
        SaleItemKey key = saleItemRepository.findKeyById(saleItemId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INPUT, "구매할 항목을 찾을 수 없습니다."));
        Product product = productRepository.findById(key.productId())
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
        boolean ticket = product.getType() == ProductType.TICKET;
        LocalDateTime closeAt = product.getSaleEndAt();
        if (ticket) {
            Performance performance = performanceRepository.findById(key.performanceId())
                    .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
            TicketDetail detail = ticketDetailRepository.findById(product.getId())
                    .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
            closeAt = Performance.bookingCloseAt(performance.getEventAt(), product.getSaleEndAt(),
                    detail.getCloseBeforeHours());
        }
        Map<Object, Object> info = new HashMap<>();
        info.put("productId", String.valueOf(product.getId()));
        info.put("scopeId", String.valueOf(ticket ? key.performanceId() : PurchaseCountId.WHOLE_PRODUCT));
        info.put("maxPerPerson", String.valueOf(product.getMaxPerPerson()));
        info.put("ticket", ticket ? "1" : "0");
        info.put("saleStartAt", String.valueOf(millis(product.getSaleStartAt())));
        info.put("saleEndAt", String.valueOf(millis(product.getSaleEndAt())));
        info.put("closeAt", String.valueOf(millis(closeAt)));
        return info;
    }

    private static long millis(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * Redis 에서 한도·수량을 확인하고 줄인다. 통과하지 못하면 아무것도 바꾸지 않고 CustomException.
     * 키가 없으면 DB 값으로 채운 뒤 다시 시도한다.
     */
    public void reserve(PurchaseCommand command) {
        String stockKey = stockKey(command.saleItemId());
        String boughtKey = boughtKey(command.productId(), command.scopeId(), command.memberId());
        for (int attempt = 1; attempt <= MAX_LOAD_TRIES; attempt++) {
            List<?> result = redis.execute(RESERVE, List.of(stockKey, boughtKey),
                    String.valueOf(command.quantity()), String.valueOf(command.maxPerPerson()));
            long code = ((Number) result.get(0)).longValue();
            long value = ((Number) result.get(1)).longValue();
            if (code == 1) {
                return;
            }
            if (code == -1) {
                int remaining = saleItemRepository.findRemainingQuantity(command.saleItemId())
                        .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
                redis.opsForValue().setIfAbsent(stockKey, String.valueOf(remaining));
                continue;
            }
            if (code == -2) {
                int bought = purchaseCountRepository.findBoughtQuantity(
                        command.memberId(), command.productId(), command.scopeId()).orElse(0);
                redis.opsForValue().setIfAbsent(boughtKey, String.valueOf(bought));
                continue;
            }
            if (code == -3) {
                throw new CustomException(ErrorCode.LIMIT_EXCEEDED, command.limitMessage((int) value));
            }
            if (code == -4) {
                throw new CustomException(ErrorCode.SOLD_OUT);
            }
            throw new CustomException(ErrorCode.INSUFFICIENT_STOCK,
                    "남은 수량은 " + value + "개입니다. 남은 수량보다 많이 구매할 수 없습니다.");
        }
        throw new IllegalStateException("Redis 재고를 불러오지 못했습니다: " + stockKey);
    }

    /** reserve 로 줄인 것을 되돌린다 (DB 기록이 실패해 트랜잭션이 되돌려졌을 때) */
    public void release(PurchaseCommand command) {
        add(List.of(stockKey(command.saleItemId()), boughtKey(command.productId(), command.scopeId(), command.memberId())),
                command.quantity(), -command.quantity());
    }

    /** DB 와 어긋났을 때(조건부 UPDATE 실패) 재고 키를 지워 다음 요청이 DB 값으로 다시 채우게 한다 */
    public void forgetStock(Long saleItemId) {
        redis.delete(stockKey(saleItemId));
    }

    /** DB 의 산 수량이 한도를 넘었을 때 산 수량 키를 지워 DB 값으로 다시 채우게 한다 */
    public void forgetBought(PurchaseCommand command) {
        redis.delete(boughtKey(command.productId(), command.scopeId(), command.memberId()));
    }

    // ---------- 주문 취소·변경 (방식 C 일 때만, DB 커밋 뒤에) ----------

    /** 취소·수량 줄이기: 재고 +n, 산 수량 -n */
    public void restoreAfterCommit(Long saleItemId, Long productId, Long scopeId, Long memberId, int quantity) {
        afterCommit(() -> add(List.of(stockKey(saleItemId), boughtKey(productId, scopeId, memberId)), quantity, -quantity));
    }

    /** 회차·등급 바꾸기: 원래 판매 단위 재고 +n, 바꾼 쪽 -n. 회차가 바뀌면 회차별 산 수량도 옮긴다 */
    public void moveAfterCommit(Long fromItemId, Long toItemId, Long productId, Long fromScope, Long toScope,
                                Long memberId, int quantity) {
        afterCommit(() -> {
            add(List.of(stockKey(fromItemId), stockKey(toItemId)), quantity, -quantity);
            if (!fromScope.equals(toScope)) {
                add(List.of(boughtKey(productId, fromScope, memberId), boughtKey(productId, toScope, memberId)),
                        -quantity, quantity);
            }
        });
    }

    private void afterCommit(Runnable action) {
        if (!enabled) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    action.run();
                } catch (DataAccessException e) {
                    // Redis 를 못 고쳐도 DB 는 맞다. Redis 쪽이 적게 남아 있으면 더 일찍 거절할 뿐 초과 판매는 없다
                    log.warn("주문 취소·변경 뒤 Redis 재고 반영 실패: {}", e.getMessage());
                }
            }
        });
    }

    private void add(List<String> keys, int first, int second) {
        redis.execute(ADD_IF_EXISTS, keys, String.valueOf(first), String.valueOf(second));
    }

    static String itemKey(Long saleItemId) {
        return "item:" + saleItemId;
    }

    static String stockKey(Long saleItemId) {
        return "stock:" + saleItemId;
    }

    static String boughtKey(Long productId, Long scopeId, Long memberId) {
        return "bought:" + productId + ":" + scopeId + ":" + memberId;
    }
}

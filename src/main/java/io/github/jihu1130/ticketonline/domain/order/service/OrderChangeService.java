package io.github.jihu1130.ticketonline.domain.order.service;

import io.github.jihu1130.ticketonline.domain.order.dto.OrderResponse;
import io.github.jihu1130.ticketonline.domain.order.entity.ProductOrder;
import io.github.jihu1130.ticketonline.domain.order.entity.PurchaseCount;
import io.github.jihu1130.ticketonline.domain.order.entity.PurchaseCountId;
import io.github.jihu1130.ticketonline.domain.order.repository.ProductOrderRepository;
import io.github.jihu1130.ticketonline.domain.order.repository.PurchaseCountRepository;
import io.github.jihu1130.ticketonline.domain.product.entity.DeliveryType;
import io.github.jihu1130.ticketonline.domain.product.entity.GoodsDetail;
import io.github.jihu1130.ticketonline.domain.product.entity.Performance;
import io.github.jihu1130.ticketonline.domain.product.entity.Product;
import io.github.jihu1130.ticketonline.domain.product.entity.ProductType;
import io.github.jihu1130.ticketonline.domain.product.entity.SaleItem;
import io.github.jihu1130.ticketonline.domain.product.entity.TicketDetail;
import io.github.jihu1130.ticketonline.domain.product.repository.GoodsDetailRepository;
import io.github.jihu1130.ticketonline.domain.product.repository.PerformanceRepository;
import io.github.jihu1130.ticketonline.domain.product.repository.ProductRepository;
import io.github.jihu1130.ticketonline.domain.product.repository.SaleItemKey;
import io.github.jihu1130.ticketonline.domain.product.repository.SaleItemRepository;
import io.github.jihu1130.ticketonline.domain.product.repository.TicketDetailRepository;
import io.github.jihu1130.ticketonline.global.exception.CustomException;
import io.github.jihu1130.ticketonline.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 주문 취소·변경: 전체 취소, 수량 줄이기, 배송지 변경, 회차·등급 바꾸기.
 * 기한: 티켓은 그 회차의 예매 마감 전, 굿즈는 판매 종료 전 (지나면 CHANGE_CLOSED).
 *
 * 구매 방식(A/B)과 상관없이 행을 잠가서 바꾼다. 취소·변경은 구매보다 훨씬 드물어 잠금 비용이 작다.
 * 잠그는 순서: 주문 -> 1인 한도 -> 재고 (구매와 같은 "한도 -> 재고" 순서).
 * 한도·재고를 두 개씩 잠글 때(회차·등급 바꾸기)는 번호가 작은 것부터 잠가 서로 기다리는 교착을 막는다.
 * 방식 B 구매는 잠금 대신 버전을 보므로, 여기서 바꾼 재고와 겹치면 버전 충돌로 다시 시도한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class OrderChangeService {

    private final ProductOrderRepository productOrderRepository;
    private final PurchaseCountRepository purchaseCountRepository;
    private final SaleItemRepository saleItemRepository;
    private final ProductRepository productRepository;
    private final PerformanceRepository performanceRepository;
    private final TicketDetailRepository ticketDetailRepository;
    private final GoodsDetailRepository goodsDetailRepository;
    private final OrderNotices orderNotices;
    /** 방식 C 일 때만 커밋 뒤 Redis 재고·산 수량도 같은 만큼 바꾼다 (A·B 에서는 아무 일도 안 함) */
    private final RedisStock redisStock;

    /** 전체 취소: 재고와 1인 한도를 모두 되돌린다 */
    public OrderResponse cancel(Long memberId, Long orderId) {
        Target target = lockOrder(memberId, orderId);
        ProductOrder order = target.order();
        PurchaseCount count = lockCount(memberId, target.product(), target.key().performanceId());
        SaleItem item = lockItem(target.key().id());

        item.restore(order.getQuantity());
        count.subtract(order.getQuantity());
        order.cancel();
        redisStock.restoreAfterCommit(item.getId(), target.product().getId(), scopeOf(target.key()), memberId,
                order.getQuantity());
        orderNotices.canceled(order);
        return OrderResponse.from(order);
    }

    /** 수량 줄이기 (부분 취소): 줄인 만큼 재고와 1인 한도를 되돌린다 */
    public OrderResponse reduceQuantity(Long memberId, Long orderId, int quantity) {
        Target target = lockOrder(memberId, orderId);
        ProductOrder order = target.order();
        if (quantity >= order.getQuantity()) {
            throw new CustomException(ErrorCode.INVALID_INPUT,
                    "지금 수량(" + order.getQuantity() + ")보다 적게 입력해 주세요. 늘리려면 새로 구매해 주세요.");
        }
        int before = order.getQuantity();
        int returned = before - quantity;
        PurchaseCount count = lockCount(memberId, target.product(), target.key().performanceId());
        SaleItem item = lockItem(target.key().id());

        item.restore(returned);
        count.subtract(returned);
        order.reduceQuantity(quantity);
        redisStock.restoreAfterCommit(item.getId(), target.product().getId(), scopeOf(target.key()), memberId, returned);
        orderNotices.quantityReduced(order, before);
        return OrderResponse.from(order, target.changeableUntil());
    }

    /** 배송지 변경 (택배로 받는 굿즈만). 재고와 상관없어 주문 행만 잠근다 */
    public OrderResponse changeAddress(Long memberId, Long orderId, String address) {
        Target target = lockOrder(memberId, orderId);
        GoodsDetail goods = target.product().getType() == ProductType.GOODS
                ? goodsDetailRepository.findById(target.product().getId()).orElse(null)
                : null;
        if (goods == null || goods.getDeliveryType() != DeliveryType.PARCEL) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "택배로 받는 굿즈만 배송지를 바꿀 수 있습니다.");
        }
        target.order().changeAddress(address.trim());
        orderNotices.addressChanged(target.order());
        return OrderResponse.from(target.order(), target.changeableUntil());
    }

    /**
     * 회차·등급 바꾸기 (티켓만, 같은 수량 그대로).
     * 바꿀 쪽이 매진이거나 그 회차 한도를 넘으면 오류를 내고 원래 주문은 그대로 둔다.
     */
    public OrderResponse changeItem(Long memberId, Long orderId, Long saleItemId) {
        Target target = lockOrder(memberId, orderId);
        ProductOrder order = target.order();
        Product product = target.product();
        if (product.getType() != ProductType.TICKET) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "회차·좌석 등급은 티켓만 바꿀 수 있습니다.");
        }
        SaleItemKey from = target.key();
        SaleItemKey to = saleItemRepository.findKeyById(saleItemId)
                .filter(key -> key.productId().equals(product.getId()))
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INPUT, "이 공연의 회차·좌석 등급이 아닙니다."));
        if (to.id().equals(from.id())) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "지금과 같은 회차·좌석 등급입니다.");
        }
        LocalDateTime toCloseAt = changeableUntil(product, to.performanceId());
        if (!LocalDateTime.now().isBefore(toCloseAt)) {
            throw new CustomException(ErrorCode.NOT_ON_SALE, "예매가 마감된 회차로는 바꿀 수 없습니다.");
        }

        int quantity = order.getQuantity();
        boolean samePerformance = from.performanceId().equals(to.performanceId());

        // 1) 1인 한도: 다른 회차로 옮기면 두 회차의 한도를 같이 바꾼다 (회차 번호가 작은 쪽부터 잠금)
        PurchaseCount fromCount = null;
        PurchaseCount toCount = null;
        if (!samePerformance) {
            Map<Long, PurchaseCount> counts = List.of(from.performanceId(), to.performanceId()).stream()
                    .sorted()
                    .collect(Collectors.toMap(Function.identity(), scope -> lockCount(memberId, product, scope)));
            fromCount = counts.get(from.performanceId());
            toCount = counts.get(to.performanceId());
            int allowance = toCount.remainingAllowance(product.getMaxPerPerson());
            if (quantity > allowance) {
                throw new CustomException(ErrorCode.LIMIT_EXCEEDED, "바꿀 회차는 회차당 " + product.getMaxPerPerson()
                        + "매까지라 " + (allowance == 0 ? "더 예매할 수 없습니다." : allowance + "매만 더 예매할 수 있습니다."));
            }
        }

        // 2) 재고: 판매 단위 번호가 작은 쪽부터 잠금
        Map<Long, SaleItem> items = List.of(from.id(), to.id()).stream()
                .sorted()
                .collect(Collectors.toMap(Function.identity(), this::lockItem));
        SaleItem fromItem = items.get(from.id());
        SaleItem toItem = items.get(to.id());
        int remaining = toItem.getRemainingQuantity();
        if (remaining == 0) {
            throw new CustomException(ErrorCode.SOLD_OUT, "바꿀 좌석 등급이 매진되었습니다.");
        }
        if (quantity > remaining) {
            throw new CustomException(ErrorCode.INSUFFICIENT_STOCK,
                    "바꿀 좌석 등급은 " + remaining + "매 남아 " + quantity + "매로 바꿀 수 없습니다.");
        }

        // 3) 옮기기
        fromItem.restore(quantity);
        toItem.sell(quantity);
        if (!samePerformance) {
            fromCount.subtract(quantity);
            toCount.add(quantity);
        }
        order.changeSaleItem(toItem);
        redisStock.moveAfterCommit(from.id(), to.id(), product.getId(), from.performanceId(), to.performanceId(),
                memberId, quantity);
        orderNotices.itemChanged(order, fromItem);
        return OrderResponse.from(order, toCloseAt);
    }

    // ---------- 잠금과 확인 ----------

    /** 잠근 주문과, 재고를 읽지 않고 구한 판매 단위 번호·상품·기한 */
    private record Target(ProductOrder order, SaleItemKey key, Product product, LocalDateTime changeableUntil) {
    }

    /** 내 주문인지, 이미 취소했는지, 기한이 지났는지 확인한다. 남의 주문은 없는 주문처럼 답한다 */
    private Target lockOrder(Long memberId, Long orderId) {
        ProductOrder order = productOrderRepository.findByIdForUpdate(orderId)
                .filter(o -> o.isOrderedBy(memberId))
                .orElseThrow(() -> new CustomException(ErrorCode.ORDER_NOT_FOUND));
        if (order.isCanceled()) {
            throw new CustomException(ErrorCode.ALREADY_CANCELED);
        }
        // 재고 엔티티는 아직 읽지 않는다 (잠금 전에 읽으면 옛 판매 수량을 쓰게 됨, SaleItemKey 참고)
        SaleItemKey key = saleItemRepository.findKeyById(order.getSaleItem().getId())
                .orElseThrow(() -> new CustomException(ErrorCode.ORDER_NOT_FOUND));
        Product product = productRepository.findById(key.productId())
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
        LocalDateTime until = changeableUntil(product, key.performanceId());
        if (!LocalDateTime.now().isBefore(until)) {
            throw new CustomException(ErrorCode.CHANGE_CLOSED);
        }
        return new Target(order, key, product, until);
    }

    /** 티켓은 회차 예매 마감, 굿즈는 판매 종료 */
    private LocalDateTime changeableUntil(Product product, Long performanceId) {
        if (performanceId == null) {
            return product.getSaleEndAt();
        }
        Performance performance = performanceRepository.findById(performanceId)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
        TicketDetail ticket = ticketDetailRepository.findById(product.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
        return Performance.bookingCloseAt(performance.getEventAt(), product.getSaleEndAt(), ticket.getCloseBeforeHours());
    }

    /** 1인 한도 행 잠금 (회차를 바꿀 때 처음 예매하는 회차면 행을 만든 뒤 잠금) */
    private static Long scopeOf(SaleItemKey key) {
        return key.performanceId() == null ? PurchaseCountId.WHOLE_PRODUCT : key.performanceId();
    }

    private PurchaseCount lockCount(Long memberId, Product product, Long performanceId) {
        long scopeId = performanceId == null ? PurchaseCountId.WHOLE_PRODUCT : performanceId;
        purchaseCountRepository.insertIfAbsent(memberId, product.getId(), scopeId);
        PurchaseCountId id = new PurchaseCountId(memberId, product.getId(), scopeId);
        return purchaseCountRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalStateException("구매 수량 기록을 찾을 수 없습니다: " + id));
    }

    private SaleItem lockItem(Long saleItemId) {
        return saleItemRepository.findByIdForUpdate(saleItemId)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
    }
}

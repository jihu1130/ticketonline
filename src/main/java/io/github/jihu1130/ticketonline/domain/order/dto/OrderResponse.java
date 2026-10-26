package io.github.jihu1130.ticketonline.domain.order.dto;

import io.github.jihu1130.ticketonline.domain.order.entity.OrderStatus;
import io.github.jihu1130.ticketonline.domain.order.entity.ProductOrder;
import io.github.jihu1130.ticketonline.domain.product.entity.Product;
import io.github.jihu1130.ticketonline.domain.product.entity.ProductType;
import io.github.jihu1130.ticketonline.domain.product.entity.SaleItem;
import java.time.LocalDateTime;

/**
 * 주문 한 건 (구매 결과, 내 구매 내역).
 * eventAt, gradeName: 티켓만 (공연 일시, 좌석 등급). 굿즈는 null
 * address: 택배 굿즈만
 * saleItemId, performanceId: 회차·등급 바꾸기 화면에서 지금 고른 것 표시 (굿즈 performanceId 는 null)
 * changeableUntil: 이 시각 전까지 취소·변경할 수 있다 (티켓은 회차 예매 마감, 굿즈는 판매 종료). 취소된 주문은 null
 */
public record OrderResponse(
        Long orderId,
        Long productId,
        ProductType type,
        String title,
        String imageUrl,
        LocalDateTime eventAt,
        String gradeName,
        int quantity,
        int unitPrice,
        int totalPrice,
        OrderStatus status,
        String address,
        LocalDateTime orderedAt,
        Long saleItemId,
        Long performanceId,
        LocalDateTime changeableUntil
) {

    /** 구매 결과 (취소·변경 기한은 구매 내역에서 보여 준다) */
    public static OrderResponse from(ProductOrder order) {
        return from(order, null);
    }

    public static OrderResponse from(ProductOrder order, LocalDateTime changeableUntil) {
        SaleItem item = order.getSaleItem();
        Product product = item.getProduct();
        return new OrderResponse(
                order.getId(),
                product.getId(),
                product.getType(),
                product.getTitle(),
                product.getImageUrl(),
                item.getPerformance() == null ? null : item.getPerformance().getEventAt(),
                item.getSeatGrade() == null ? null : item.getSeatGrade().getName(),
                order.getQuantity(),
                order.getUnitPrice(),
                order.getTotalPrice(),
                order.getStatus(),
                order.getAddress(),
                order.getOrderedAt(),
                item.getId(),
                item.getPerformance() == null ? null : item.getPerformance().getId(),
                order.isCanceled() ? null : changeableUntil);
    }
}

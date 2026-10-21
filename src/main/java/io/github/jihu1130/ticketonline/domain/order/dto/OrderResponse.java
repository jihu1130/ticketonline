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
        LocalDateTime orderedAt
) {

    public static OrderResponse from(ProductOrder order) {
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
                order.getOrderedAt());
    }
}

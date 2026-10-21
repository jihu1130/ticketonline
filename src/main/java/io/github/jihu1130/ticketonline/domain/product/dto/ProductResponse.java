package io.github.jihu1130.ticketonline.domain.product.dto;

import io.github.jihu1130.ticketonline.domain.product.entity.DeliveryType;
import io.github.jihu1130.ticketonline.domain.product.entity.GoodsDetail;
import io.github.jihu1130.ticketonline.domain.product.entity.Product;
import io.github.jihu1130.ticketonline.domain.product.entity.ProductType;
import io.github.jihu1130.ticketonline.domain.product.entity.TicketDetail;
import java.time.LocalDateTime;

/** 상품 정보. 종류에 맞지 않는 상세 항목은 null이다. */
public record ProductResponse(
        Long id,
        ProductType type,
        String title,
        int price,
        int totalQuantity,
        int remainingQuantity,
        int maxPerPerson,
        LocalDateTime saleStartAt,
        LocalDateTime saleEndAt,
        LocalDateTime createdAt,
        LocalDateTime eventAt,
        String venue,
        String description,
        DeliveryType deliveryType
) {

    public static ProductResponse of(Product product, TicketDetail ticket, GoodsDetail goods) {
        return new ProductResponse(
                product.getId(),
                product.getType(),
                product.getTitle(),
                product.getPrice(),
                product.getTotalQuantity(),
                product.getRemainingQuantity(),
                product.getMaxPerPerson(),
                product.getSaleStartAt(),
                product.getSaleEndAt(),
                product.getCreatedAt(),
                ticket == null ? null : ticket.getEventAt(),
                ticket == null ? null : ticket.getVenue(),
                goods == null ? null : goods.getDescription(),
                goods == null ? null : goods.getDeliveryType());
    }
}

package io.github.jihu1130.ticketonline.domain.product.dto;

import io.github.jihu1130.ticketonline.domain.product.entity.ProductStatus;
import io.github.jihu1130.ticketonline.domain.product.entity.ProductType;
import java.time.LocalDateTime;
import java.util.Comparator;

/**
 * F-03 상품 목록의 카드 한 장.
 * minPrice: 가장 낮은 가격 (티켓은 등급 중 최저가, 굿즈는 가격)
 * venue, firstEventAt, lastEventAt: 티켓만 (공연 기간), 굿즈는 null
 */
public record ProductSummaryResponse(
        Long id,
        ProductType type,
        ProductStatus status,
        String title,
        String imageUrl,
        Integer minPrice,
        LocalDateTime saleStartAt,
        LocalDateTime saleEndAt,
        String venue,
        LocalDateTime firstEventAt,
        LocalDateTime lastEventAt
) {

    public static ProductSummaryResponse from(ProductResponse p) {
        Integer minPrice = p.saleItems().stream()
                .map(ProductResponse.SaleItemResponse::price)
                .min(Comparator.naturalOrder())
                .orElse(null);
        LocalDateTime firstEventAt = p.performances().stream()
                .map(ProductResponse.PerformanceResponse::eventAt)
                .min(Comparator.naturalOrder())
                .orElse(null);
        LocalDateTime lastEventAt = p.performances().stream()
                .map(ProductResponse.PerformanceResponse::eventAt)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return new ProductSummaryResponse(p.id(), p.type(), p.status(), p.title(), p.imageUrl(), minPrice,
                p.saleStartAt(), p.saleEndAt(), p.venue(), firstEventAt, lastEventAt);
    }
}

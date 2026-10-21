package io.github.jihu1130.ticketonline.domain.product.dto;

import io.github.jihu1130.ticketonline.domain.product.entity.DeliveryType;
import io.github.jihu1130.ticketonline.domain.product.entity.GoodsDetail;
import io.github.jihu1130.ticketonline.domain.product.entity.Performance;
import io.github.jihu1130.ticketonline.domain.product.entity.Product;
import io.github.jihu1130.ticketonline.domain.product.entity.ProductImage;
import io.github.jihu1130.ticketonline.domain.product.entity.ProductType;
import io.github.jihu1130.ticketonline.domain.product.entity.SaleItem;
import io.github.jihu1130.ticketonline.domain.product.entity.SeatGrade;
import io.github.jihu1130.ticketonline.domain.product.entity.TicketDetail;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 상품 정보. 종류에 맞지 않는 항목은 null 또는 빈 목록이다.
 * saleItems: 구매 대상(재고). 티켓은 회차 x 등급마다 1개, 굿즈는 1개.
 */
public record ProductResponse(
        Long id,
        ProductType type,
        String title,
        String imageUrl,
        int maxPerPerson,
        LocalDateTime saleStartAt,
        LocalDateTime saleEndAt,
        LocalDateTime createdAt,
        // 공통: 설명(굿즈 상품 설명 / 티켓 공연 소개, 없으면 null), 부가 사진(굿즈만)
        String description,
        List<String> images,
        // 티켓
        String venue,
        String seatMapUrl,
        Integer closeBeforeHours,
        List<SeatGradeResponse> seatGrades,
        List<PerformanceResponse> performances,
        // 굿즈
        DeliveryType deliveryType,
        // 공통 재고
        List<SaleItemResponse> saleItems
) {

    public record SeatGradeResponse(Long id, String name, int price, int seatCount) {
        static SeatGradeResponse from(SeatGrade g) {
            return new SeatGradeResponse(g.getId(), g.getName(), g.getPrice(), g.getSeatCount());
        }
    }

    /** bookingCloseAt: 이 회차의 예매 마감 시각 (판매 종료와 공연 시작 N시간 전 중 이른 시각) */
    public record PerformanceResponse(Long id, LocalDateTime eventAt, LocalDateTime bookingCloseAt) {
        static PerformanceResponse from(Performance p, LocalDateTime saleEndAt, int closeBeforeHours) {
            return new PerformanceResponse(p.getId(), p.getEventAt(),
                    Performance.bookingCloseAt(p.getEventAt(), saleEndAt, closeBeforeHours));
        }
    }

    /** performanceId, seatGradeId는 굿즈에서 null */
    public record SaleItemResponse(Long id, Long performanceId, Long seatGradeId,
                                   int price, int totalQuantity, int remainingQuantity) {
        static SaleItemResponse from(SaleItem s) {
            return new SaleItemResponse(
                    s.getId(),
                    s.getPerformance() == null ? null : s.getPerformance().getId(),
                    s.getSeatGrade() == null ? null : s.getSeatGrade().getId(),
                    s.getPrice(), s.getTotalQuantity(), s.getRemainingQuantity());
        }
    }

    public static ProductResponse of(Product product, TicketDetail ticket, GoodsDetail goods,
                                     List<SeatGrade> grades, List<Performance> performances,
                                     List<SaleItem> saleItems, List<ProductImage> images) {
        String description = ticket != null ? ticket.getDescription()
                : goods != null ? goods.getDescription() : null;
        return new ProductResponse(
                product.getId(),
                product.getType(),
                product.getTitle(),
                product.getImageUrl(),
                product.getMaxPerPerson(),
                product.getSaleStartAt(),
                product.getSaleEndAt(),
                product.getCreatedAt(),
                description,
                images.stream().map(ProductImage::getUrl).toList(),
                ticket == null ? null : ticket.getVenue(),
                ticket == null ? null : ticket.getSeatMapUrl(),
                ticket == null ? null : ticket.getCloseBeforeHours(),
                grades.stream().map(SeatGradeResponse::from).toList(),
                performances.stream()
                        .map(p -> PerformanceResponse.from(p, product.getSaleEndAt(),
                                ticket == null ? 0 : ticket.getCloseBeforeHours()))
                        .toList(),
                goods == null ? null : goods.getDeliveryType(),
                saleItems.stream().map(SaleItemResponse::from).toList());
    }
}

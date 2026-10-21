package io.github.jihu1130.ticketonline.domain.product.dto;

import io.github.jihu1130.ticketonline.domain.product.entity.DeliveryType;
import io.github.jihu1130.ticketonline.domain.product.entity.GoodsDetail;
import io.github.jihu1130.ticketonline.domain.product.entity.Performance;
import io.github.jihu1130.ticketonline.domain.product.entity.Product;
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
        // 티켓
        String venue,
        String seatMapUrl,
        List<SeatGradeResponse> seatGrades,
        List<PerformanceResponse> performances,
        // 굿즈
        String description,
        DeliveryType deliveryType,
        // 공통 재고
        List<SaleItemResponse> saleItems
) {

    public record SeatGradeResponse(Long id, String name, int price, int seatCount) {
        static SeatGradeResponse from(SeatGrade g) {
            return new SeatGradeResponse(g.getId(), g.getName(), g.getPrice(), g.getSeatCount());
        }
    }

    public record PerformanceResponse(Long id, LocalDateTime eventAt) {
        static PerformanceResponse from(Performance p) {
            return new PerformanceResponse(p.getId(), p.getEventAt());
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
                                     List<SaleItem> saleItems) {
        return new ProductResponse(
                product.getId(),
                product.getType(),
                product.getTitle(),
                product.getImageUrl(),
                product.getMaxPerPerson(),
                product.getSaleStartAt(),
                product.getSaleEndAt(),
                product.getCreatedAt(),
                ticket == null ? null : ticket.getVenue(),
                ticket == null ? null : ticket.getSeatMapUrl(),
                grades.stream().map(SeatGradeResponse::from).toList(),
                performances.stream().map(PerformanceResponse::from).toList(),
                goods == null ? null : goods.getDescription(),
                goods == null ? null : goods.getDeliveryType(),
                saleItems.stream().map(SaleItemResponse::from).toList());
    }
}

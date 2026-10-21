package io.github.jihu1130.ticketonline.domain.product.dto;

import io.github.jihu1130.ticketonline.domain.product.entity.DeliveryType;
import io.github.jihu1130.ticketonline.domain.product.entity.GoodsDetail;
import io.github.jihu1130.ticketonline.domain.product.entity.Performance;
import io.github.jihu1130.ticketonline.domain.product.entity.PerformanceStatus;
import io.github.jihu1130.ticketonline.domain.product.entity.Product;
import io.github.jihu1130.ticketonline.domain.product.entity.ProductImage;
import io.github.jihu1130.ticketonline.domain.product.entity.ProductStatus;
import io.github.jihu1130.ticketonline.domain.product.entity.ProductType;
import io.github.jihu1130.ticketonline.domain.product.entity.SaleItem;
import io.github.jihu1130.ticketonline.domain.product.entity.SeatGrade;
import io.github.jihu1130.ticketonline.domain.product.entity.TicketDetail;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 상품 정보 (상품 상세, 내 판매 상품). 종류에 맞지 않는 항목은 null 또는 빈 목록이다.
 * status: 판매 상태 (조회 시각 기준으로 계산)
 * saleItems: 구매 대상(재고). 티켓은 회차 x 등급마다 1개, 굿즈는 1개.
 */
public record ProductResponse(
        Long id,
        ProductType type,
        ProductStatus status,
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

    /**
     * bookingCloseAt: 이 회차의 예매 마감 시각 (판매 종료와 공연 시작 N시간 전 중 이른 시각)
     * status: 이 회차의 예매 상태 (예매 전 / 예매 중 / 매진 / 예매 마감)
     */
    public record PerformanceResponse(Long id, LocalDateTime eventAt, LocalDateTime bookingCloseAt,
                                      PerformanceStatus status) {
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

    /** @param now 판매 상태를 계산할 기준 시각 (서버 시간) */
    public static ProductResponse of(Product product, TicketDetail ticket, GoodsDetail goods,
                                     List<SeatGrade> grades, List<Performance> performances,
                                     List<SaleItem> saleItems, List<ProductImage> images,
                                     LocalDateTime now) {
        String description = ticket != null ? ticket.getDescription()
                : goods != null ? goods.getDescription() : null;
        int closeBeforeHours = ticket == null ? 0 : ticket.getCloseBeforeHours();

        List<PerformanceResponse> performanceResponses = performances.stream()
                .map(p -> {
                    LocalDateTime closeAt = Performance.bookingCloseAt(
                            p.getEventAt(), product.getSaleEndAt(), closeBeforeHours);
                    PerformanceStatus status = PerformanceStatus.of(
                            product.getSaleStartAt(), closeAt, remainingOf(p, saleItems), now);
                    return new PerformanceResponse(p.getId(), p.getEventAt(), closeAt, status);
                })
                .toList();

        ProductStatus status = product.getType() == ProductType.TICKET
                ? ProductStatus.ofTicket(product.getSaleStartAt(), product.getSaleEndAt(),
                        performanceResponses.stream().map(PerformanceResponse::status).toList(), now)
                : ProductStatus.ofGoods(product.getSaleStartAt(), product.getSaleEndAt(),
                        saleItems.stream().mapToInt(SaleItem::getRemainingQuantity).sum(), now);

        return new ProductResponse(
                product.getId(),
                product.getType(),
                status,
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
                performanceResponses,
                goods == null ? null : goods.getDeliveryType(),
                saleItems.stream().map(SaleItemResponse::from).toList());
    }

    /** 이 회차의 등급별 남은 좌석 합계 */
    private static int remainingOf(Performance performance, List<SaleItem> saleItems) {
        return saleItems.stream()
                .filter(s -> isSame(s.getPerformance(), performance))
                .mapToInt(SaleItem::getRemainingQuantity)
                .sum();
    }

    /** 같은 객체이거나 id가 같으면 같은 회차 (저장 직후에는 id가 없을 수 있어 객체도 비교) */
    private static boolean isSame(Performance a, Performance b) {
        if (a == null) {
            return false;
        }
        return a == b || (b.getId() != null && b.getId().equals(a.getId()));
    }
}

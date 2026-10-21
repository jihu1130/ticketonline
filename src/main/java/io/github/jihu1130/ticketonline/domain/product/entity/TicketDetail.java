package io.github.jihu1130.ticketonline.domain.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 종류가 TICKET일 때만 쓰는 정보. 공연 일시는 회차(Performance)에 있다. */
@Entity
@Table(name = "ticket_detail")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TicketDetail {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    /** 장소 (공연장, 팬미팅장 등) */
    @Column(nullable = false, length = 200)
    private String venue;

    /** 좌석 배치도 사진. 없으면 null */
    @Column(length = 255)
    private String seatMapUrl;

    /** 회차별 예매 마감: 공연 시작 몇 시간 전 (0~72). 모든 회차에 같게 적용 */
    @Column(nullable = false)
    private int closeBeforeHours;

    /** 공연 소개 (선택). 없으면 null */
    @Column(columnDefinition = "TEXT")
    private String description;

    @Builder
    private TicketDetail(Product product, String venue, String seatMapUrl, int closeBeforeHours,
                         String description) {
        this.product = product;
        this.venue = venue;
        this.seatMapUrl = seatMapUrl;
        this.closeBeforeHours = closeBeforeHours;
        this.description = description;
    }

    public void update(String venue, String seatMapUrl, int closeBeforeHours, String description) {
        this.venue = venue;
        this.seatMapUrl = seatMapUrl;
        this.closeBeforeHours = closeBeforeHours;
        this.description = description;
    }
}

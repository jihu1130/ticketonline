package io.github.jihu1130.ticketonline.domain.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 종류가 TICKET일 때만 쓰는 정보 (ERD01 TICKET_DETAIL) */
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

    /** 공연 일시 */
    @Column(nullable = false)
    private LocalDateTime eventAt;

    /** 장소 */
    @Column(nullable = false, length = 200)
    private String venue;

    @Builder
    private TicketDetail(Product product, LocalDateTime eventAt, String venue) {
        this.product = product;
        this.eventAt = eventAt;
        this.venue = venue;
    }

    public void update(LocalDateTime eventAt, String venue) {
        this.eventAt = eventAt;
        this.venue = venue;
    }
}

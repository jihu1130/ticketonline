package io.github.jihu1130.ticketonline.domain.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 티켓 공연 회차 */
@Entity
@Table(name = "performance")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Performance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "performance_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** 공연 일시 */
    @Column(nullable = false)
    private LocalDateTime eventAt;

    @Builder
    private Performance(Product product, LocalDateTime eventAt) {
        this.product = product;
        this.eventAt = eventAt;
    }

    /**
     * 이 회차의 예매 마감 시각 = 상품 판매 종료와 "공연 시작 N시간 전" 중 이른 시각.
     * 예) 판매 종료 11/22 22:00, 1시간 전 마감 -> 11/2 19:00 공연은 11/2 18:00, 11/25 공연은 11/22 22:00
     */
    public static LocalDateTime bookingCloseAt(LocalDateTime eventAt, LocalDateTime saleEndAt, int closeBeforeHours) {
        LocalDateTime beforeEvent = eventAt.minusHours(closeBeforeHours);
        return beforeEvent.isBefore(saleEndAt) ? beforeEvent : saleEndAt;
    }
}

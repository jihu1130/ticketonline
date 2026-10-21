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
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 판매 단위 = 재고. 구매는 항상 SaleItem 하나를 대상으로 한다.
 * 티켓: 회차 x 등급마다 1개 / 굿즈: 상품당 1개 (performance, seatGrade가 null)
 * 구매 방식 A/B/C 모두 이 행의 soldQuantity(또는 Redis 키)를 줄인다.
 */
@Entity
@Table(name = "sale_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SaleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sale_item_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** 티켓 회차 (굿즈는 null) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performance_id")
    private Performance performance;

    /** 티켓 좌석 등급 (굿즈는 null) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_grade_id")
    private SeatGrade seatGrade;

    @Column(nullable = false)
    private int price;

    @Column(nullable = false)
    private int totalQuantity;

    /** 판매된 수량 (totalQuantity 이하) */
    @Column(nullable = false)
    private int soldQuantity;

    /** 낙관적 락(방식 B)용 버전 */
    @Version
    @Column(nullable = false)
    private Long version;

    @Builder
    private SaleItem(Product product, Performance performance, SeatGrade seatGrade,
                     int price, int totalQuantity) {
        this.product = product;
        this.performance = performance;
        this.seatGrade = seatGrade;
        this.price = price;
        this.totalQuantity = totalQuantity;
        this.soldQuantity = 0;
    }

    public int getRemainingQuantity() {
        return totalQuantity - soldQuantity;
    }

    /**
     * 판매 수량을 늘린다. 남은 수량 확인은 호출하는 쪽에서 먼저 하고,
     * 여기서는 초과 판매가 절대 저장되지 않도록 한 번 더 막는다.
     */
    public void sell(int quantity) {
        if (quantity <= 0 || quantity > getRemainingQuantity()) {
            throw new IllegalStateException("남은 수량보다 많이 팔 수 없습니다: " + quantity);
        }
        this.soldQuantity += quantity;
    }
}

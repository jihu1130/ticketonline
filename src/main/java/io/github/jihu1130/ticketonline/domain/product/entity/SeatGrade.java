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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 티켓 좌석 등급 (VIP, R석 등). 좌석 수는 모든 회차에 똑같이 적용된다. */
@Entity
@Table(name = "seat_grade")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeatGrade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seat_grade_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(nullable = false)
    private int price;

    /** 회차 하나당 좌석 수 */
    @Column(nullable = false)
    private int seatCount;

    @Builder
    private SeatGrade(Product product, String name, int price, int seatCount) {
        this.product = product;
        this.name = name;
        this.price = price;
        this.seatCount = seatCount;
    }
}

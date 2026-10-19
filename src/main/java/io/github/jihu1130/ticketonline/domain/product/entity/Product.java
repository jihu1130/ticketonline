package io.github.jihu1130.ticketonline.domain.product.entity;

import io.github.jihu1130.ticketonline.domain.member.entity.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 티켓과 한정 물품의 공통 정보와 판매 수량 (ERD01 PRODUCT) */
@Entity
@Table(name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private Member seller;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductType type;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false)
    private int price;

    /** 판매 총 수량 */
    @Column(nullable = false)
    private int totalQuantity;

    /** 판매된 수량 (totalQuantity 이하) */
    @Column(nullable = false)
    private int soldQuantity;

    /** 1인 구매 제한 수량 */
    @Column(nullable = false)
    private int maxPerPerson;

    @Column(nullable = false)
    private LocalDateTime saleStartAt;

    @Column(nullable = false)
    private LocalDateTime saleEndAt;

    /** 낙관적 락(방식 B)용 버전 */
    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Product(Member seller, ProductType type, String title, int price, int totalQuantity,
                    int maxPerPerson, LocalDateTime saleStartAt, LocalDateTime saleEndAt) {
        this.seller = seller;
        this.type = type;
        this.title = title;
        this.price = price;
        this.totalQuantity = totalQuantity;
        this.soldQuantity = 0;
        this.maxPerPerson = maxPerPerson;
        this.saleStartAt = saleStartAt;
        this.saleEndAt = saleEndAt;
    }

    @PrePersist
    private void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public int getRemainingQuantity() {
        return totalQuantity - soldQuantity;
    }
}

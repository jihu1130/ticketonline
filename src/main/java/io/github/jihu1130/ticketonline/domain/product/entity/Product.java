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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 티켓과 굿즈의 공통 정보.
 * 가격과 재고는 판매 단위(SaleItem)에 있다. 티켓은 회차 x 등급마다, 굿즈는 1개.
 */
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

    /** 대표 사진 (티켓 포스터, 굿즈 사진). 없으면 null */
    @Column(length = 255)
    private String imageUrl;

    /** 1인 구매 제한 수량. 티켓은 회차당, 굿즈는 상품당 */
    @Column(nullable = false)
    private int maxPerPerson;

    @Column(nullable = false)
    private LocalDateTime saleStartAt;

    @Column(nullable = false)
    private LocalDateTime saleEndAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Product(Member seller, ProductType type, String title, String imageUrl, int maxPerPerson,
                    LocalDateTime saleStartAt, LocalDateTime saleEndAt) {
        this.seller = seller;
        this.type = type;
        this.title = title;
        this.imageUrl = imageUrl;
        this.maxPerPerson = maxPerPerson;
        this.saleStartAt = saleStartAt;
        this.saleEndAt = saleEndAt;
    }

    @PrePersist
    private void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public void update(String title, String imageUrl, int maxPerPerson,
                       LocalDateTime saleStartAt, LocalDateTime saleEndAt) {
        this.title = title;
        this.imageUrl = imageUrl;
        this.maxPerPerson = maxPerPerson;
        this.saleStartAt = saleStartAt;
        this.saleEndAt = saleEndAt;
    }

    public boolean isSaleStarted(LocalDateTime now) {
        return !now.isBefore(saleStartAt);
    }

    public boolean isOwnedBy(Long memberId) {
        return seller.getId().equals(memberId);
    }
}

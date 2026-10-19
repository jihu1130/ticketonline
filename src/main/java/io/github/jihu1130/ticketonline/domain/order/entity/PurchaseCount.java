package io.github.jihu1130.ticketonline.domain.order.entity;

import io.github.jihu1130.ticketonline.domain.member.entity.Member;
import io.github.jihu1130.ticketonline.domain.product.entity.Product;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원별로 지금까지 산 수량. 구매할 때 이 값과 Product.maxPerPerson을 비교해
 * 1인 구매 제한을 지킨다. (ERD02 PURCHASE_COUNT)
 */
@Entity
@Table(name = "purchase_count")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseCount {

    @EmbeddedId
    private PurchaseCountId id;

    @MapsId("memberId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id")
    private Member member;

    @MapsId("productId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    /** 지금까지 산 수량 (maxPerPerson 이하) */
    @Column(nullable = false)
    private int boughtQuantity;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public PurchaseCount(Member member, Product product) {
        this.id = new PurchaseCountId(member.getId(), product.getId());
        this.member = member;
        this.product = product;
        this.boughtQuantity = 0;
    }

    @PrePersist
    @PreUpdate
    private void onSave() {
        this.updatedAt = LocalDateTime.now();
    }
}

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
 * 티켓은 회차마다, 굿즈는 상품마다 한 행 (PurchaseCountId.scopeId 참고)
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

    public PurchaseCount(Member member, Product product, Long scopeId) {
        this.id = new PurchaseCountId(member.getId(), product.getId(), scopeId);
        this.member = member;
        this.product = product;
        this.boughtQuantity = 0;
    }

    /** 1인 한도 안에서 몇 개 더 살 수 있는지 */
    public int remainingAllowance(int maxPerPerson) {
        return Math.max(0, maxPerPerson - boughtQuantity);
    }

    /** 구매한 수량을 더한다. 한도 확인은 호출하는 쪽(잠금을 잡은 상태)에서 먼저 한다. */
    public void add(int quantity) {
        this.boughtQuantity += quantity;
    }

    @PrePersist
    @PreUpdate
    private void onSave() {
        this.updatedAt = LocalDateTime.now();
    }
}

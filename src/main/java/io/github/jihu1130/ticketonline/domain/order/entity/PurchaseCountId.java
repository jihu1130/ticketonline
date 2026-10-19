package io.github.jihu1130.ticketonline.domain.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** PurchaseCount의 복합 키 (회원 + 상품) */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseCountId implements Serializable {

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "product_id")
    private Long productId;

    public PurchaseCountId(Long memberId, Long productId) {
        this.memberId = memberId;
        this.productId = productId;
    }
}

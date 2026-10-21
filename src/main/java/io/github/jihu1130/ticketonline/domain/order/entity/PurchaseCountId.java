package io.github.jihu1130.ticketonline.domain.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * PurchaseCount의 복합 키 (회원 + 상품 + 한도 범위)
 * 1인 구매 한도를 세는 범위: 티켓은 회차(scopeId = 회차 id), 굿즈는 상품 전체(scopeId = 0).
 * 키 컬럼에는 null을 쓸 수 없어 굿즈는 0을 넣는다.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseCountId implements Serializable {

    /** 굿즈처럼 회차가 없는 상품의 scopeId */
    public static final long WHOLE_PRODUCT = 0L;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "scope_id")
    private Long scopeId;

    public PurchaseCountId(Long memberId, Long productId, Long scopeId) {
        this.memberId = memberId;
        this.productId = productId;
        this.scopeId = scopeId;
    }
}

package io.github.jihu1130.ticketonline.domain.order.entity;

import io.github.jihu1130.ticketonline.domain.member.entity.Member;
import io.github.jihu1130.ticketonline.domain.product.entity.SaleItem;
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
 * 구매 기록. 한 사람이 여러 번 살 수 있으므로 주문마다 수량을 저장한다. (ERD02 PRODUCT_ORDER)
 * 구매 대상은 판매 단위(SaleItem): 티켓은 회차 x 등급, 굿즈는 상품.
 */
@Entity
@Table(name = "product_order")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_item_id", nullable = false)
    private SaleItem saleItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    /** 구매 수량 */
    @Column(nullable = false)
    private int quantity;

    /** 구매 당시 1개 가격 (주문 금액 = unitPrice x quantity) */
    @Column(nullable = false)
    private int unitPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    /** 배송 주소 (택배 물품일 때만) */
    @Column(length = 255)
    private String address;

    @Column(nullable = false, updatable = false)
    private LocalDateTime orderedAt;

    /** 결제 기능이 없어 바로 구매 확정(SUCCESS)으로 만든다. */
    @Builder
    private ProductOrder(SaleItem saleItem, Member member, int quantity, String address) {
        this.saleItem = saleItem;
        this.member = member;
        this.quantity = quantity;
        this.unitPrice = saleItem.getPrice();
        this.address = address;
        this.status = OrderStatus.SUCCESS;
    }

    public int getTotalPrice() {
        return unitPrice * quantity;
    }

    @PrePersist
    private void onCreate() {
        this.orderedAt = LocalDateTime.now();
    }
}

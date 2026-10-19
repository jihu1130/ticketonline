package io.github.jihu1130.ticketonline.domain.order.entity;

import io.github.jihu1130.ticketonline.domain.member.entity.Member;
import io.github.jihu1130.ticketonline.domain.product.entity.Product;
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

/** 구매 기록. 한 사람이 여러 번 살 수 있으므로 주문마다 수량을 저장한다. (ERD02 PRODUCT_ORDER) */
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
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    /** 구매 수량 */
    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    /** 배송 주소 (택배 물품일 때만) */
    @Column(length = 255)
    private String address;

    @Column(nullable = false, updatable = false)
    private LocalDateTime orderedAt;

    @Builder
    private ProductOrder(Product product, Member member, int quantity, String address) {
        this.product = product;
        this.member = member;
        this.quantity = quantity;
        this.address = address;
        this.status = OrderStatus.SUCCESS;
    }

    @PrePersist
    private void onCreate() {
        this.orderedAt = LocalDateTime.now();
    }
}

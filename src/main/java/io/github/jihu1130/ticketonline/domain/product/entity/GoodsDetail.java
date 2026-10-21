package io.github.jihu1130.ticketonline.domain.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 종류가 GOODS일 때만 쓰는 정보 (ERD01 GOODS_DETAIL) */
@Entity
@Table(name = "goods_detail")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoodsDetail {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    /** 상품 설명 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryType deliveryType;

    @Builder
    private GoodsDetail(Product product, String description, DeliveryType deliveryType) {
        this.product = product;
        this.description = description;
        this.deliveryType = deliveryType;
    }

    public void update(String description, DeliveryType deliveryType) {
        this.description = description;
        this.deliveryType = deliveryType;
    }
}

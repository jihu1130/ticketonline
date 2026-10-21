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

/**
 * 상품 부가 사진 (대표 사진 Product.imageUrl 외에 추가로 보여 줄 사진).
 * 지금은 굿즈만 최대 10장 (ProductService에서 확인).
 */
@Entity
@Table(name = "product_image")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_image_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 255)
    private String url;

    /** 보여 줄 순서 (0부터) */
    @Column(nullable = false)
    private int sortOrder;

    @Builder
    private ProductImage(Product product, String url, int sortOrder) {
        this.product = product;
        this.url = url;
        this.sortOrder = sortOrder;
    }
}

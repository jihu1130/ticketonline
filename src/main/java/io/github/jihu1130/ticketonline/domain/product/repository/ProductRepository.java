package io.github.jihu1130.ticketonline.domain.product.repository;

import io.github.jihu1130.ticketonline.domain.product.entity.Product;
import io.github.jihu1130.ticketonline.domain.product.entity.ProductType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /** 판매자 본인의 상품 목록 (최근 등록 순) */
    List<Product> findBySellerIdOrderByCreatedAtDesc(Long sellerId);

    /** 종류별 상품 목록 (순서는 서비스에서 판매 상태로 정렬) */
    List<Product> findByType(ProductType type);
}

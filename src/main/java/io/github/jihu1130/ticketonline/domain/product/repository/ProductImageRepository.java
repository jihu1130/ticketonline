package io.github.jihu1130.ticketonline.domain.product.repository;

import io.github.jihu1130.ticketonline.domain.product.entity.ProductImage;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    /** 여러 상품의 부가 사진 (상품별 보여 줄 순서대로) */
    List<ProductImage> findByProductIdInOrderBySortOrderAsc(Collection<Long> productIds);

    @Modifying
    @Query("delete from ProductImage i where i.product.id = :productId")
    void deleteByProductId(@Param("productId") Long productId);
}

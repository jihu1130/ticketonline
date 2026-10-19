package io.github.jihu1130.ticketonline.domain.product.repository;

import io.github.jihu1130.ticketonline.domain.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

/** 구매 방식 A(비관적 락)용 조회 메서드는 6단계에서 추가한다. */
public interface ProductRepository extends JpaRepository<Product, Long> {
}

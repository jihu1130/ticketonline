package io.github.jihu1130.ticketonline.domain.order.repository;

import io.github.jihu1130.ticketonline.domain.order.entity.ProductOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductOrderRepository extends JpaRepository<ProductOrder, Long> {
}

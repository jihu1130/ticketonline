package io.github.jihu1130.ticketonline.domain.product.repository;

import io.github.jihu1130.ticketonline.domain.product.entity.SeatGrade;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeatGradeRepository extends JpaRepository<SeatGrade, Long> {

    /** 여러 상품의 좌석 등급 (등록한 순서) */
    List<SeatGrade> findByProductIdInOrderByIdAsc(Collection<Long> productIds);

    @Modifying
    @Query("delete from SeatGrade g where g.product.id = :productId")
    void deleteByProductId(@Param("productId") Long productId);
}

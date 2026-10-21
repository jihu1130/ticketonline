package io.github.jihu1130.ticketonline.domain.product.dto;

import io.github.jihu1130.ticketonline.domain.product.entity.DeliveryType;
import java.time.LocalDateTime;
import java.util.List;

/** 등록·수정 요청이 함께 쓰는 입력 항목. 서비스는 이 타입으로 같은 규칙을 검사한다. */
public interface ProductInput {

    String title();

    String imageUrl();

    Integer maxPerPerson();

    LocalDateTime saleStartAt();

    LocalDateTime saleEndAt();

    // 티켓
    String venue();

    String seatMapUrl();

    List<SeatGradeRequest> seatGrades();

    List<LocalDateTime> performances();

    // 굿즈
    Integer price();

    Integer quantity();

    String description();

    DeliveryType deliveryType();
}

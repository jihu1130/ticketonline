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

    /** 공연 시작 몇 시간 전에 예매를 마감할지. null이면 기본값 */
    Integer closeBeforeHours();

    /** 상품 설명. 굿즈는 필수, 티켓은 공연 소개(선택) */
    String description();

    /** 부가 사진 주소 목록 (굿즈만, 최대 10장). 보낸 순서대로 보여 준다 */
    List<String> images();

    // 굿즈
    Integer price();

    Integer quantity();

    DeliveryType deliveryType();
}

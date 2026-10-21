package io.github.jihu1130.ticketonline.domain.product.dto;

import io.github.jihu1130.ticketonline.domain.product.entity.DeliveryType;
import io.github.jihu1130.ticketonline.domain.product.entity.ProductType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/**
 * F-02 상품 등록.
 * TICKET: venue, seatGrades(등급), performances(회차 일시), seatMapUrl(선택),
 *         closeBeforeHours(공연 시작 몇 시간 전 예매 마감, 생략 시 1)
 * GOODS : price, quantity, description, deliveryType
 * 종류별 필수 항목은 서비스에서 확인한다. 사진 주소는 POST /api/images 로 받은 값.
 */
public record ProductCreateRequest(
        @NotNull(message = "상품 종류를 선택해 주세요.")
        ProductType type,

        @NotBlank(message = "상품명을 입력해 주세요.")
        @Size(max = 100, message = "상품명은 100자 이하로 입력해 주세요.")
        String title,

        String imageUrl,

        @NotNull(message = "1인 구매 제한 수량을 입력해 주세요.")
        @Min(value = 1, message = "1인 구매 제한 수량은 1개 이상이어야 합니다.")
        Integer maxPerPerson,

        @NotNull(message = "판매 시작 일시를 입력해 주세요.")
        LocalDateTime saleStartAt,

        @NotNull(message = "판매 종료 일시를 입력해 주세요.")
        LocalDateTime saleEndAt,

        // 티켓
        @Size(max = 200, message = "장소는 200자 이하로 입력해 주세요.")
        String venue,
        String seatMapUrl,
        List<@Valid SeatGradeRequest> seatGrades,
        List<LocalDateTime> performances,
        @Min(value = 0, message = "예매 마감은 공연 시작 0시간 전 이상이어야 합니다.")
        @Max(value = 72, message = "예매 마감은 공연 시작 72시간 전까지 정할 수 있습니다.")
        Integer closeBeforeHours,

        // 굿즈
        @Min(value = 0, message = "가격은 0원 이상이어야 합니다.")
        @Max(value = 100_000_000, message = "가격은 1억 원 이하로 입력해 주세요.")
        Integer price,

        @Min(value = 1, message = "판매 수량은 1개 이상이어야 합니다.")
        @Max(value = 1_000_000, message = "판매 수량은 1,000,000개 이하로 입력해 주세요.")
        Integer quantity,

        @Size(max = 2000, message = "상품 설명은 2000자 이하로 입력해 주세요.")
        String description,
        DeliveryType deliveryType
) implements ProductInput {
}

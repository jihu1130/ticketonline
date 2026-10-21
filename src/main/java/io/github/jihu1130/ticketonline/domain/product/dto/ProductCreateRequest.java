package io.github.jihu1130.ticketonline.domain.product.dto;

import io.github.jihu1130.ticketonline.domain.product.entity.DeliveryType;
import io.github.jihu1130.ticketonline.domain.product.entity.ProductType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * F-02 상품 등록.
 * type이 TICKET이면 eventAt·venue, GOODS면 description·deliveryType을 채운다 (서비스에서 확인).
 */
public record ProductCreateRequest(
        @NotNull(message = "상품 종류를 선택해 주세요.")
        ProductType type,

        @NotBlank(message = "상품명을 입력해 주세요.")
        @Size(max = 100, message = "상품명은 100자 이하로 입력해 주세요.")
        String title,

        @NotNull(message = "가격을 입력해 주세요.")
        @Min(value = 0, message = "가격은 0원 이상이어야 합니다.")
        @Max(value = 100_000_000, message = "가격은 1억 원 이하로 입력해 주세요.")
        Integer price,

        @NotNull(message = "판매 수량을 입력해 주세요.")
        @Min(value = 1, message = "판매 수량은 1개 이상이어야 합니다.")
        Integer totalQuantity,

        @NotNull(message = "1인 구매 제한 수량을 입력해 주세요.")
        @Min(value = 1, message = "1인 구매 제한 수량은 1개 이상이어야 합니다.")
        Integer maxPerPerson,

        @NotNull(message = "판매 시작 일시를 입력해 주세요.")
        LocalDateTime saleStartAt,

        @NotNull(message = "판매 종료 일시를 입력해 주세요.")
        LocalDateTime saleEndAt,

        // 티켓
        LocalDateTime eventAt,
        @Size(max = 200, message = "장소는 200자 이하로 입력해 주세요.")
        String venue,

        // 굿즈
        @Size(max = 2000, message = "상품 설명은 2000자 이하로 입력해 주세요.")
        String description,
        DeliveryType deliveryType
) {
}

package io.github.jihu1130.ticketonline.domain.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * F-04 구매 요청 (결제 없이 바로 구매 확정).
 * saleItemId: 구매할 판매 단위. 티켓은 회차 x 등급을 골라 필수, 굿즈는 생략 가능(상품의 판매 단위 1개)
 * address: 택배로 받는 굿즈만 필수
 */
public record OrderRequest(
        Long saleItemId,

        @NotNull(message = "구매 수량을 입력해 주세요.")
        @Min(value = 1, message = "구매 수량은 1개 이상이어야 합니다.")
        @Max(value = 100, message = "한 번에 100개까지 구매할 수 있습니다.")
        Integer quantity,

        @Size(max = 255, message = "배송 주소는 255자 이하로 입력해 주세요.")
        String address
) {
}

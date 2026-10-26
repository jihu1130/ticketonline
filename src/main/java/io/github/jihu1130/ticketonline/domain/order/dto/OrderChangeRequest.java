package io.github.jihu1130.ticketonline.domain.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 내 주문 변경 요청들 */
public final class OrderChangeRequest {

    private OrderChangeRequest() {
    }

    /** 수량 줄이기 (부분 취소). 지금 수량보다 적게 */
    public record Quantity(
            @NotNull(message = "바꿀 수량을 입력해 주세요.")
            @Min(value = 1, message = "수량은 1개 이상이어야 합니다. 모두 취소하려면 주문 취소를 이용해 주세요.")
            @Max(value = 100, message = "수량은 100개 이하로 입력해 주세요.")
            Integer quantity
    ) {
    }

    /** 배송지 변경 (택배 굿즈만) */
    public record Address(
            @NotBlank(message = "배송 주소를 입력해 주세요.")
            @Size(max = 255, message = "배송 주소는 255자 이하로 입력해 주세요.")
            String address
    ) {
    }

    /** 회차·등급 바꾸기 (티켓만, 같은 상품의 다른 판매 단위로) */
    public record Item(
            @NotNull(message = "바꿀 회차와 좌석 등급을 선택해 주세요.")
            Long saleItemId
    ) {
    }
}

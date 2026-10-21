package io.github.jihu1130.ticketonline.domain.product.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 좌석 등급 입력. 좌석 수는 모든 회차에 똑같이 적용된다. */
public record SeatGradeRequest(
        @NotBlank(message = "등급 이름을 입력해 주세요.")
        @Size(max = 30, message = "등급 이름은 30자 이하로 입력해 주세요.")
        String name,

        @NotNull(message = "등급 가격을 입력해 주세요.")
        @Min(value = 0, message = "가격은 0원 이상이어야 합니다.")
        @Max(value = 100_000_000, message = "가격은 1억 원 이하로 입력해 주세요.")
        Integer price,

        @NotNull(message = "등급 좌석 수를 입력해 주세요.")
        @Min(value = 1, message = "좌석 수는 1석 이상이어야 합니다.")
        @Max(value = 100_000, message = "좌석 수는 100,000석 이하로 입력해 주세요.")
        Integer seatCount
) {
}

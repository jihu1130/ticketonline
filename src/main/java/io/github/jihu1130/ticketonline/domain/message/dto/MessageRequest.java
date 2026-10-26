package io.github.jihu1130.ticketonline.domain.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 쪽지 보내기 요청들 */
public final class MessageRequest {

    private MessageRequest() {
    }

    /** 상품 문의: 그 상품의 판매자에게 보낸다 */
    public record Send(
            @NotNull(message = "문의할 상품을 선택해 주세요.")
            Long productId,

            @NotBlank(message = "내용을 입력해 주세요.")
            @Size(max = 1000, message = "내용은 1000자 이하로 입력해 주세요.")
            String content
    ) {
    }

    /** 받은 쪽지에 답장: 보낸 사람에게 보낸다 */
    public record Reply(
            @NotBlank(message = "내용을 입력해 주세요.")
            @Size(max = 1000, message = "내용은 1000자 이하로 입력해 주세요.")
            String content
    ) {
    }
}

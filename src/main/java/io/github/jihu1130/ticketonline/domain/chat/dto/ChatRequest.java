package io.github.jihu1130.ticketonline.domain.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 채팅 요청들 */
public final class ChatRequest {

    private ChatRequest() {
    }

    /** 상품 판매자와의 채팅방 열기 (없으면 만든다) */
    public record Open(
            @NotNull(message = "문의할 상품을 선택해 주세요.")
            Long productId
    ) {
    }

    public record Send(
            @NotBlank(message = "메시지를 입력해 주세요.")
            @Size(max = 1000, message = "메시지는 1000자 이하로 입력해 주세요.")
            String content
    ) {
    }
}

package io.github.jihu1130.ticketonline.domain.chat.dto;

import io.github.jihu1130.ticketonline.domain.chat.entity.ChatMessage;
import java.time.LocalDateTime;

/** 채팅 메시지 한 건. mine: 내가 보낸 메시지면 true (화면 오른쪽 말풍선) */
public record ChatMessageResponse(
        Long id,
        boolean mine,
        String senderName,
        String content,
        LocalDateTime createdAt
) {

    public static ChatMessageResponse of(ChatMessage message, Long viewerId) {
        return new ChatMessageResponse(
                message.getId(),
                message.isSentBy(viewerId),
                message.getSender().getName(),
                message.getContent(),
                message.getCreatedAt());
    }
}

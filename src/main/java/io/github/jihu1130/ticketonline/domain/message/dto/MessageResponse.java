package io.github.jihu1130.ticketonline.domain.message.dto;

import io.github.jihu1130.ticketonline.domain.message.entity.Message;
import io.github.jihu1130.ticketonline.domain.message.entity.MessageType;
import io.github.jihu1130.ticketonline.domain.product.entity.Product;
import java.time.LocalDateTime;

/**
 * 메시지 한 건.
 * senderName: 알림이면 null (화면에서 "티켓온라인"으로 표시)
 * productId, productTitle: 관련 상품이 없으면 null
 * canReply: 받은 쪽지만 true
 */
public record MessageResponse(
        Long id,
        MessageType type,
        String senderName,
        String receiverName,
        Long productId,
        String productTitle,
        String content,
        boolean read,
        LocalDateTime createdAt,
        boolean canReply
) {

    public static MessageResponse of(Message message, Long viewerId) {
        Product product = message.getProduct();
        boolean direct = message.getType() == MessageType.DIRECT;
        return new MessageResponse(
                message.getId(),
                message.getType(),
                message.getSender() == null ? null : message.getSender().getName(),
                message.getReceiver().getName(),
                product == null ? null : product.getId(),
                product == null ? null : product.getTitle(),
                message.getContent(),
                message.isRead(),
                message.getCreatedAt(),
                direct && message.isReceivedBy(viewerId));
    }
}

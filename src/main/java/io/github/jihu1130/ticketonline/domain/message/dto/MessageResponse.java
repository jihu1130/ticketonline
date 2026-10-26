package io.github.jihu1130.ticketonline.domain.message.dto;

import io.github.jihu1130.ticketonline.domain.message.entity.Message;
import io.github.jihu1130.ticketonline.domain.product.entity.Product;
import java.time.LocalDateTime;

/** 알림 한 건. productId, productTitle: 관련 상품이 없으면 null */
public record MessageResponse(
        Long id,
        Long productId,
        String productTitle,
        String content,
        boolean read,
        LocalDateTime createdAt
) {

    public static MessageResponse from(Message message) {
        Product product = message.getProduct();
        return new MessageResponse(
                message.getId(),
                product == null ? null : product.getId(),
                product == null ? null : product.getTitle(),
                message.getContent(),
                message.isRead(),
                message.getCreatedAt());
    }
}

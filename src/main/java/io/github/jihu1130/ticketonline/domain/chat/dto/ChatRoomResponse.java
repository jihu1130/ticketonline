package io.github.jihu1130.ticketonline.domain.chat.dto;

import io.github.jihu1130.ticketonline.domain.chat.entity.ChatRoom;
import io.github.jihu1130.ticketonline.domain.product.entity.Product;
import java.time.LocalDateTime;

/**
 * 채팅방 한 개 (목록, 방 열기).
 * partnerName: 대화 상대 이름 / iAmSeller: 내가 이 상품의 판매자인지 (화면에서 "구매 문의" / "판매자" 표시)
 * lastMessage, lastMessageAt: 아직 대화가 없으면 null
 */
public record ChatRoomResponse(
        Long id,
        Long productId,
        String productTitle,
        String productImageUrl,
        String partnerName,
        boolean iAmSeller,
        String lastMessage,
        LocalDateTime lastMessageAt,
        long unreadCount
) {

    public static ChatRoomResponse of(ChatRoom room, Long viewerId, long unreadCount) {
        Product product = room.getProduct();
        return new ChatRoomResponse(
                room.getId(),
                product.getId(),
                product.getTitle(),
                product.getImageUrl(),
                room.partnerOf(viewerId).getName(),
                !room.isBuyer(viewerId),
                room.getLastMessage(),
                room.getLastMessageAt(),
                unreadCount);
    }
}

package io.github.jihu1130.ticketonline.domain.message.entity;

/**
 * 메시지 종류. 지금은 서비스가 보내는 알림(구매 완료, 취소·변경 완료, 내 상품 매진)만 있다.
 * 회원 간 대화는 채팅(chat_room, chat_message)으로 한다.
 */
public enum MessageType {
    NOTICE
}

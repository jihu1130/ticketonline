package io.github.jihu1130.ticketonline.domain.message.entity;

/** 메시지 종류 */
public enum MessageType {
    /** 서비스가 보내는 알림 (구매 완료, 취소·변경 완료, 내 상품 매진). 보낸 사람 없음 */
    NOTICE,
    /** 회원 간 쪽지 (상품 문의와 답장) */
    DIRECT
}

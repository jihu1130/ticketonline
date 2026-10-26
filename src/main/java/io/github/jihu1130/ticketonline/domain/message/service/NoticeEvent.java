package io.github.jihu1130.ticketonline.domain.message.service;

/**
 * 알림 보내기 요청. 구매·취소·변경 트랜잭션 안에서 발행하고, 커밋된 뒤에만 NoticeListener 가 저장한다.
 * (구매가 실패해 되돌려지면 알림도 보내지 않는다)
 *
 * @param productId 관련 상품 (없으면 null)
 */
public record NoticeEvent(Long receiverId, Long productId, String content) {
}

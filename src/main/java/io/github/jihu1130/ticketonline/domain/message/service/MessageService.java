package io.github.jihu1130.ticketonline.domain.message.service;

import io.github.jihu1130.ticketonline.domain.chat.service.ChatService;
import io.github.jihu1130.ticketonline.domain.member.repository.MemberRepository;
import io.github.jihu1130.ticketonline.domain.message.dto.MessageResponse;
import io.github.jihu1130.ticketonline.domain.message.dto.UnreadCountResponse;
import io.github.jihu1130.ticketonline.domain.message.entity.Message;
import io.github.jihu1130.ticketonline.domain.message.repository.MessageRepository;
import io.github.jihu1130.ticketonline.domain.product.repository.ProductRepository;
import io.github.jihu1130.ticketonline.global.exception.CustomException;
import io.github.jihu1130.ticketonline.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메시지함 알림 (구매·취소·변경 완료, 내 상품 매진). 회원 간 대화는 ChatService.
 * 남의 알림은 없는 알림처럼 답한다 (MESSAGE_NOT_FOUND).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageService {

    /** 최근 100건까지 보여 준다 */
    static final int LIST_SIZE = 100;

    private final MessageRepository messageRepository;
    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;
    private final ChatService chatService;

    public List<MessageResponse> getNotices(Long memberId) {
        return messageRepository.findNotices(memberId, PageRequest.of(0, LIST_SIZE)).stream()
                .map(MessageResponse::from)
                .toList();
    }

    /** 헤더·탭 숫자: 안 읽은 알림 + 안 읽은 채팅 메시지 */
    public UnreadCountResponse countUnread(Long memberId) {
        return UnreadCountResponse.of(messageRepository.countByReceiverIdAndReadAtIsNull(memberId),
                chatService.countUnread(memberId));
    }

    @Transactional
    public MessageResponse markRead(Long memberId, Long messageId) {
        Message message = messageRepository.findById(messageId)
                .filter(m -> m.isReceivedBy(memberId))
                .orElseThrow(() -> new CustomException(ErrorCode.MESSAGE_NOT_FOUND));
        message.markRead();
        return MessageResponse.from(message);
    }

    @Transactional
    public UnreadCountResponse markAllRead(Long memberId) {
        messageRepository.markAllRead(memberId);
        return countUnread(memberId);
    }

    /** 알림 저장 (NoticeListener 가 구매·취소·변경이 커밋된 뒤 부른다) */
    @Transactional
    public void saveNotice(NoticeEvent event) {
        messageRepository.save(Message.builder()
                .receiver(memberRepository.getReferenceById(event.receiverId()))
                .product(event.productId() == null ? null : productRepository.getReferenceById(event.productId()))
                .content(event.content())
                .build());
    }
}

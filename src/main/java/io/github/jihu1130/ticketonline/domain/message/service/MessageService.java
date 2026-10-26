package io.github.jihu1130.ticketonline.domain.message.service;

import io.github.jihu1130.ticketonline.domain.member.entity.Member;
import io.github.jihu1130.ticketonline.domain.member.repository.MemberRepository;
import io.github.jihu1130.ticketonline.domain.message.dto.MessageBox;
import io.github.jihu1130.ticketonline.domain.message.dto.MessageResponse;
import io.github.jihu1130.ticketonline.domain.message.dto.UnreadCountResponse;
import io.github.jihu1130.ticketonline.domain.message.entity.Message;
import io.github.jihu1130.ticketonline.domain.message.entity.MessageType;
import io.github.jihu1130.ticketonline.domain.message.repository.MessageRepository;
import io.github.jihu1130.ticketonline.domain.product.entity.Product;
import io.github.jihu1130.ticketonline.domain.product.repository.ProductRepository;
import io.github.jihu1130.ticketonline.global.exception.CustomException;
import io.github.jihu1130.ticketonline.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메시지함: 알림(서비스가 보냄)과 쪽지(상품 문의, 답장).
 * 남의 메시지는 없는 메시지처럼 답한다 (MESSAGE_NOT_FOUND).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageService {

    /** 탭마다 최근 100건까지 보여 준다 */
    static final int LIST_SIZE = 100;

    private final MessageRepository messageRepository;
    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;

    public List<MessageResponse> getMessages(Long memberId, MessageBox box) {
        Pageable page = PageRequest.of(0, LIST_SIZE);
        List<Message> messages = switch (box) {
            case NOTICE -> messageRepository.findReceived(memberId, MessageType.NOTICE, page);
            case INBOX -> messageRepository.findReceived(memberId, MessageType.DIRECT, page);
            case SENT -> messageRepository.findSent(memberId, page);
        };
        return messages.stream().map(m -> MessageResponse.of(m, memberId)).toList();
    }

    public UnreadCountResponse countUnread(Long memberId) {
        return UnreadCountResponse.of(
                messageRepository.countByReceiverIdAndTypeAndReadAtIsNull(memberId, MessageType.NOTICE),
                messageRepository.countByReceiverIdAndTypeAndReadAtIsNull(memberId, MessageType.DIRECT));
    }

    /** 상품 문의: 그 상품의 판매자에게 쪽지를 보낸다 (본인 상품에는 보낼 수 없음) */
    @Transactional
    public MessageResponse send(Long memberId, Long productId, String content) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
        if (product.isOwnedBy(memberId)) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "본인이 등록한 상품에는 문의를 보낼 수 없습니다.");
        }
        return saveDirect(memberId, product.getSeller(), product, content);
    }

    /** 받은 쪽지에 답장: 보낸 사람에게, 같은 상품으로 */
    @Transactional
    public MessageResponse reply(Long memberId, Long messageId, String content) {
        Message original = findReceived(memberId, messageId);
        if (original.getType() != MessageType.DIRECT || original.getSender() == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "알림에는 답장할 수 없습니다.");
        }
        return saveDirect(memberId, original.getSender(), original.getProduct(), content);
    }

    @Transactional
    public MessageResponse markRead(Long memberId, Long messageId) {
        Message message = findReceived(memberId, messageId);
        message.markRead();
        return MessageResponse.of(message, memberId);
    }

    /** 알림 또는 받은 쪽지 모두 읽음. 보낸 쪽지 탭은 읽음 표시가 없다 */
    @Transactional
    public UnreadCountResponse markAllRead(Long memberId, MessageBox box) {
        if (box == MessageBox.SENT) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "보낸 쪽지는 읽음 표시가 없습니다.");
        }
        messageRepository.markAllRead(memberId, box == MessageBox.NOTICE ? MessageType.NOTICE : MessageType.DIRECT);
        return countUnread(memberId);
    }

    /** 알림 저장 (NoticeListener 가 구매·취소·변경이 커밋된 뒤 부른다) */
    @Transactional
    public void saveNotice(NoticeEvent event) {
        messageRepository.save(Message.builder()
                .receiver(memberRepository.getReferenceById(event.receiverId()))
                .type(MessageType.NOTICE)
                .product(event.productId() == null ? null : productRepository.getReferenceById(event.productId()))
                .content(event.content())
                .build());
    }

    private MessageResponse saveDirect(Long senderId, Member receiver, Product product, String content) {
        Message message = messageRepository.save(Message.builder()
                .receiver(receiver)
                .sender(memberRepository.getReferenceById(senderId))
                .type(MessageType.DIRECT)
                .product(product)
                .content(content.trim())
                .build());
        return MessageResponse.of(message, senderId);
    }

    private Message findReceived(Long memberId, Long messageId) {
        return messageRepository.findById(messageId)
                .filter(m -> m.isReceivedBy(memberId))
                .orElseThrow(() -> new CustomException(ErrorCode.MESSAGE_NOT_FOUND));
    }
}

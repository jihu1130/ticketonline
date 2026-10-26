package io.github.jihu1130.ticketonline.domain.chat.service;

import io.github.jihu1130.ticketonline.domain.chat.dto.ChatMessageResponse;
import io.github.jihu1130.ticketonline.domain.chat.dto.ChatRoomResponse;
import io.github.jihu1130.ticketonline.domain.chat.entity.ChatMessage;
import io.github.jihu1130.ticketonline.domain.chat.entity.ChatRoom;
import io.github.jihu1130.ticketonline.domain.chat.repository.ChatMessageRepository;
import io.github.jihu1130.ticketonline.domain.chat.repository.ChatRoomRepository;
import io.github.jihu1130.ticketonline.domain.member.repository.MemberRepository;
import io.github.jihu1130.ticketonline.domain.product.entity.Product;
import io.github.jihu1130.ticketonline.domain.product.repository.ProductRepository;
import io.github.jihu1130.ticketonline.global.exception.CustomException;
import io.github.jihu1130.ticketonline.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품 문의 채팅. 상품 하나 x 구매자 한 명 = 방 하나.
 * 화면은 열린 방을 2초마다 "마지막으로 받은 번호 이후" 메시지만 가져가고, 목록은 10초마다 다시 가져간다.
 * 방에 참여하지 않은 사람에게는 없는 방처럼 답한다 (CHAT_NOT_FOUND).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    /** 방에 처음 들어올 때 보여 줄 최근 메시지 수 */
    static final int FIRST_PAGE = 100;
    /** 2초마다 한 번에 가져갈 최대 수 */
    static final int NEXT_PAGE = 200;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ProductRepository productRepository;
    private final MemberRepository memberRepository;

    /**
     * 상품 판매자와의 방을 연다 (없으면 만든다). 본인 상품에는 문의할 수 없다.
     * 같은 사람이 버튼을 두 번 빨리 눌러 동시에 만들면 (상품, 구매자) 유일 조건에 걸리므로, 그때는 먼저 만든 방을 쓴다.
     * 그래서 이 메서드는 트랜잭션 없이 각 조회·저장이 따로 커밋된다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ChatRoomResponse openRoom(Long memberId, Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
        if (product.isOwnedBy(memberId)) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "본인이 등록한 상품에는 문의할 수 없습니다.");
        }
        Long roomId = chatRoomRepository.findByProductIdAndBuyerId(productId, memberId)
                .map(ChatRoom::getId)
                .orElseGet(() -> create(product, memberId));
        ChatRoom room = chatRoomRepository.findDetailById(roomId).orElseThrow();
        return ChatRoomResponse.of(room, memberId, unreadByRoom(memberId).getOrDefault(roomId, 0L));
    }

    private Long create(Product product, Long memberId) {
        try {
            return chatRoomRepository.save(ChatRoom.builder()
                    .product(product)
                    .buyer(memberRepository.getReferenceById(memberId))
                    .seller(product.getSeller())
                    .build()).getId();
        } catch (DataIntegrityViolationException e) {
            return chatRoomRepository.findByProductIdAndBuyerId(product.getId(), memberId)
                    .map(ChatRoom::getId)
                    .orElseThrow(() -> e);
        }
    }

    /** 내 채팅방 목록 (최근 대화 순, 방마다 안 읽은 개수) */
    public List<ChatRoomResponse> getRooms(Long memberId) {
        Map<Long, Long> unread = unreadByRoom(memberId);
        return chatRoomRepository.findMyRooms(memberId).stream()
                .map(room -> ChatRoomResponse.of(room, memberId, unread.getOrDefault(room.getId(), 0L)))
                .toList();
    }

    /**
     * 방의 메시지. afterId 가 없으면 최근 100건, 있으면 그 번호 다음부터.
     * 가져간 메시지까지 읽음으로 표시한다 (방을 열어 본 것 = 읽음).
     */
    @Transactional
    public List<ChatMessageResponse> getMessages(Long memberId, Long roomId, Long afterId) {
        ChatRoom room = findMyRoom(memberId, roomId);
        List<ChatMessage> messages;
        if (afterId == null) {
            messages = new ArrayList<>(chatMessageRepository.findLatest(roomId, PageRequest.of(0, FIRST_PAGE)));
            Collections.reverse(messages);
        } else {
            messages = chatMessageRepository.findAfter(roomId, afterId, PageRequest.of(0, NEXT_PAGE));
        }
        if (!messages.isEmpty()) {
            long lastId = messages.get(messages.size() - 1).getId();
            if (room.isBuyer(memberId)) {
                chatRoomRepository.markBuyerRead(roomId, lastId);
            } else {
                chatRoomRepository.markSellerRead(roomId, lastId);
            }
        }
        return messages.stream().map(m -> ChatMessageResponse.of(m, memberId)).toList();
    }

    @Transactional
    public ChatMessageResponse send(Long memberId, Long roomId, String content) {
        ChatRoom room = chatRoomRepository.findByIdForUpdate(roomId)
                .filter(r -> r.hasMember(memberId))
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_NOT_FOUND));
        ChatMessage message = chatMessageRepository.save(ChatMessage.builder()
                .room(room)
                .sender(memberRepository.getReferenceById(memberId))
                .content(content.trim())
                .build());
        room.onMessage(message);
        return ChatMessageResponse.of(message, memberId);
    }

    /** 헤더 숫자용: 모든 방의 안 읽은 메시지 합계 */
    public long countUnread(Long memberId) {
        return unreadByRoom(memberId).values().stream().mapToLong(Long::longValue).sum();
    }

    private Map<Long, Long> unreadByRoom(Long memberId) {
        return chatMessageRepository.countUnreadByRoom(memberId).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }

    private ChatRoom findMyRoom(Long memberId, Long roomId) {
        return chatRoomRepository.findDetailById(roomId)
                .filter(room -> room.hasMember(memberId))
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_NOT_FOUND));
    }
}

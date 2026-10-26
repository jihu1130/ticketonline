package io.github.jihu1130.ticketonline.domain.message.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import io.github.jihu1130.ticketonline.domain.chat.service.ChatService;
import io.github.jihu1130.ticketonline.domain.member.entity.Member;
import io.github.jihu1130.ticketonline.domain.member.repository.MemberRepository;
import io.github.jihu1130.ticketonline.domain.message.dto.MessageResponse;
import io.github.jihu1130.ticketonline.domain.message.dto.UnreadCountResponse;
import io.github.jihu1130.ticketonline.domain.message.entity.Message;
import io.github.jihu1130.ticketonline.domain.message.entity.MessageType;
import io.github.jihu1130.ticketonline.domain.message.repository.MessageRepository;
import io.github.jihu1130.ticketonline.domain.product.repository.ProductRepository;
import io.github.jihu1130.ticketonline.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

/** 알림 읽음·저장 규칙과 안 읽은 개수 (알림 + 채팅) */
class MessageServiceTest {

    private static final Long BUYER_ID = 2L;
    private static final Long OTHER_ID = 3L;

    private final MessageRepository messageRepository = Mockito.mock(MessageRepository.class);
    private final MemberRepository memberRepository = Mockito.mock(MemberRepository.class);
    private final ProductRepository productRepository = Mockito.mock(ProductRepository.class);
    private final ChatService chatService = Mockito.mock(ChatService.class);
    private MessageService messageService;

    private final Member buyer = member(BUYER_ID);

    @BeforeEach
    void setUp() {
        messageService = new MessageService(messageRepository, memberRepository, productRepository, chatService);
    }

    @Test
    void markRead_byReceiver() {
        Message notice = saved(100L);

        MessageResponse response = messageService.markRead(BUYER_ID, notice.getId());

        assertThat(response.read()).isTrue();
        assertThat(notice.getReadAt()).isNotNull();
    }

    @Test
    void othersNotice_looksLikeNotFound() {
        Message notice = saved(100L);

        assertThatThrownBy(() -> messageService.markRead(OTHER_ID, notice.getId()))
                .extracting("errorCode").isEqualTo(ErrorCode.MESSAGE_NOT_FOUND);
        assertThat(notice.isRead()).isFalse();
    }

    @Test
    void saveNotice_withoutSender() {
        given(memberRepository.getReferenceById(BUYER_ID)).willReturn(buyer);

        messageService.saveNotice(new NoticeEvent(BUYER_ID, null, "구매가 확정되었습니다."));

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(captor.capture());
        assertThat(captor.getValue().getReceiver()).isSameAs(buyer);
        assertThat(captor.getValue().getType()).isEqualTo(MessageType.NOTICE);
        assertThat(captor.getValue().getProduct()).isNull();
    }

    @Test
    void countUnread_addsNoticesAndChat() {
        given(messageRepository.countByReceiverIdAndReadAtIsNull(BUYER_ID)).willReturn(2L);
        given(chatService.countUnread(BUYER_ID)).willReturn(5L);

        UnreadCountResponse count = messageService.countUnread(BUYER_ID);

        assertThat(count.notice()).isEqualTo(2);
        assertThat(count.chat()).isEqualTo(5);
        assertThat(count.total()).isEqualTo(7);
    }

    private Message saved(Long id) {
        Message message = Message.builder().receiver(buyer).content("구매가 확정되었습니다.").build();
        ReflectionTestUtils.setField(message, "id", id);
        given(messageRepository.findById(id)).willReturn(Optional.of(message));
        given(messageRepository.save(any())).willAnswer(call -> call.getArgument(0));
        return message;
    }

    private static Member member(Long id) {
        Member member = Member.builder().email("m" + id + "@test.com").password("pw").name("회원" + id).build();
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}

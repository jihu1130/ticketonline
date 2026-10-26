package io.github.jihu1130.ticketonline.domain.message.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.jihu1130.ticketonline.domain.member.entity.Member;
import io.github.jihu1130.ticketonline.domain.member.repository.MemberRepository;
import io.github.jihu1130.ticketonline.domain.message.dto.MessageBox;
import io.github.jihu1130.ticketonline.domain.message.dto.MessageResponse;
import io.github.jihu1130.ticketonline.domain.message.entity.Message;
import io.github.jihu1130.ticketonline.domain.message.entity.MessageType;
import io.github.jihu1130.ticketonline.domain.message.repository.MessageRepository;
import io.github.jihu1130.ticketonline.domain.product.entity.Product;
import io.github.jihu1130.ticketonline.domain.product.entity.ProductType;
import io.github.jihu1130.ticketonline.domain.product.repository.ProductRepository;
import io.github.jihu1130.ticketonline.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

/** 쪽지 보내기·답장·읽음의 규칙 (누구에게 가는지, 남의 메시지는 못 건드리는지) */
class MessageServiceTest {

    private static final Long SELLER_ID = 1L;
    private static final Long BUYER_ID = 2L;
    private static final Long OTHER_ID = 3L;
    private static final Long PRODUCT_ID = 10L;

    private final MessageRepository messageRepository = Mockito.mock(MessageRepository.class);
    private final MemberRepository memberRepository = Mockito.mock(MemberRepository.class);
    private final ProductRepository productRepository = Mockito.mock(ProductRepository.class);
    private MessageService messageService;

    private final Member seller = member(SELLER_ID, "판매자");
    private final Member buyer = member(BUYER_ID, "구매자");
    private Product product;

    @BeforeEach
    void setUp() {
        messageService = new MessageService(messageRepository, memberRepository, productRepository);
        product = Product.builder().seller(seller).type(ProductType.GOODS).title("응원봉").maxPerPerson(2)
                .saleStartAt(LocalDateTime.now()).saleEndAt(LocalDateTime.now().plusDays(1)).build();
        ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
        given(memberRepository.getReferenceById(SELLER_ID)).willReturn(seller);
        given(memberRepository.getReferenceById(BUYER_ID)).willReturn(buyer);
        given(messageRepository.save(any())).willAnswer(call -> call.getArgument(0));
    }

    @Test
    void send_goesToSellerOfProduct() {
        MessageResponse response = messageService.send(BUYER_ID, PRODUCT_ID, "  배송은 언제 되나요?  ");

        Message saved = captureSaved();
        assertThat(saved.getReceiver()).isSameAs(seller);
        assertThat(saved.getSender()).isSameAs(buyer);
        assertThat(saved.getType()).isEqualTo(MessageType.DIRECT);
        assertThat(saved.getContent()).isEqualTo("배송은 언제 되나요?");
        assertThat(response.productTitle()).isEqualTo("응원봉");
        assertThat(response.canReply()).isFalse(); // 내가 보낸 쪽지
    }

    @Test
    void send_toOwnProduct_rejected() {
        assertThatThrownBy(() -> messageService.send(SELLER_ID, PRODUCT_ID, "내용"))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
        verify(messageRepository, never()).save(any());
    }

    @Test
    void reply_goesBackToSender() {
        Message question = saved(direct(buyer, seller), 100L);

        messageService.reply(SELLER_ID, question.getId(), "내일 보내 드립니다.");

        Message answer = captureSaved();
        assertThat(answer.getReceiver()).isSameAs(buyer);
        assertThat(answer.getSender()).isSameAs(seller);
        assertThat(answer.getProduct()).isSameAs(product);
    }

    @Test
    void reply_toNotice_rejected() {
        Message notice = saved(Message.builder().receiver(buyer).type(MessageType.NOTICE).content("구매 확정").build(), 101L);

        assertThatThrownBy(() -> messageService.reply(BUYER_ID, notice.getId(), "답장"))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void othersMessage_looksLikeNotFound() {
        Message question = saved(direct(buyer, seller), 100L);

        assertThatThrownBy(() -> messageService.reply(OTHER_ID, question.getId(), "끼어들기"))
                .extracting("errorCode").isEqualTo(ErrorCode.MESSAGE_NOT_FOUND);
        // 보낸 사람도 자기가 보낸 쪽지를 "읽음"으로 바꿀 수 없다 (받는 사람만)
        assertThatThrownBy(() -> messageService.markRead(BUYER_ID, question.getId()))
                .extracting("errorCode").isEqualTo(ErrorCode.MESSAGE_NOT_FOUND);
    }

    @Test
    void markRead_byReceiver() {
        Message question = saved(direct(buyer, seller), 100L);

        MessageResponse response = messageService.markRead(SELLER_ID, question.getId());

        assertThat(response.read()).isTrue();
        assertThat(response.canReply()).isTrue();
    }

    @Test
    void markAllRead_sentBox_rejected() {
        assertThatThrownBy(() -> messageService.markAllRead(BUYER_ID, MessageBox.SENT))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
    }

    private Message direct(Member from, Member to) {
        return Message.builder().sender(from).receiver(to).type(MessageType.DIRECT).product(product).content("문의").build();
    }

    private Message saved(Message message, Long id) {
        ReflectionTestUtils.setField(message, "id", id);
        given(messageRepository.findById(id)).willReturn(Optional.of(message));
        return message;
    }

    private Message captureSaved() {
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(captor.capture());
        return captor.getValue();
    }

    private static Member member(Long id, String name) {
        Member member = Member.builder().email("m" + id + "@test.com").password("pw").name(name).build();
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}

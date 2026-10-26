package io.github.jihu1130.ticketonline.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.jihu1130.ticketonline.domain.chat.dto.ChatMessageResponse;
import io.github.jihu1130.ticketonline.domain.chat.dto.ChatRoomResponse;
import io.github.jihu1130.ticketonline.domain.chat.service.ChatService;
import io.github.jihu1130.ticketonline.domain.message.service.MessageService;
import io.github.jihu1130.ticketonline.global.exception.CustomException;
import io.github.jihu1130.ticketonline.global.exception.ErrorCode;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 상품 문의 채팅 (실제 DB): 방 열기, 주고받기, 2초마다 새 메시지만 받기, 안 읽은 개수와 읽음, 권한.
 */
class ChatTest extends ConcurrencyTestSupport {

    @Autowired private ChatService chatService;
    @Autowired private MessageService messageService;

    @Test
    void openRoom_sameProductSameBuyer_sameRoom() {
        GoodsFixture goods = createGoods(10, 2);
        List<Long> buyers = createMembers(2);

        ChatRoomResponse first = chatService.openRoom(buyers.get(0), goods.productId());
        ChatRoomResponse again = chatService.openRoom(buyers.get(0), goods.productId());
        ChatRoomResponse other = chatService.openRoom(buyers.get(1), goods.productId());

        assertThat(again.id()).isEqualTo(first.id());
        assertThat(other.id()).isNotEqualTo(first.id());
        assertThat(first.iAmSeller()).isFalse();
        assertThat(first.partnerName()).startsWith("구매자"); // 판매자도 테스트 회원이라 이름이 "구매자N"
        assertThat(first.lastMessage()).isNull();
    }

    @Test
    void openRoom_ownProduct_rejected() {
        GoodsFixture goods = createGoods(10, 2);

        assertError(() -> chatService.openRoom(sellerOf(goods.productId()), goods.productId()), ErrorCode.INVALID_INPUT);
        assertError(() -> chatService.openRoom(sellerOf(goods.productId()), -1L), ErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    void conversation_unreadCountsAndIncrementalFetch() {
        GoodsFixture goods = createGoods(10, 2);
        Long seller = sellerOf(goods.productId());
        Long buyer = createMembers(1).get(0);
        Long roomId = chatService.openRoom(buyer, goods.productId()).id();
        // 아무 말도 안 했으면 판매자 목록에는 아직 없다
        assertThat(chatService.getRooms(seller)).isEmpty();

        ChatMessageResponse question = chatService.send(buyer, roomId, "  배송은 언제 되나요?  ");
        chatService.send(buyer, roomId, "택배로 받을게요.");

        assertThat(question.mine()).isTrue();
        assertThat(question.content()).isEqualTo("배송은 언제 되나요?");
        List<ChatRoomResponse> sellerRooms = chatService.getRooms(seller);
        assertThat(sellerRooms).hasSize(1);
        assertThat(sellerRooms.get(0).iAmSeller()).isTrue();
        assertThat(sellerRooms.get(0).unreadCount()).isEqualTo(2);
        assertThat(sellerRooms.get(0).lastMessage()).isEqualTo("택배로 받을게요.");
        assertThat(messageService.countUnread(seller).chat()).isEqualTo(2);
        // 내가 보낸 메시지는 안 읽은 개수에 들어가지 않는다
        assertThat(chatService.getRooms(buyer).get(0).unreadCount()).isZero();

        // 판매자가 방을 연다 -> 2건 읽음
        List<ChatMessageResponse> opened = chatService.getMessages(seller, roomId, null);
        assertThat(opened).extracting(ChatMessageResponse::content).containsExactly("배송은 언제 되나요?", "택배로 받을게요.");
        assertThat(opened).noneMatch(ChatMessageResponse::mine);
        assertThat(messageService.countUnread(seller).chat()).isZero();

        // 판매자 답장 -> 구매자는 마지막으로 받은 번호 다음 것만 받는다
        long lastSeen = chatService.getMessages(buyer, roomId, null).get(1).id();
        assertThat(chatService.getRooms(buyer).get(0).unreadCount()).isZero();
        chatService.send(seller, roomId, "내일 보내 드립니다.");
        assertThat(messageService.countUnread(buyer).chat()).isEqualTo(1);

        List<ChatMessageResponse> next = chatService.getMessages(buyer, roomId, lastSeen);
        assertThat(next).extracting(ChatMessageResponse::content).containsExactly("내일 보내 드립니다.");
        assertThat(next.get(0).mine()).isFalse();
        assertThat(messageService.countUnread(buyer).chat()).isZero();
        assertThat(chatService.getMessages(buyer, roomId, next.get(0).id())).isEmpty();
    }

    @Test
    void outsider_cannotSeeOrSend() {
        GoodsFixture goods = createGoods(10, 2);
        List<Long> members = createMembers(2);
        Long roomId = chatService.openRoom(members.get(0), goods.productId()).id();

        assertError(() -> chatService.getMessages(members.get(1), roomId, null), ErrorCode.CHAT_NOT_FOUND);
        assertError(() -> chatService.send(members.get(1), roomId, "끼어들기"), ErrorCode.CHAT_NOT_FOUND);
        assertError(() -> chatService.send(members.get(0), -1L, "없는 방"), ErrorCode.CHAT_NOT_FOUND);
    }

    @Test
    void openRoomAtOnce_makesOneRoom() throws InterruptedException {
        GoodsFixture goods = createGoods(10, 2);
        Long buyer = createMembers(1).get(0);
        Set<Long> roomIds = ConcurrentHashMap.newKeySet();
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(10);
        for (int i = 0; i < 10; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    roomIds.add(chatService.openRoom(buyer, goods.productId()).id());
                } catch (Exception ignored) {
                    // 아래에서 방 개수로 확인한다
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(roomIds).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from chat_room where product_id = ? and buyer_id = ?",
                Integer.class, goods.productId(), buyer)).isEqualTo(1);
    }

    @Test
    void sendAtOnce_bothSides_allSavedInOrder() throws InterruptedException {
        GoodsFixture goods = createGoods(10, 2);
        Long seller = sellerOf(goods.productId());
        Long buyer = createMembers(1).get(0);
        Long roomId = chatService.openRoom(buyer, goods.productId()).id();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(40);
        for (int i = 0; i < 40; i++) {
            Long sender = i % 2 == 0 ? buyer : seller;
            String text = "메시지" + i;
            pool.submit(() -> {
                try {
                    start.await();
                    chatService.send(sender, roomId, text);
                } catch (Exception ignored) {
                    // 아래에서 개수로 확인한다
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        List<ChatMessageResponse> all = chatService.getMessages(buyer, roomId, null);
        assertThat(all).hasSize(40);
        // 목록의 마지막 메시지 = 실제로 가장 마지막에 저장된 메시지
        assertThat(chatService.getRooms(buyer).get(0).lastMessage()).isEqualTo(all.get(39).content());
        assertThat(messageService.countUnread(buyer).chat()).isZero();
        assertThat(messageService.countUnread(seller).chat()).isEqualTo(20);
    }

    private Long sellerOf(Long productId) {
        return jdbc.queryForObject("select seller_id from product where product_id = ?", Long.class, productId);
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(CustomException.class).extracting("errorCode").isEqualTo(code);
    }
}

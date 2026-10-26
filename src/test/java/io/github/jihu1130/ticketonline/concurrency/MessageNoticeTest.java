package io.github.jihu1130.ticketonline.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.jihu1130.ticketonline.domain.message.dto.MessageResponse;
import io.github.jihu1130.ticketonline.domain.message.service.MessageService;
import io.github.jihu1130.ticketonline.domain.order.dto.OrderRequest;
import io.github.jihu1130.ticketonline.domain.order.dto.OrderResponse;
import io.github.jihu1130.ticketonline.domain.order.service.OrderChangeService;
import io.github.jihu1130.ticketonline.global.exception.CustomException;
import io.github.jihu1130.ticketonline.global.exception.ErrorCode;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 메시지함 알림 (실제 DB).
 * 알림: 구매·취소가 커밋된 뒤 다른 스레드에서 저장되므로 잠깐 기다리며 확인한다.
 */
class MessageNoticeTest extends ConcurrencyTestSupport {

    @Autowired private MessageService messageService;
    @Autowired private OrderChangeService orderChangeService;

    @Test
    void purchaseAndCancel_sendNoticesToBuyer() {
        GoodsFixture goods = createGoods(10, 2);
        Long buyer = createMembers(1).get(0);

        OrderResponse order = purchaseFacade.purchase(buyer, goods.productId(), new OrderRequest(goods.saleItemId(), 2, null));
        List<MessageResponse> afterBuy = waitFor(() -> messageService.getNotices(buyer), 1);
        assertThat(afterBuy.get(0).content()).contains("2개 구매가 확정되었습니다", "주문번호 " + order.orderId());
        assertThat(afterBuy.get(0).productId()).isEqualTo(goods.productId());
        assertThat(afterBuy.get(0).read()).isFalse();

        orderChangeService.cancel(buyer, order.orderId());
        List<MessageResponse> afterCancel = waitFor(() -> messageService.getNotices(buyer), 2);
        assertThat(afterCancel.get(0).content()).contains("주문을 취소했습니다");
        assertThat(messageService.countUnread(buyer).notice()).isEqualTo(2);
    }

    @Test
    void soldOut_notifiesSellerOnce_failedPurchaseSendsNothing() {
        GoodsFixture goods = createGoods(2, 2);
        Long seller = sellerOf(goods.productId());
        List<Long> buyers = createMembers(2);
        purchaseFacade.purchase(buyers.get(0), goods.productId(), new OrderRequest(goods.saleItemId(), 1, null));
        purchaseFacade.purchase(buyers.get(1), goods.productId(), new OrderRequest(goods.saleItemId(), 1, null));
        assertThatThrownBy(() -> purchaseFacade.purchase(buyers.get(0), goods.productId(),
                new OrderRequest(goods.saleItemId(), 1, null)))
                .isInstanceOf(CustomException.class).extracting("errorCode").isEqualTo(ErrorCode.SOLD_OUT);

        List<MessageResponse> sellerNotices = waitFor(() -> messageService.getNotices(seller), 1);
        assertThat(sellerNotices).hasSize(1);
        assertThat(sellerNotices.get(0).content()).contains("매진되었습니다");
        // 실패한 구매(매진)는 알림이 없다: 첫 구매자는 자기 구매 알림 1건만
        sleep(300);
        assertThat(messageService.getNotices(buyers.get(0))).hasSize(1);
    }

    private Long sellerOf(Long productId) {
        return jdbc.queryForObject("select seller_id from product where product_id = ?", Long.class, productId);
    }

    /** 알림은 다른 스레드에서 저장되므로 원하는 개수가 될 때까지 최대 5초 기다린다 */
    private static List<MessageResponse> waitFor(Supplier<List<MessageResponse>> query, int count) {
        long until = System.currentTimeMillis() + 5000;
        List<MessageResponse> result = query.get();
        while (result.size() < count && System.currentTimeMillis() < until) {
            sleep(50);
            result = query.get();
        }
        assertThat(result).hasSize(count);
        return result;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

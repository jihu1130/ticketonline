package io.github.jihu1130.ticketonline.domain.order.service;

import io.github.jihu1130.ticketonline.domain.message.service.NoticeEvent;
import io.github.jihu1130.ticketonline.domain.order.entity.ProductOrder;
import io.github.jihu1130.ticketonline.domain.product.entity.Product;
import io.github.jihu1130.ticketonline.domain.product.entity.SaleItem;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 구매·취소·변경 알림 문구를 만들어 발행한다. 저장은 커밋된 뒤 NoticeListener 가 한다.
 * 판매자에게는 판매될 때마다가 아니라 매진될 때만 알린다 (1000명이 사면 1000건이 쌓이므로).
 */
@Component
@RequiredArgsConstructor
public class OrderNotices {

    private static final DateTimeFormatter EVENT_AT = DateTimeFormatter.ofPattern("M.d (E) HH:mm", Locale.KOREAN);

    private final ApplicationEventPublisher publisher;

    /** 구매 확정 (구매자), 이번 구매로 매진되었으면 판매자에게도 */
    public void purchased(ProductOrder order) {
        notifyBuyer(order, describe(order.getSaleItem()) + " " + amount(order) + " 구매가 확정되었습니다.");
        SaleItem item = order.getSaleItem();
        if (item.getRemainingQuantity() == 0) {
            Product product = item.getProduct();
            String what = item.getPerformance() == null ? "상품" : describe(item);
            publisher.publishEvent(new NoticeEvent(product.getSeller().getId(), product.getId(),
                    "판매 중인 " + what + "이(가) 매진되었습니다."));
        }
    }

    public void canceled(ProductOrder order) {
        notifyBuyer(order, describe(order.getSaleItem()) + " " + amount(order) + " 주문을 취소했습니다.");
    }

    public void quantityReduced(ProductOrder order, int before) {
        notifyBuyer(order, describe(order.getSaleItem()) + " 수량을 " + before + unit(order) + "에서 "
                + amount(order) + "로 줄였습니다.");
    }

    public void addressChanged(ProductOrder order) {
        notifyBuyer(order, "배송지를 " + order.getAddress() + "(으)로 바꿨습니다.");
    }

    public void itemChanged(ProductOrder order, SaleItem before) {
        notifyBuyer(order, describe(before) + "에서 " + describe(order.getSaleItem()) + "(으)로 "
                + amount(order) + " 바꿨습니다.");
    }

    /** 상품명은 메시지함에서 상품 링크로 따로 보여 주므로 문구에는 넣지 않는다 */
    private void notifyBuyer(ProductOrder order, String text) {
        Product product = order.getSaleItem().getProduct();
        publisher.publishEvent(new NoticeEvent(order.getMember().getId(), product.getId(),
                text.trim() + " (주문번호 " + order.getId() + ")"));
    }

    /** 티켓: "11.2 (월) 19:00 VIP" / 굿즈: "" */
    private static String describe(SaleItem item) {
        if (item.getPerformance() == null) {
            return "";
        }
        return item.getPerformance().getEventAt().format(EVENT_AT) + " " + item.getSeatGrade().getName();
    }

    private static String amount(ProductOrder order) {
        return order.getQuantity() + unit(order);
    }

    private static String unit(ProductOrder order) {
        return order.getSaleItem().getPerformance() == null ? "개" : "매";
    }
}

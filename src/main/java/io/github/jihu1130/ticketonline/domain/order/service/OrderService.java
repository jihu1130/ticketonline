package io.github.jihu1130.ticketonline.domain.order.service;

import io.github.jihu1130.ticketonline.domain.order.dto.OrderRequest;
import io.github.jihu1130.ticketonline.domain.order.dto.OrderResponse;
import io.github.jihu1130.ticketonline.domain.order.entity.ProductOrder;
import io.github.jihu1130.ticketonline.domain.order.entity.PurchaseCountId;
import io.github.jihu1130.ticketonline.domain.order.repository.ProductOrderRepository;
import io.github.jihu1130.ticketonline.domain.product.entity.DeliveryType;
import io.github.jihu1130.ticketonline.domain.product.entity.GoodsDetail;
import io.github.jihu1130.ticketonline.domain.product.entity.Performance;
import io.github.jihu1130.ticketonline.domain.product.entity.Product;
import io.github.jihu1130.ticketonline.domain.product.entity.ProductType;
import io.github.jihu1130.ticketonline.domain.product.entity.TicketDetail;
import io.github.jihu1130.ticketonline.domain.product.repository.GoodsDetailRepository;
import io.github.jihu1130.ticketonline.domain.product.repository.PerformanceRepository;
import io.github.jihu1130.ticketonline.domain.product.repository.ProductRepository;
import io.github.jihu1130.ticketonline.domain.product.repository.SaleItemKey;
import io.github.jihu1130.ticketonline.domain.product.repository.SaleItemRepository;
import io.github.jihu1130.ticketonline.domain.product.repository.TicketDetailRepository;
import io.github.jihu1130.ticketonline.global.exception.CustomException;
import io.github.jihu1130.ticketonline.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * F-04 구매 / F-05 내 구매 내역.
 * 결제 기능이 없어 구매하면 바로 구매 확정(SUCCESS)으로 저장한다.
 * 여기서는 구매할 수 있는 상황인지 확인하고, 재고 차감은 PurchaseProcessor(지금은 방식 A)에 맡긴다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final ProductRepository productRepository;
    private final TicketDetailRepository ticketDetailRepository;
    private final GoodsDetailRepository goodsDetailRepository;
    private final PerformanceRepository performanceRepository;
    private final SaleItemRepository saleItemRepository;
    private final ProductOrderRepository productOrderRepository;
    private final PurchaseProcessor purchaseProcessor;
    private final OrderNotices orderNotices;

    @Transactional
    public OrderResponse purchase(Long memberId, Long productId, OrderRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
        // 판매자도 자기 상품을 구매할 수 있다
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(product.getSaleStartAt())) {
            throw new CustomException(ErrorCode.NOT_ON_SALE, "아직 판매가 시작되지 않았습니다.");
        }
        if (!now.isBefore(product.getSaleEndAt())) {
            throw new CustomException(ErrorCode.NOT_ON_SALE, "판매가 종료된 상품입니다.");
        }

        // 재고 엔티티는 아직 읽지 않는다 (잠금 전에 읽으면 옛 판매 수량을 쓰게 됨, SaleItemKey 참고)
        SaleItemKey target = findTarget(product, request.saleItemId());
        boolean ticket = product.getType() == ProductType.TICKET;
        Long scopeId;
        String address = null;
        if (ticket) {
            checkBookingOpen(product, target, now);
            scopeId = target.performanceId();
        } else {
            address = checkAddress(productId, request.address());
            scopeId = PurchaseCountId.WHOLE_PRODUCT;
        }

        PurchaseCommand command = new PurchaseCommand(memberId, productId, target.id(), scopeId,
                product.getMaxPerPerson(), request.quantity(), address, ticket);
        ProductOrder order = purchaseProcessor.process(command);
        orderNotices.purchased(order);
        return OrderResponse.from(order);
    }

    /** 내 구매 내역 (최근 순). 주문마다 취소·변경 기한(티켓은 회차 예매 마감, 굿즈는 판매 종료)을 함께 준다 */
    public List<OrderResponse> getMyOrders(Long memberId) {
        List<ProductOrder> orders = productOrderRepository.findMyOrders(memberId);
        // 티켓의 "공연 몇 시간 전 마감"은 상품마다 한 번만 읽는다
        List<Long> ticketIds = orders.stream()
                .map(o -> o.getSaleItem().getProduct())
                .filter(p -> p.getType() == ProductType.TICKET)
                .map(Product::getId)
                .distinct()
                .toList();
        Map<Long, Integer> closeHours = ticketDetailRepository.findAllById(ticketIds).stream()
                .collect(Collectors.toMap(t -> t.getProduct().getId(), TicketDetail::getCloseBeforeHours));
        return orders.stream()
                .map(order -> OrderResponse.from(order, changeableUntil(order, closeHours)))
                .toList();
    }

    private static LocalDateTime changeableUntil(ProductOrder order, Map<Long, Integer> closeHours) {
        Product product = order.getSaleItem().getProduct();
        Performance performance = order.getSaleItem().getPerformance();
        if (performance == null) {
            return product.getSaleEndAt();
        }
        return Performance.bookingCloseAt(performance.getEventAt(), product.getSaleEndAt(),
                closeHours.getOrDefault(product.getId(), 0));
    }

    /** 구매할 판매 단위. 티켓은 회차 x 등급을 골라야 하고, 굿즈는 생략하면 상품의 판매 단위 1개 */
    private SaleItemKey findTarget(Product product, Long saleItemId) {
        if (saleItemId == null) {
            if (product.getType() == ProductType.TICKET) {
                throw new CustomException(ErrorCode.INVALID_INPUT, "공연 회차와 좌석 등급을 선택해 주세요.");
            }
            return saleItemRepository.findKeysByProductId(product.getId()).stream()
                    .findFirst()
                    .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
        }
        SaleItemKey key = saleItemRepository.findKeyById(saleItemId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INPUT, "구매할 항목을 찾을 수 없습니다."));
        if (!key.productId().equals(product.getId())) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "이 상품의 구매 항목이 아닙니다.");
        }
        return key;
    }

    /** 티켓: 회차마다 "판매 종료"와 "공연 시작 N시간 전" 중 이른 시각에 예매가 마감된다 */
    private void checkBookingOpen(Product product, SaleItemKey target, LocalDateTime now) {
        Performance performance = performanceRepository.findById(target.performanceId())
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
        TicketDetail ticket = ticketDetailRepository.findById(product.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
        LocalDateTime closeAt = Performance.bookingCloseAt(
                performance.getEventAt(), product.getSaleEndAt(), ticket.getCloseBeforeHours());
        if (!now.isBefore(closeAt)) {
            throw new CustomException(ErrorCode.NOT_ON_SALE, "예매가 마감된 회차입니다.");
        }
    }

    /** 굿즈: 택배로 받으면 배송 주소가 필요하다. 현장 수령이면 주소를 저장하지 않는다 */
    private String checkAddress(Long productId, String address) {
        GoodsDetail goods = goodsDetailRepository.findById(productId)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
        if (goods.getDeliveryType() != DeliveryType.PARCEL) {
            return null;
        }
        if (address == null || address.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "배송 주소를 입력해 주세요.");
        }
        return address.trim();
    }
}

package io.github.jihu1130.ticketonline.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.jihu1130.ticketonline.domain.member.entity.Member;
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
import io.github.jihu1130.ticketonline.domain.product.entity.SaleItem;
import io.github.jihu1130.ticketonline.domain.product.entity.TicketDetail;
import io.github.jihu1130.ticketonline.domain.product.repository.GoodsDetailRepository;
import io.github.jihu1130.ticketonline.domain.product.repository.PerformanceRepository;
import io.github.jihu1130.ticketonline.domain.product.repository.ProductRepository;
import io.github.jihu1130.ticketonline.domain.product.repository.SaleItemKey;
import io.github.jihu1130.ticketonline.domain.product.repository.SaleItemRepository;
import io.github.jihu1130.ticketonline.domain.product.repository.TicketDetailRepository;
import io.github.jihu1130.ticketonline.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

/** 구매할 수 있는 상황인지 확인하는 부분 (재고 차감은 PurchaseProcessor를 가짜로 둔다) */
class OrderServiceTest {

    private static final Long SELLER_ID = 1L;
    private static final Long BUYER_ID = 2L;
    private static final Long PRODUCT_ID = 10L;
    private static final Long SALE_ITEM_ID = 100L;
    private static final Long PERFORMANCE_ID = 1000L;

    private final ProductRepository productRepository = Mockito.mock(ProductRepository.class);
    private final TicketDetailRepository ticketDetailRepository = Mockito.mock(TicketDetailRepository.class);
    private final GoodsDetailRepository goodsDetailRepository = Mockito.mock(GoodsDetailRepository.class);
    private final PerformanceRepository performanceRepository = Mockito.mock(PerformanceRepository.class);
    private final SaleItemRepository saleItemRepository = Mockito.mock(SaleItemRepository.class);
    private final ProductOrderRepository productOrderRepository = Mockito.mock(ProductOrderRepository.class);
    private final PurchaseProcessor purchaseProcessor = Mockito.mock(PurchaseProcessor.class);
    private OrderService orderService;

    private final LocalDateTime now = LocalDateTime.now();
    private final Member seller = member(SELLER_ID);

    @BeforeEach
    void setUp() {
        orderService = new OrderService(productRepository, ticketDetailRepository, goodsDetailRepository,
                performanceRepository, saleItemRepository, productOrderRepository, purchaseProcessor);
    }

    // ---------- 굿즈 ----------

    @Test
    void purchaseGoods_usesWholeProductLimitAndTrimmedAddress() {
        Product goods = onSale(ProductType.GOODS);
        stubGoods(goods, DeliveryType.PARCEL);

        OrderResponse response = orderService.purchase(BUYER_ID, PRODUCT_ID, new OrderRequest(null, 2, "  서울시 송파구  "));

        PurchaseCommand command = capturedCommand();
        assertThat(command.saleItemId()).isEqualTo(SALE_ITEM_ID);
        assertThat(command.scopeId()).isEqualTo(PurchaseCountId.WHOLE_PRODUCT);
        assertThat(command.maxPerPerson()).isEqualTo(2);
        assertThat(command.quantity()).isEqualTo(2);
        assertThat(command.address()).isEqualTo("서울시 송파구");
        assertThat(command.ticket()).isFalse();
        assertThat(response.productId()).isEqualTo(PRODUCT_ID);
    }

    @Test
    void purchaseGoods_parcelNeedsAddress() {
        Product goods = onSale(ProductType.GOODS);
        stubGoods(goods, DeliveryType.PARCEL);

        assertInvalid(new OrderRequest(null, 1, " "), ErrorCode.INVALID_INPUT, "배송 주소");
    }

    @Test
    void purchaseGoods_pickupDoesNotKeepAddress() {
        Product goods = onSale(ProductType.GOODS);
        stubGoods(goods, DeliveryType.PICKUP);

        orderService.purchase(BUYER_ID, PRODUCT_ID, new OrderRequest(null, 1, "보낸 주소"));

        assertThat(capturedCommand().address()).isNull();
    }

    // ---------- 티켓 ----------

    @Test
    void purchaseTicket_usesPerformanceAsLimitScope() {
        Product ticket = onSale(ProductType.TICKET);
        stubTicket(ticket, now.plusDays(3));

        orderService.purchase(BUYER_ID, PRODUCT_ID, new OrderRequest(SALE_ITEM_ID, 1, null));

        PurchaseCommand command = capturedCommand();
        assertThat(command.scopeId()).isEqualTo(PERFORMANCE_ID);
        assertThat(command.ticket()).isTrue();
    }

    @Test
    void purchaseTicket_needsSaleItem() {
        onSale(ProductType.TICKET);

        assertInvalid(new OrderRequest(null, 1, null), ErrorCode.INVALID_INPUT, "회차와 좌석 등급");
    }

    @Test
    void purchaseTicket_rejectsSaleItemOfOtherProduct() {
        onSale(ProductType.TICKET);
        given(saleItemRepository.findKeyById(SALE_ITEM_ID))
                .willReturn(Optional.of(new SaleItemKey(SALE_ITEM_ID, 999L, PERFORMANCE_ID)));

        assertInvalid(new OrderRequest(SALE_ITEM_ID, 1, null), ErrorCode.INVALID_INPUT, "이 상품의 구매 항목이 아닙니다");
    }

    @Test
    void purchaseTicket_rejectsClosedPerformance() {
        Product ticket = onSale(ProductType.TICKET);
        // 1시간 전 마감인데 30분 뒤 공연 -> 이미 예매 마감
        stubTicket(ticket, now.plusMinutes(30));

        assertInvalid(new OrderRequest(SALE_ITEM_ID, 1, null), ErrorCode.NOT_ON_SALE, "예매가 마감된 회차");
    }

    // ---------- 공통 ----------

    @Test
    void purchase_rejectsUnknownProduct() {
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.empty());

        assertInvalid(new OrderRequest(null, 1, null), ErrorCode.PRODUCT_NOT_FOUND, "");
    }

    @Test
    void purchase_rejectsOwnProduct() {
        onSale(ProductType.GOODS);

        assertThatThrownBy(() -> orderService.purchase(SELLER_ID, PRODUCT_ID, new OrderRequest(null, 1, "주소")))
                .extracting("errorCode").isEqualTo(ErrorCode.OWN_PRODUCT);
        verify(purchaseProcessor, never()).process(any());
    }

    @Test
    void purchase_rejectsBeforeSaleStart() {
        product(ProductType.GOODS, now.plusMinutes(1), now.plusDays(1));

        assertInvalid(new OrderRequest(null, 1, "주소"), ErrorCode.NOT_ON_SALE, "아직 판매가 시작되지 않았습니다");
    }

    @Test
    void purchase_rejectsAfterSaleEnd() {
        product(ProductType.GOODS, now.minusDays(2), now.minusSeconds(1));

        assertInvalid(new OrderRequest(null, 1, "주소"), ErrorCode.NOT_ON_SALE, "판매가 종료된 상품");
    }

    // ---------- 도우미 ----------

    private void assertInvalid(OrderRequest request, ErrorCode code, String messagePart) {
        assertThatThrownBy(() -> orderService.purchase(BUYER_ID, PRODUCT_ID, request))
                .hasMessageContaining(messagePart)
                .extracting("errorCode").isEqualTo(code);
        verify(purchaseProcessor, never()).process(any());
    }

    private PurchaseCommand capturedCommand() {
        ArgumentCaptor<PurchaseCommand> captor = ArgumentCaptor.forClass(PurchaseCommand.class);
        verify(purchaseProcessor).process(captor.capture());
        return captor.getValue();
    }

    private Product onSale(ProductType type) {
        return product(type, now.minusDays(1), now.plusDays(10));
    }

    private Product product(ProductType type, LocalDateTime saleStart, LocalDateTime saleEnd) {
        Product product = Product.builder().seller(seller).type(type).title("상품").maxPerPerson(2)
                .saleStartAt(saleStart).saleEndAt(saleEnd).build();
        ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
        // 구매 결과 응답을 만들 수 있게 가짜 주문을 돌려준다
        SaleItem item = SaleItem.builder().product(product).price(1000).totalQuantity(10).build();
        given(purchaseProcessor.process(any())).willReturn(
                ProductOrder.builder().saleItem(item).member(member(BUYER_ID)).quantity(1).build());
        return product;
    }

    private void stubGoods(Product goods, DeliveryType deliveryType) {
        given(goodsDetailRepository.findById(PRODUCT_ID)).willReturn(Optional.of(
                GoodsDetail.builder().product(goods).description("설명").deliveryType(deliveryType).build()));
        given(saleItemRepository.findKeysByProductId(PRODUCT_ID))
                .willReturn(List.of(new SaleItemKey(SALE_ITEM_ID, PRODUCT_ID, null)));
    }

    private void stubTicket(Product ticket, LocalDateTime eventAt) {
        given(saleItemRepository.findKeyById(SALE_ITEM_ID))
                .willReturn(Optional.of(new SaleItemKey(SALE_ITEM_ID, PRODUCT_ID, PERFORMANCE_ID)));
        given(performanceRepository.findById(PERFORMANCE_ID)).willReturn(Optional.of(
                Performance.builder().product(ticket).eventAt(eventAt).build()));
        given(ticketDetailRepository.findById(PRODUCT_ID)).willReturn(Optional.of(
                TicketDetail.builder().product(ticket).venue("올림픽홀").closeBeforeHours(1).build()));
    }

    private static Member member(Long id) {
        Member member = Member.builder().email("m" + id + "@test.com").password("pw").name("회원").build();
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}

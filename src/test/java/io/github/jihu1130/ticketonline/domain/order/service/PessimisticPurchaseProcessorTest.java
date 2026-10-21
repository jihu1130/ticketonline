package io.github.jihu1130.ticketonline.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.jihu1130.ticketonline.domain.member.entity.Member;
import io.github.jihu1130.ticketonline.domain.member.repository.MemberRepository;
import io.github.jihu1130.ticketonline.domain.order.entity.OrderStatus;
import io.github.jihu1130.ticketonline.domain.order.entity.ProductOrder;
import io.github.jihu1130.ticketonline.domain.order.entity.PurchaseCount;
import io.github.jihu1130.ticketonline.domain.order.entity.PurchaseCountId;
import io.github.jihu1130.ticketonline.domain.order.repository.ProductOrderRepository;
import io.github.jihu1130.ticketonline.domain.order.repository.PurchaseCountRepository;
import io.github.jihu1130.ticketonline.domain.product.entity.Product;
import io.github.jihu1130.ticketonline.domain.product.entity.ProductType;
import io.github.jihu1130.ticketonline.domain.product.entity.SaleItem;
import io.github.jihu1130.ticketonline.domain.product.repository.SaleItemRepository;
import io.github.jihu1130.ticketonline.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

/** 구매 방식 A: 잠금 순서, 1인 한도, 재고 검사와 차감 */
class PessimisticPurchaseProcessorTest {

    private static final Long BUYER_ID = 2L;
    private static final Long PRODUCT_ID = 10L;
    private static final Long SALE_ITEM_ID = 100L;
    private static final Long SCOPE_ID = 1000L;

    private final PurchaseCountRepository purchaseCountRepository = Mockito.mock(PurchaseCountRepository.class);
    private final SaleItemRepository saleItemRepository = Mockito.mock(SaleItemRepository.class);
    private final ProductOrderRepository productOrderRepository = Mockito.mock(ProductOrderRepository.class);
    private final MemberRepository memberRepository = Mockito.mock(MemberRepository.class);
    private PessimisticPurchaseProcessor processor;

    private final Member buyer = member(BUYER_ID);
    private final Product product = product();
    private PurchaseCount count;
    private SaleItem item;

    @BeforeEach
    void setUp() {
        processor = new PessimisticPurchaseProcessor(purchaseCountRepository, saleItemRepository,
                productOrderRepository, memberRepository);
        count = new PurchaseCount(buyer, product, SCOPE_ID);
        item = SaleItem.builder().product(product).price(150000).totalQuantity(10).build();
        given(purchaseCountRepository.findByIdForUpdate(new PurchaseCountId(BUYER_ID, PRODUCT_ID, SCOPE_ID)))
                .willReturn(Optional.of(count));
        given(saleItemRepository.findByIdForUpdate(SALE_ITEM_ID)).willReturn(Optional.of(item));
        given(memberRepository.getReferenceById(BUYER_ID)).willReturn(buyer);
        given(productOrderRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void process_locksLimitRowThenStockRowAndSaves() {
        ProductOrder order = processor.process(command(2));

        // 잠금 순서: 1인 한도 행 -> 재고 행 (항상 같은 순서라 교착이 생기지 않는다)
        InOrder order1 = inOrder(purchaseCountRepository, saleItemRepository, productOrderRepository);
        order1.verify(purchaseCountRepository).insertIfAbsent(BUYER_ID, PRODUCT_ID, SCOPE_ID);
        order1.verify(purchaseCountRepository).findByIdForUpdate(any());
        order1.verify(saleItemRepository).findByIdForUpdate(SALE_ITEM_ID);
        order1.verify(productOrderRepository).save(any());

        assertThat(item.getSoldQuantity()).isEqualTo(2);
        assertThat(item.getRemainingQuantity()).isEqualTo(8);
        assertThat(count.getBoughtQuantity()).isEqualTo(2);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SUCCESS);
        assertThat(order.getUnitPrice()).isEqualTo(150000);
        assertThat(order.getTotalPrice()).isEqualTo(300000);
    }

    @Test
    void process_limitCountsEarlierPurchases() {
        ReflectionTestUtils.setField(count, "boughtQuantity", 1); // 이미 1매 구매 (한도 2매)

        assertThatThrownBy(() -> processor.process(command(2)))
                .hasMessageContaining("회차당 2매까지 구매할 수 있어 1매만 더 구매할 수 있습니다.")
                .extracting("errorCode").isEqualTo(ErrorCode.LIMIT_EXCEEDED);
        // 한도에서 막히면 재고 행은 잠그지도 않는다
        verify(saleItemRepository, never()).findByIdForUpdate(anyLong());
        assertThat(count.getBoughtQuantity()).isEqualTo(1);
    }

    @Test
    void process_limitReached() {
        ReflectionTestUtils.setField(count, "boughtQuantity", 2);

        assertThatThrownBy(() -> processor.process(command(1)))
                .hasMessageContaining("더 구매할 수 없습니다")
                .extracting("errorCode").isEqualTo(ErrorCode.LIMIT_EXCEEDED);
    }

    @Test
    void process_soldOut() {
        ReflectionTestUtils.setField(item, "soldQuantity", 10);

        assertThatThrownBy(() -> processor.process(command(1)))
                .extracting("errorCode").isEqualTo(ErrorCode.SOLD_OUT);
        verify(productOrderRepository, never()).save(any());
    }

    @Test
    void process_rejectsMoreThanRemainingAsWhole() {
        ReflectionTestUtils.setField(item, "soldQuantity", 9); // 1개 남음

        assertThatThrownBy(() -> processor.process(command(2)))
                .hasMessageContaining("남은 수량은 1개")
                .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_STOCK);
        // 일부만 팔지 않는다
        assertThat(item.getSoldQuantity()).isEqualTo(9);
        assertThat(count.getBoughtQuantity()).isZero();
        verify(productOrderRepository, never()).save(any());
    }

    @Test
    void saleItem_neverSellsMoreThanRemaining() {
        // 검사를 빠뜨려도 엔티티가 한 번 더 막는다
        assertThatThrownBy(() -> item.sell(11)).isInstanceOf(IllegalStateException.class);
        assertThat(item.getSoldQuantity()).isZero();
    }

    private PurchaseCommand command(int quantity) {
        return new PurchaseCommand(BUYER_ID, PRODUCT_ID, SALE_ITEM_ID, SCOPE_ID, 2, quantity, null, true);
    }

    private static Product product() {
        Product product = Product.builder().seller(member(1L)).type(ProductType.TICKET).title("콘서트")
                .maxPerPerson(2).saleStartAt(LocalDateTime.now().minusDays(1))
                .saleEndAt(LocalDateTime.now().plusDays(1)).build();
        ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
        return product;
    }

    private static Member member(Long id) {
        Member member = Member.builder().email("m" + id + "@test.com").password("pw").name("회원").build();
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}

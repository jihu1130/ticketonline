package io.github.jihu1130.ticketonline.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.jihu1130.ticketonline.domain.member.entity.Member;
import io.github.jihu1130.ticketonline.domain.member.repository.MemberRepository;
import io.github.jihu1130.ticketonline.domain.order.entity.ProductOrder;
import io.github.jihu1130.ticketonline.domain.order.entity.PurchaseCount;
import io.github.jihu1130.ticketonline.domain.order.entity.PurchaseCountId;
import io.github.jihu1130.ticketonline.domain.order.repository.ProductOrderRepository;
import io.github.jihu1130.ticketonline.domain.order.repository.PurchaseCountRepository;
import io.github.jihu1130.ticketonline.domain.product.entity.Product;
import io.github.jihu1130.ticketonline.domain.product.entity.ProductType;
import io.github.jihu1130.ticketonline.domain.product.entity.SaleItem;
import io.github.jihu1130.ticketonline.domain.product.repository.ProductRepository;
import io.github.jihu1130.ticketonline.domain.product.repository.SaleItemRepository;
import io.github.jihu1130.ticketonline.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

/** 구매 방식 B: 잠금 없이 읽어 검사·차감 (버전 충돌 확인은 커밋 때 DB에서, 동시성 테스트에서 확인) */
class OptimisticPurchaseProcessorTest {

    private static final Long BUYER_ID = 2L;
    private static final Long PRODUCT_ID = 10L;
    private static final Long SALE_ITEM_ID = 100L;
    private static final Long SCOPE_ID = 1000L;
    private static final PurchaseCountId COUNT_ID = new PurchaseCountId(BUYER_ID, PRODUCT_ID, SCOPE_ID);

    private final PurchaseCountRepository purchaseCountRepository = Mockito.mock(PurchaseCountRepository.class);
    private final SaleItemRepository saleItemRepository = Mockito.mock(SaleItemRepository.class);
    private final ProductOrderRepository productOrderRepository = Mockito.mock(ProductOrderRepository.class);
    private final MemberRepository memberRepository = Mockito.mock(MemberRepository.class);
    private final ProductRepository productRepository = Mockito.mock(ProductRepository.class);
    private OptimisticPurchaseProcessor processor;

    private final Member buyer = member(BUYER_ID);
    private final Product product = product();
    private SaleItem item;

    @BeforeEach
    void setUp() {
        processor = new OptimisticPurchaseProcessor(purchaseCountRepository, saleItemRepository,
                productOrderRepository, memberRepository, productRepository);
        item = SaleItem.builder().product(product).price(30000).totalQuantity(10).build();
        given(saleItemRepository.findById(SALE_ITEM_ID)).willReturn(Optional.of(item));
        given(memberRepository.getReferenceById(BUYER_ID)).willReturn(buyer);
        given(productRepository.getReferenceById(PRODUCT_ID)).willReturn(product);
        given(purchaseCountRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(productOrderRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void tryThreeTimes() {
        assertThat(processor.maxAttempts()).isEqualTo(3);
    }

    @Test
    void firstPurchase_createsLimitRowAndSellsWithoutLocking() {
        given(purchaseCountRepository.findById(COUNT_ID)).willReturn(Optional.empty());

        ProductOrder order = processor.process(command(2));

        ArgumentCaptor<PurchaseCount> created = ArgumentCaptor.forClass(PurchaseCount.class);
        verify(purchaseCountRepository).save(created.capture());
        assertThat(created.getValue().getBoughtQuantity()).isEqualTo(2);
        assertThat(item.getSoldQuantity()).isEqualTo(2);
        assertThat(order.getTotalPrice()).isEqualTo(60000);
        // 잠금 조회는 쓰지 않는다
        verify(saleItemRepository, never()).findByIdForUpdate(anyLong());
        verify(purchaseCountRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void limitCountsEarlierPurchases() {
        PurchaseCount count = new PurchaseCount(buyer, product, SCOPE_ID);
        ReflectionTestUtils.setField(count, "boughtQuantity", 2);
        given(purchaseCountRepository.findById(COUNT_ID)).willReturn(Optional.of(count));

        assertThatThrownBy(() -> processor.process(command(1)))
                .hasMessageContaining("더 구매할 수 없습니다")
                .extracting("errorCode").isEqualTo(ErrorCode.LIMIT_EXCEEDED);
        assertThat(item.getSoldQuantity()).isZero();
    }

    @Test
    void soldOut() {
        given(purchaseCountRepository.findById(COUNT_ID)).willReturn(Optional.empty());
        ReflectionTestUtils.setField(item, "soldQuantity", 10);

        assertThatThrownBy(() -> processor.process(command(1)))
                .extracting("errorCode").isEqualTo(ErrorCode.SOLD_OUT);
        verify(productOrderRepository, never()).save(any());
    }

    @Test
    void moreThanRemaining_rejectedAsWhole() {
        given(purchaseCountRepository.findById(COUNT_ID)).willReturn(Optional.empty());
        ReflectionTestUtils.setField(item, "soldQuantity", 9);

        assertThatThrownBy(() -> processor.process(command(2)))
                .hasMessageContaining("남은 수량은 1개")
                .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_STOCK);
        assertThat(item.getSoldQuantity()).isEqualTo(9);
    }

    private PurchaseCommand command(int quantity) {
        return new PurchaseCommand(BUYER_ID, PRODUCT_ID, SALE_ITEM_ID, SCOPE_ID, 2, quantity, null, false);
    }

    private static Product product() {
        Product product = Product.builder().seller(member(1L)).type(ProductType.GOODS).title("굿즈")
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

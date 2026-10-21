package io.github.jihu1130.ticketonline.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.jihu1130.ticketonline.domain.member.entity.Member;
import io.github.jihu1130.ticketonline.domain.member.repository.MemberRepository;
import io.github.jihu1130.ticketonline.domain.product.dto.ProductCreateRequest;
import io.github.jihu1130.ticketonline.domain.product.dto.ProductResponse;
import io.github.jihu1130.ticketonline.domain.product.dto.ProductUpdateRequest;
import io.github.jihu1130.ticketonline.domain.product.entity.DeliveryType;
import io.github.jihu1130.ticketonline.domain.product.entity.GoodsDetail;
import io.github.jihu1130.ticketonline.domain.product.entity.Product;
import io.github.jihu1130.ticketonline.domain.product.entity.ProductType;
import io.github.jihu1130.ticketonline.domain.product.entity.TicketDetail;
import io.github.jihu1130.ticketonline.domain.product.repository.GoodsDetailRepository;
import io.github.jihu1130.ticketonline.domain.product.repository.ProductRepository;
import io.github.jihu1130.ticketonline.domain.product.repository.TicketDetailRepository;
import io.github.jihu1130.ticketonline.global.exception.CustomException;
import io.github.jihu1130.ticketonline.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class ProductServiceTest {

    private static final Long SELLER_ID = 1L;
    private static final Long OTHER_ID = 2L;
    private static final Long PRODUCT_ID = 10L;

    private final ProductRepository productRepository = Mockito.mock(ProductRepository.class);
    private final TicketDetailRepository ticketDetailRepository = Mockito.mock(TicketDetailRepository.class);
    private final GoodsDetailRepository goodsDetailRepository = Mockito.mock(GoodsDetailRepository.class);
    private final MemberRepository memberRepository = Mockito.mock(MemberRepository.class);
    private ProductService productService;

    private final LocalDateTime start = LocalDateTime.now().plusDays(1);
    private final LocalDateTime end = start.plusDays(1);
    private final LocalDateTime event = end.plusDays(1);
    private final Member seller = member(SELLER_ID);

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, ticketDetailRepository,
                goodsDetailRepository, memberRepository);
        given(memberRepository.getReferenceById(SELLER_ID)).willReturn(seller);
    }

    // ---------- 등록 ----------

    @Test
    void create_ticketSavesProductAndTicketDetail() {
        ProductResponse response = productService.create(SELLER_ID, ticketRequest(100, 2, start, end, event));

        ArgumentCaptor<Product> product = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(product.capture());
        assertThat(product.getValue().getSeller()).isSameAs(seller);
        assertThat(product.getValue().getRemainingQuantity()).isEqualTo(100);
        verify(ticketDetailRepository).save(any(TicketDetail.class));
        verify(goodsDetailRepository, never()).save(any());
        assertThat(response.venue()).isEqualTo("올림픽홀");
        assertThat(response.description()).isNull();
    }

    @Test
    void create_goodsSavesGoodsDetail() {
        ProductResponse response = productService.create(SELLER_ID, goodsRequest("한정 굿즈", DeliveryType.PARCEL));

        verify(goodsDetailRepository).save(any(GoodsDetail.class));
        verify(ticketDetailRepository, never()).save(any());
        assertThat(response.deliveryType()).isEqualTo(DeliveryType.PARCEL);
    }

    @Test
    void create_rejectsLimitBiggerThanQuantity() {
        assertInvalid(ticketRequest(5, 6, start, end, event));
    }

    @Test
    void create_rejectsSaleStartInPast() {
        LocalDateTime past = LocalDateTime.now().minusMinutes(1);
        assertInvalid(ticketRequest(10, 1, past, end, event));
    }

    @Test
    void create_rejectsEndNotAfterStart() {
        assertInvalid(ticketRequest(10, 1, start, start, event));
    }

    @Test
    void create_rejectsEventBeforeSaleEnd() {
        assertInvalid(ticketRequest(10, 1, start, end, end.minusHours(1)));
    }

    @Test
    void create_rejectsTicketWithoutVenue() {
        ProductCreateRequest request = new ProductCreateRequest(ProductType.TICKET, "콘서트", 50000, 10, 1,
                start, end, event, " ", null, null);
        assertInvalid(request);
    }

    @Test
    void create_rejectsGoodsWithoutDeliveryType() {
        assertInvalid(goodsRequest("설명", null));
    }

    // ---------- 수정 / 삭제 ----------

    @Test
    void update_changesProductAndTicketDetail() {
        Product product = savedTicket(start);
        TicketDetail ticket = TicketDetail.builder().product(product).eventAt(event).venue("올림픽홀").build();
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
        given(ticketDetailRepository.findById(PRODUCT_ID)).willReturn(Optional.of(ticket));

        ProductResponse response = productService.update(SELLER_ID, PRODUCT_ID,
                updateRequest(200, 4, "체조경기장"));

        assertThat(response.totalQuantity()).isEqualTo(200);
        assertThat(product.getMaxPerPerson()).isEqualTo(4);
        assertThat(ticket.getVenue()).isEqualTo("체조경기장");
    }

    @Test
    void update_rejectsOtherMember() {
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(savedTicket(start)));

        assertThatThrownBy(() -> productService.update(OTHER_ID, PRODUCT_ID, updateRequest(10, 1, "장소")))
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void update_rejectsAfterSaleStarted() {
        given(productRepository.findById(PRODUCT_ID))
                .willReturn(Optional.of(savedTicket(LocalDateTime.now().minusMinutes(1))));

        assertThatThrownBy(() -> productService.update(SELLER_ID, PRODUCT_ID, updateRequest(10, 1, "장소")))
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_ON_SALE);
    }

    @Test
    void update_rejectsUnknownProduct() {
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.update(SELLER_ID, PRODUCT_ID, updateRequest(10, 1, "장소")))
                .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    void delete_removesDetailAndProduct() {
        Product product = savedTicket(start);
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));

        productService.delete(SELLER_ID, PRODUCT_ID);

        verify(ticketDetailRepository).deleteById(PRODUCT_ID);
        verify(productRepository).delete(product);
    }

    @Test
    void delete_rejectsOtherMember() {
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(savedTicket(start)));

        assertThatThrownBy(() -> productService.delete(OTHER_ID, PRODUCT_ID))
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
        verify(productRepository, never()).delete(any());
    }

    @Test
    void delete_rejectsAfterSaleStarted() {
        given(productRepository.findById(PRODUCT_ID))
                .willReturn(Optional.of(savedTicket(LocalDateTime.now().minusMinutes(1))));

        assertThatThrownBy(() -> productService.delete(SELLER_ID, PRODUCT_ID))
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_ON_SALE);
        verify(productRepository, never()).delete(any());
    }

    // ---------- 도우미 ----------

    private void assertInvalid(ProductCreateRequest request) {
        assertThatThrownBy(() -> productService.create(SELLER_ID, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
        verify(productRepository, never()).save(any());
    }

    private ProductCreateRequest ticketRequest(int quantity, int limit, LocalDateTime saleStart,
                                               LocalDateTime saleEnd, LocalDateTime eventAt) {
        return new ProductCreateRequest(ProductType.TICKET, "콘서트", 50000, quantity, limit,
                saleStart, saleEnd, eventAt, "올림픽홀", null, null);
    }

    private ProductCreateRequest goodsRequest(String description, DeliveryType deliveryType) {
        return new ProductCreateRequest(ProductType.GOODS, "한정 굿즈", 30000, 50, 2,
                start, end, null, null, description, deliveryType);
    }

    private ProductUpdateRequest updateRequest(int quantity, int limit, String venue) {
        return new ProductUpdateRequest("콘서트", 60000, quantity, limit, start, end, event, venue, null, null);
    }

    private Product savedTicket(LocalDateTime saleStart) {
        Product product = Product.builder()
                .seller(seller)
                .type(ProductType.TICKET)
                .title("콘서트")
                .price(50000)
                .totalQuantity(100)
                .maxPerPerson(2)
                .saleStartAt(saleStart)
                .saleEndAt(end)
                .build();
        ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
        return product;
    }

    private static Member member(Long id) {
        Member member = Member.builder().email("seller@test.com").password("pw").name("판매자").build();
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}

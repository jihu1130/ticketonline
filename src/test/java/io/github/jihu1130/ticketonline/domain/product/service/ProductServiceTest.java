package io.github.jihu1130.ticketonline.domain.product.service;

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
import io.github.jihu1130.ticketonline.domain.product.dto.ProductCreateRequest;
import io.github.jihu1130.ticketonline.domain.product.dto.ProductResponse;
import io.github.jihu1130.ticketonline.domain.product.dto.ProductUpdateRequest;
import io.github.jihu1130.ticketonline.domain.product.dto.SeatGradeRequest;
import io.github.jihu1130.ticketonline.domain.product.entity.DeliveryType;
import io.github.jihu1130.ticketonline.domain.product.entity.GoodsDetail;
import io.github.jihu1130.ticketonline.domain.product.entity.Product;
import io.github.jihu1130.ticketonline.domain.product.entity.ProductType;
import io.github.jihu1130.ticketonline.domain.product.entity.SaleItem;
import io.github.jihu1130.ticketonline.domain.product.entity.TicketDetail;
import io.github.jihu1130.ticketonline.domain.product.repository.GoodsDetailRepository;
import io.github.jihu1130.ticketonline.domain.product.repository.PerformanceRepository;
import io.github.jihu1130.ticketonline.domain.product.repository.ProductRepository;
import io.github.jihu1130.ticketonline.domain.product.repository.SaleItemRepository;
import io.github.jihu1130.ticketonline.domain.product.repository.SeatGradeRepository;
import io.github.jihu1130.ticketonline.domain.product.repository.TicketDetailRepository;
import io.github.jihu1130.ticketonline.global.exception.CustomException;
import io.github.jihu1130.ticketonline.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class ProductServiceTest {

    private static final Long SELLER_ID = 1L;
    private static final Long OTHER_ID = 2L;
    private static final Long PRODUCT_ID = 10L;
    private static final String IMAGE = "/uploads/202610/123e4567-e89b-12d3-a456-426614174000.png";

    private final ProductRepository productRepository = Mockito.mock(ProductRepository.class);
    private final TicketDetailRepository ticketDetailRepository = Mockito.mock(TicketDetailRepository.class);
    private final GoodsDetailRepository goodsDetailRepository = Mockito.mock(GoodsDetailRepository.class);
    private final SeatGradeRepository seatGradeRepository = Mockito.mock(SeatGradeRepository.class);
    private final PerformanceRepository performanceRepository = Mockito.mock(PerformanceRepository.class);
    private final SaleItemRepository saleItemRepository = Mockito.mock(SaleItemRepository.class);
    private final MemberRepository memberRepository = Mockito.mock(MemberRepository.class);
    private ProductService productService;

    private final LocalDateTime start = LocalDateTime.now().plusDays(1);
    private final LocalDateTime end = start.plusDays(1);
    private final LocalDateTime day1 = end.plusDays(1);
    private final LocalDateTime day2 = end.plusDays(2);
    private final Member seller = member(SELLER_ID);

    private final List<SeatGradeRequest> twoGrades = List.of(
            new SeatGradeRequest("VIP", 150000, 50),
            new SeatGradeRequest("R석", 99000, 200));

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, ticketDetailRepository, goodsDetailRepository,
                seatGradeRepository, performanceRepository, saleItemRepository, memberRepository);
        given(memberRepository.getReferenceById(SELLER_ID)).willReturn(seller);
    }

    // ---------- 등록: 티켓 ----------

    @Test
    void createTicket_makesSaleItemForEveryPerformanceAndGrade() {
        ProductResponse response = productService.create(SELLER_ID,
                ticket(2, twoGrades, List.of(day2, day1)));

        // 회차 2개 x 등급 2개 = 판매 단위 4개
        assertThat(response.saleItems()).hasSize(4);
        assertThat(response.saleItems()).extracting(ProductResponse.SaleItemResponse::totalQuantity)
                .containsExactly(50, 200, 50, 200);
        assertThat(response.saleItems()).extracting(ProductResponse.SaleItemResponse::price)
                .containsExactly(150000, 99000, 150000, 99000);
        // 회차는 일시 순으로 정렬해 저장
        assertThat(response.performances()).extracting(ProductResponse.PerformanceResponse::eventAt)
                .containsExactly(day1, day2);
        assertThat(response.venue()).isEqualTo("올림픽홀");
        assertThat(response.imageUrl()).isEqualTo(IMAGE);
        verify(ticketDetailRepository).save(any(TicketDetail.class));
        verify(goodsDetailRepository, never()).save(any());
    }

    @Test
    void createTicket_rejectsWithoutGrades() {
        assertInvalid(ticket(2, List.of(), List.of(day1)), "좌석 등급을 1개 이상");
    }

    @Test
    void createTicket_rejectsTooManyGrades() {
        List<SeatGradeRequest> grades = new ArrayList<>();
        for (int i = 0; i <= ProductService.MAX_SEAT_GRADES; i++) {
            grades.add(new SeatGradeRequest("등급" + i, 1000, 10));
        }
        assertInvalid(ticket(2, grades, List.of(day1)), "좌석 등급은");
    }

    @Test
    void createTicket_rejectsDuplicateGradeName() {
        List<SeatGradeRequest> grades = List.of(
                new SeatGradeRequest("VIP", 1000, 10),
                new SeatGradeRequest(" vip ", 2000, 10));
        assertInvalid(ticket(2, grades, List.of(day1)), "중복");
    }

    @Test
    void createTicket_rejectsWithoutPerformances() {
        assertInvalid(ticket(2, twoGrades, List.of()), "공연 회차를 1개 이상");
    }

    @Test
    void createTicket_rejectsDuplicatePerformance() {
        assertInvalid(ticket(2, twoGrades, List.of(day1, day1)), "두 번");
    }

    @Test
    void createTicket_rejectsPerformanceBeforeSaleEnd() {
        assertInvalid(ticket(2, twoGrades, List.of(end.minusHours(1))), "판매 종료 일시 이후");
    }

    @Test
    void createTicket_rejectsLimitBiggerThanSeatsPerPerformance() {
        // 회차당 좌석 250석
        assertInvalid(ticket(251, twoGrades, List.of(day1)), "회차당 전체 좌석 수");
    }

    @Test
    void createTicket_rejectsWithoutVenue() {
        ProductCreateRequest request = new ProductCreateRequest(ProductType.TICKET, "콘서트", null, 2,
                start, end, " ", null, twoGrades, List.of(day1), null, null, null, null);
        assertInvalid(request, "장소");
    }

    // ---------- 등록: 굿즈 ----------

    @Test
    void createGoods_makesOneSaleItem() {
        ProductResponse response = productService.create(SELLER_ID, goods(2, 30000, 100, DeliveryType.PARCEL));

        assertThat(response.saleItems()).hasSize(1);
        assertThat(response.saleItems().get(0).totalQuantity()).isEqualTo(100);
        assertThat(response.saleItems().get(0).performanceId()).isNull();
        assertThat(response.seatGrades()).isEmpty();
        verify(goodsDetailRepository).save(any(GoodsDetail.class));
        verify(seatGradeRepository, never()).saveAll(any());
    }

    @Test
    void createGoods_rejectsLimitBiggerThanQuantity() {
        assertInvalid(goods(6, 30000, 5, DeliveryType.PARCEL), "판매 수량보다");
    }

    @Test
    void createGoods_rejectsWithoutPrice() {
        assertInvalid(goods(1, null, 5, DeliveryType.PARCEL), "가격과 판매 수량");
    }

    @Test
    void createGoods_rejectsWithoutDeliveryType() {
        assertInvalid(goods(1, 30000, 5, null), "수령 방법");
    }

    // ---------- 등록: 공통 ----------

    @Test
    void create_rejectsSaleStartInPast() {
        ProductCreateRequest request = new ProductCreateRequest(ProductType.TICKET, "콘서트", null, 2,
                LocalDateTime.now().minusMinutes(1), end, "올림픽홀", null, twoGrades, List.of(day1),
                null, null, null, null);
        assertInvalid(request, "현재 이후");
    }

    @Test
    void create_rejectsEndNotAfterStart() {
        ProductCreateRequest request = new ProductCreateRequest(ProductType.TICKET, "콘서트", null, 2,
                start, start, "올림픽홀", null, twoGrades, List.of(day1), null, null, null, null);
        assertInvalid(request, "판매 시작 일시 이후");
    }

    @Test
    void create_rejectsExternalImageUrl() {
        ProductCreateRequest request = new ProductCreateRequest(ProductType.TICKET, "콘서트",
                "https://evil.example.com/a.png", 2, start, end, "올림픽홀", null, twoGrades, List.of(day1),
                null, null, null, null);
        assertInvalid(request, "이 사이트에 올린 사진");
    }

    // ---------- 수정 / 삭제 ----------

    @Test
    void update_recreatesStockAfterDeletingOldOne() {
        Product product = savedProduct(ProductType.TICKET, start);
        TicketDetail detail = TicketDetail.builder().product(product).venue("올림픽홀").build();
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
        given(ticketDetailRepository.findById(PRODUCT_ID)).willReturn(Optional.of(detail));

        ProductResponse response = productService.update(SELLER_ID, PRODUCT_ID,
                updateTicket(4, List.of(new SeatGradeRequest("S석", 50000, 300)), List.of(day1, day2)));

        // 재고는 회차·등급을 참조하므로 재고부터 지운다
        InOrder order = inOrder(saleItemRepository, performanceRepository, seatGradeRepository);
        order.verify(saleItemRepository).deleteByProductId(PRODUCT_ID);
        order.verify(performanceRepository).deleteByProductId(PRODUCT_ID);
        order.verify(seatGradeRepository).deleteByProductId(PRODUCT_ID);
        assertThat(response.saleItems()).hasSize(2);
        assertThat(product.getMaxPerPerson()).isEqualTo(4);
        assertThat(detail.getVenue()).isEqualTo("체조경기장");
    }

    @Test
    void update_rejectsOtherMember() {
        given(productRepository.findById(PRODUCT_ID))
                .willReturn(Optional.of(savedProduct(ProductType.TICKET, start)));

        assertThatThrownBy(() -> productService.update(OTHER_ID, PRODUCT_ID,
                updateTicket(1, twoGrades, List.of(day1))))
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
        verify(saleItemRepository, never()).deleteByProductId(anyLong());
    }

    @Test
    void update_rejectsAfterSaleStarted() {
        given(productRepository.findById(PRODUCT_ID))
                .willReturn(Optional.of(savedProduct(ProductType.TICKET, LocalDateTime.now().minusMinutes(1))));

        assertThatThrownBy(() -> productService.update(SELLER_ID, PRODUCT_ID,
                updateTicket(1, twoGrades, List.of(day1))))
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_ON_SALE);
    }

    @Test
    void update_rejectsUnknownProduct() {
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.update(SELLER_ID, PRODUCT_ID,
                updateTicket(1, twoGrades, List.of(day1))))
                .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    void delete_removesStockDetailAndProduct() {
        Product product = savedProduct(ProductType.GOODS, start);
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));

        productService.delete(SELLER_ID, PRODUCT_ID);

        InOrder order = inOrder(saleItemRepository, goodsDetailRepository, productRepository);
        order.verify(saleItemRepository).deleteByProductId(PRODUCT_ID);
        order.verify(goodsDetailRepository).deleteById(PRODUCT_ID);
        order.verify(productRepository).delete(product);
    }

    @Test
    void delete_rejectsOtherMember() {
        given(productRepository.findById(PRODUCT_ID))
                .willReturn(Optional.of(savedProduct(ProductType.TICKET, start)));

        assertThatThrownBy(() -> productService.delete(OTHER_ID, PRODUCT_ID))
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
        verify(productRepository, never()).delete(any());
    }

    @Test
    void delete_rejectsAfterSaleStarted() {
        given(productRepository.findById(PRODUCT_ID))
                .willReturn(Optional.of(savedProduct(ProductType.TICKET, LocalDateTime.now().minusMinutes(1))));

        assertThatThrownBy(() -> productService.delete(SELLER_ID, PRODUCT_ID))
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_ON_SALE);
        verify(productRepository, never()).delete(any());
    }

    // ---------- 내 상품 목록 ----------

    @Test
    void getMyProducts_groupsStockByProduct() {
        Product product = savedProduct(ProductType.GOODS, start);
        SaleItem item = SaleItem.builder().product(product).price(30000).totalQuantity(100).build();
        given(productRepository.findBySellerIdOrderByCreatedAtDesc(SELLER_ID)).willReturn(List.of(product));
        given(saleItemRepository.findByProductIdInOrderByIdAsc(List.of(PRODUCT_ID))).willReturn(List.of(item));

        List<ProductResponse> responses = productService.getMyProducts(SELLER_ID);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).saleItems()).hasSize(1);
        assertThat(responses.get(0).saleItems().get(0).remainingQuantity()).isEqualTo(100);
    }

    @Test
    void getMyProducts_emptyWhenNoProducts() {
        given(productRepository.findBySellerIdOrderByCreatedAtDesc(SELLER_ID)).willReturn(Collections.emptyList());

        assertThat(productService.getMyProducts(SELLER_ID)).isEmpty();
    }

    // ---------- 도우미 ----------

    private void assertInvalid(ProductCreateRequest request, String messagePart) {
        assertThatThrownBy(() -> productService.create(SELLER_ID, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(messagePart)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
        verify(productRepository, never()).save(any());
    }

    private ProductCreateRequest ticket(int limit, List<SeatGradeRequest> grades, List<LocalDateTime> performances) {
        return new ProductCreateRequest(ProductType.TICKET, "콘서트", IMAGE, limit, start, end,
                "올림픽홀", IMAGE, grades, performances, null, null, null, null);
    }

    private ProductCreateRequest goods(int limit, Integer price, int quantity, DeliveryType deliveryType) {
        return new ProductCreateRequest(ProductType.GOODS, "한정 굿즈", IMAGE, limit, start, end,
                null, null, null, null, price, quantity, "1000부 한정", deliveryType);
    }

    private ProductUpdateRequest updateTicket(int limit, List<SeatGradeRequest> grades,
                                              List<LocalDateTime> performances) {
        return new ProductUpdateRequest("콘서트", null, limit, start, end,
                "체조경기장", null, grades, performances, null, null, null, null);
    }

    private Product savedProduct(ProductType type, LocalDateTime saleStart) {
        Product product = Product.builder()
                .seller(seller)
                .type(type)
                .title("상품")
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

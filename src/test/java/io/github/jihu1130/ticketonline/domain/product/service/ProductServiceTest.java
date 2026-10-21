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
import io.github.jihu1130.ticketonline.domain.product.dto.ProductSummaryResponse;
import io.github.jihu1130.ticketonline.domain.product.dto.ProductUpdateRequest;
import io.github.jihu1130.ticketonline.domain.product.dto.SeatGradeRequest;
import io.github.jihu1130.ticketonline.domain.product.entity.DeliveryType;
import io.github.jihu1130.ticketonline.domain.product.entity.GoodsDetail;
import io.github.jihu1130.ticketonline.domain.product.entity.Performance;
import io.github.jihu1130.ticketonline.domain.product.entity.PerformanceStatus;
import io.github.jihu1130.ticketonline.domain.product.entity.Product;
import io.github.jihu1130.ticketonline.domain.product.entity.ProductStatus;
import io.github.jihu1130.ticketonline.domain.product.entity.ProductType;
import io.github.jihu1130.ticketonline.domain.product.entity.SaleItem;
import io.github.jihu1130.ticketonline.domain.product.entity.SeatGrade;
import io.github.jihu1130.ticketonline.domain.product.entity.TicketDetail;
import io.github.jihu1130.ticketonline.domain.product.repository.GoodsDetailRepository;
import io.github.jihu1130.ticketonline.domain.product.repository.PerformanceRepository;
import io.github.jihu1130.ticketonline.domain.product.repository.ProductImageRepository;
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
    private final ProductImageRepository productImageRepository = Mockito.mock(ProductImageRepository.class);
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
                seatGradeRepository, performanceRepository, saleItemRepository, productImageRepository,
                memberRepository);
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
    void createTicket_allowsPerformanceInsideSalePeriod() {
        // 판매 기간(start ~ end) 안에 열리는 1회차와 판매 종료 뒤 2회차를 한 상품으로 등록
        LocalDateTime inside = start.plusHours(5);
        ProductResponse response = productService.create(SELLER_ID, ticketWithClose(2, List.of(inside, day1), 1));

        assertThat(response.performances()).hasSize(2);
        assertThat(response.closeBeforeHours()).isEqualTo(1);
    }

    @Test
    void createTicket_bookingClosesAtEarlierOfSaleEndAndBeforeEvent() {
        LocalDateTime inside = start.plusHours(5);
        ProductResponse response = productService.create(SELLER_ID, ticketWithClose(2, List.of(inside, day1), 3));

        // 1회차: 공연 3시간 전이 판매 종료보다 이르다
        assertThat(response.performances().get(0).bookingCloseAt()).isEqualTo(inside.minusHours(3));
        // 2회차: 판매 종료가 더 이르다
        assertThat(response.performances().get(1).bookingCloseAt()).isEqualTo(end);
    }

    @Test
    void createTicket_rejectsPerformanceWithNoBookingTime() {
        // 2시간 전 마감인데 판매 시작 1시간 뒤 공연 -> 예매할 수 있는 시간이 없다
        assertInvalid(ticketWithClose(2, List.of(start.plusHours(1)), 2), "2시간(예매 마감 시간)");
    }

    @Test
    void createTicket_usesDefaultCloseHoursWhenMissing() {
        ProductResponse response = productService.create(SELLER_ID, ticketWithClose(2, List.of(day1), null));

        assertThat(response.closeBeforeHours()).isEqualTo(ProductService.DEFAULT_CLOSE_BEFORE_HOURS);
    }

    @Test
    void createTicket_rejectsLimitBiggerThanSeatsPerPerformance() {
        // 회차당 좌석 250석
        assertInvalid(ticket(251, twoGrades, List.of(day1)), "회차당 전체 좌석 수");
    }

    @Test
    void createTicket_rejectsWithoutVenue() {
        ProductCreateRequest request = new ProductCreateRequest(ProductType.TICKET, "콘서트", null, 2,
                start, end, " ", null, twoGrades, List.of(day1), 1, null, null, null, null, null);
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
        assertInvalid(goods(1, null, 5, DeliveryType.PARCEL), "가격을 입력해 주세요.");
    }

    @Test
    void createGoods_rejectsWithoutQuantity() {
        // 가격은 입력했고 수량만 빠졌으면 수량을 알려 준다
        assertInvalid(goods(1, 30000, null, DeliveryType.PARCEL), "판매 수량을 입력해 주세요.");
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
                1, null, null, null, null, null);
        assertInvalid(request, "현재 이후");
    }

    @Test
    void create_rejectsEndNotAfterStart() {
        ProductCreateRequest request = new ProductCreateRequest(ProductType.TICKET, "콘서트", null, 2,
                start, start, "올림픽홀", null, twoGrades, List.of(day1), 1, null, null, null, null, null);
        assertInvalid(request, "판매 시작 일시 이후");
    }

    @Test
    void create_rejectsExternalImageUrl() {
        ProductCreateRequest request = new ProductCreateRequest(ProductType.TICKET, "콘서트",
                "https://evil.example.com/a.png", 2, start, end, "올림픽홀", null, twoGrades, List.of(day1),
                1, null, null, null, null, null);
        assertInvalid(request, "이 사이트에 올린 사진");
    }

    // ---------- 부가 사진 / 티켓 설명 ----------

    @Test
    void createGoods_savesExtraImagesInOrder() {
        List<String> urls = uploadedImages(ProductService.MAX_EXTRA_IMAGES);

        ProductResponse response = productService.create(SELLER_ID,
                goodsWithImages(1, 30000, 10, DeliveryType.PARCEL, urls));

        assertThat(response.images()).containsExactlyElementsOf(urls);
        verify(productImageRepository).saveAll(any());
    }

    @Test
    void createGoods_rejectsMoreThanTenImages() {
        assertInvalid(goodsWithImages(1, 30000, 10, DeliveryType.PARCEL,
                uploadedImages(ProductService.MAX_EXTRA_IMAGES + 1)), "10장까지");
    }

    @Test
    void createGoods_rejectsDuplicateImage() {
        assertInvalid(goodsWithImages(1, 30000, 10, DeliveryType.PARCEL,
                List.of(uploaded(1), uploaded(1))), "두 번");
    }

    @Test
    void createGoods_rejectsExternalExtraImage() {
        assertInvalid(goodsWithImages(1, 30000, 10, DeliveryType.PARCEL,
                List.of(uploaded(1), "https://evil.example.com/b.png")), "이 사이트에 올린 사진");
    }

    @Test
    void createTicket_rejectsExtraImages() {
        assertInvalid(ticketWithDescription(null, List.of(uploaded(1))), "굿즈만");
    }

    @Test
    void createTicket_savesDescription() {
        ProductResponse response = productService.create(SELLER_ID,
                ticketWithDescription("  3년 만의 단독 콘서트  ", null));

        assertThat(response.description()).isEqualTo("3년 만의 단독 콘서트");
        assertThat(response.images()).isEmpty();
        verify(productImageRepository, never()).saveAll(any());
    }

    @Test
    void createTicket_descriptionIsOptional() {
        ProductResponse response = productService.create(SELLER_ID, ticketWithDescription(" ", null));

        assertThat(response.description()).isNull();
    }

    @Test
    void updateGoods_replacesExtraImages() {
        Product product = savedProduct(ProductType.GOODS, start);
        GoodsDetail detail = GoodsDetail.builder().product(product).description("설명")
                .deliveryType(DeliveryType.PARCEL).build();
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
        given(goodsDetailRepository.findById(PRODUCT_ID)).willReturn(Optional.of(detail));

        ProductResponse response = productService.update(SELLER_ID, PRODUCT_ID,
                new ProductUpdateRequest("굿즈", null, 1, start, end, null, null, null, null, null,
                        30000, 10, "새 설명", DeliveryType.PICKUP, List.of(uploaded(2), uploaded(1))));

        // 기존 사진을 지운 뒤 새 목록을 보낸 순서대로 저장
        InOrder order = inOrder(productImageRepository);
        order.verify(productImageRepository).deleteByProductId(PRODUCT_ID);
        order.verify(productImageRepository).saveAll(any());
        assertThat(response.images()).containsExactly(uploaded(2), uploaded(1));
        assertThat(response.description()).isEqualTo("새 설명");
    }

    @Test
    void updateTicket_changesDescription() {
        Product product = savedProduct(ProductType.TICKET, start);
        TicketDetail detail = TicketDetail.builder().product(product).venue("올림픽홀").description("옛 소개").build();
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
        given(ticketDetailRepository.findById(PRODUCT_ID)).willReturn(Optional.of(detail));

        productService.update(SELLER_ID, PRODUCT_ID, new ProductUpdateRequest("콘서트", null, 2, start, end,
                "올림픽홀", null, twoGrades, List.of(day1), 1, null, null, "새 소개", null, null));

        assertThat(detail.getDescription()).isEqualTo("새 소개");
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

        InOrder order = inOrder(productImageRepository, saleItemRepository, goodsDetailRepository, productRepository);
        order.verify(productImageRepository).deleteByProductId(PRODUCT_ID);
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

    // ---------- 상품 목록 / 상세 (F-03) ----------

    @Test
    void getProducts_ordersByStatusThenSaleStart() {
        LocalDateTime now = LocalDateTime.now();
        Product ended = product(1L, ProductType.GOODS, now.minusDays(3), now.minusDays(1));
        Product onSaleRecent = product(2L, ProductType.GOODS, now.minusHours(2), now.plusDays(1));
        Product upcomingLater = product(3L, ProductType.GOODS, now.plusDays(2), now.plusDays(3));
        Product soldOut = product(4L, ProductType.GOODS, now.minusHours(1), now.plusDays(1));
        Product onSaleOlder = product(5L, ProductType.GOODS, now.minusHours(5), now.plusDays(1));
        Product upcomingSoon = product(6L, ProductType.GOODS, now.plusDays(1), now.plusDays(3));
        List<Product> products = List.of(ended, onSaleRecent, upcomingLater, soldOut, onSaleOlder, upcomingSoon);
        List<SaleItem> items = new ArrayList<>();
        for (Product p : products) {
            items.add(SaleItem.builder().product(p).price(1000).totalQuantity(10).build());
        }
        ReflectionTestUtils.setField(items.get(3), "soldQuantity", 10); // soldOut 상품은 다 팔림
        given(productRepository.findAll()).willReturn(products);
        given(saleItemRepository.findByProductIdInOrderByIdAsc(any())).willReturn(items);

        List<ProductSummaryResponse> list = productService.getProducts(null);

        // 판매 중(최근 시작 순) -> 판매 예정(곧 열리는 순) -> 매진 -> 판매 종료
        assertThat(list).extracting(ProductSummaryResponse::id).containsExactly(2L, 5L, 6L, 3L, 4L, 1L);
        assertThat(list).extracting(ProductSummaryResponse::status).containsExactly(
                ProductStatus.ON_SALE, ProductStatus.ON_SALE, ProductStatus.UPCOMING, ProductStatus.UPCOMING,
                ProductStatus.SOLD_OUT, ProductStatus.ENDED);
    }

    @Test
    void getProducts_filtersByType() {
        given(productRepository.findByType(ProductType.TICKET)).willReturn(List.of());

        assertThat(productService.getProducts(ProductType.TICKET)).isEmpty();
        verify(productRepository).findByType(ProductType.TICKET);
        verify(productRepository, never()).findAll();
    }

    @Test
    void getProducts_ticketSummaryHasMinPriceAndEventRange() {
        LocalDateTime now = LocalDateTime.now();
        Product ticket = product(PRODUCT_ID, ProductType.TICKET, now.minusDays(1), now.plusDays(10));
        SeatGrade vip = grade(ticket, "VIP", 150000);
        SeatGrade r = grade(ticket, "R석", 99000);
        Performance first = performance(ticket, now.plusDays(3));
        Performance last = performance(ticket, now.plusDays(5));
        stubTicket(ticket, List.of(vip, r), List.of(first, last), List.of(
                item(ticket, first, vip), item(ticket, first, r), item(ticket, last, vip), item(ticket, last, r)));
        given(productRepository.findAll()).willReturn(List.of(ticket));

        ProductSummaryResponse summary = productService.getProducts(null).get(0);

        assertThat(summary.minPrice()).isEqualTo(99000);
        assertThat(summary.firstEventAt()).isEqualTo(first.getEventAt());
        assertThat(summary.lastEventAt()).isEqualTo(last.getEventAt());
        assertThat(summary.venue()).isEqualTo("올림픽홀");
        assertThat(summary.status()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    void getProduct_showsStatusOfEachPerformance() {
        LocalDateTime now = LocalDateTime.now();
        Product ticket = product(PRODUCT_ID, ProductType.TICKET, now.minusDays(1), now.plusDays(10));
        SeatGrade vip = grade(ticket, "VIP", 150000);
        Performance closed = performance(ticket, now.plusMinutes(30)); // 1시간 전 마감 -> 이미 마감
        Performance open = performance(ticket, now.plusHours(3));
        Performance soldOut = performance(ticket, now.plusDays(2));
        SaleItem soldOutItem = item(ticket, soldOut, vip);
        ReflectionTestUtils.setField(soldOutItem, "soldQuantity", soldOutItem.getTotalQuantity());
        stubTicket(ticket, List.of(vip), List.of(closed, open, soldOut),
                List.of(item(ticket, closed, vip), item(ticket, open, vip), soldOutItem));
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(ticket));

        ProductResponse response = productService.getProduct(PRODUCT_ID);

        assertThat(response.performances()).extracting(ProductResponse.PerformanceResponse::status)
                .containsExactly(PerformanceStatus.CLOSED, PerformanceStatus.OPEN, PerformanceStatus.SOLD_OUT);
        assertThat(response.performances().get(0).bookingCloseAt()).isEqualTo(closed.getEventAt().minusHours(1));
        // 예매 중인 회차가 있으므로 판매 중
        assertThat(response.status()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    void getProduct_rejectsUnknownProduct() {
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProduct(PRODUCT_ID))
                .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
    }

    // ---------- 도우미 ----------

    private Product product(Long id, ProductType type, LocalDateTime saleStart, LocalDateTime saleEnd) {
        Product product = Product.builder()
                .seller(seller)
                .type(type)
                .title("상품 " + id)
                .maxPerPerson(1)
                .saleStartAt(saleStart)
                .saleEndAt(saleEnd)
                .build();
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    private static SeatGrade grade(Product product, String name, int price) {
        return SeatGrade.builder().product(product).name(name).price(price).seatCount(10).build();
    }

    private static Performance performance(Product product, LocalDateTime eventAt) {
        return Performance.builder().product(product).eventAt(eventAt).build();
    }

    private static SaleItem item(Product product, Performance performance, SeatGrade grade) {
        return SaleItem.builder().product(product).performance(performance).seatGrade(grade)
                .price(grade.getPrice()).totalQuantity(grade.getSeatCount()).build();
    }

    /** 티켓 상세(올림픽홀, 1시간 전 마감)·등급·회차·재고 조회 결과를 정해 둔다 */
    private void stubTicket(Product ticket, List<SeatGrade> grades, List<Performance> performances,
                            List<SaleItem> items) {
        TicketDetail detail = TicketDetail.builder().product(ticket).venue("올림픽홀").closeBeforeHours(1).build();
        ReflectionTestUtils.setField(detail, "productId", ticket.getId());
        given(ticketDetailRepository.findAllById(any())).willReturn(List.of(detail));
        given(seatGradeRepository.findByProductIdInOrderByIdAsc(any())).willReturn(grades);
        given(performanceRepository.findByProductIdInOrderByEventAtAsc(any())).willReturn(performances);
        given(saleItemRepository.findByProductIdInOrderByIdAsc(any())).willReturn(items);
    }

    private void assertInvalid(ProductCreateRequest request, String messagePart) {
        assertThatThrownBy(() -> productService.create(SELLER_ID, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(messagePart)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
        verify(productRepository, never()).save(any());
    }

    private ProductCreateRequest ticket(int limit, List<SeatGradeRequest> grades, List<LocalDateTime> performances) {
        return new ProductCreateRequest(ProductType.TICKET, "콘서트", IMAGE, limit, start, end,
                "올림픽홀", IMAGE, grades, performances, 1, null, null, null, null, null);
    }

    private ProductCreateRequest ticketWithClose(int limit, List<LocalDateTime> performances, Integer closeHours) {
        return new ProductCreateRequest(ProductType.TICKET, "콘서트", IMAGE, limit, start, end,
                "올림픽홀", IMAGE, twoGrades, performances, closeHours, null, null, null, null, null);
    }

    private ProductCreateRequest goods(int limit, Integer price, Integer quantity, DeliveryType deliveryType) {
        return goodsWithImages(limit, price, quantity, deliveryType, null);
    }

    private ProductCreateRequest goodsWithImages(int limit, Integer price, Integer quantity,
                                                 DeliveryType deliveryType, List<String> images) {
        return new ProductCreateRequest(ProductType.GOODS, "한정 굿즈", IMAGE, limit, start, end,
                null, null, null, null, null, price, quantity, "1000부 한정", deliveryType, images);
    }

    private ProductCreateRequest ticketWithDescription(String description, List<String> images) {
        return new ProductCreateRequest(ProductType.TICKET, "콘서트", IMAGE, 2, start, end,
                "올림픽홀", IMAGE, twoGrades, List.of(day1), 1, null, null, description, null, images);
    }

    /** 이 사이트에 올린 사진 주소 형식 (/uploads/yyyyMM/uuid.png) */
    private static String uploaded(int n) {
        return String.format("/uploads/202610/00000000-0000-0000-0000-%012d.png", n);
    }

    private static List<String> uploadedImages(int count) {
        List<String> urls = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            urls.add(uploaded(i));
        }
        return urls;
    }

    private ProductUpdateRequest updateTicket(int limit, List<SeatGradeRequest> grades,
                                              List<LocalDateTime> performances) {
        return new ProductUpdateRequest("콘서트", null, limit, start, end,
                "체조경기장", null, grades, performances, 1, null, null, null, null, null);
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

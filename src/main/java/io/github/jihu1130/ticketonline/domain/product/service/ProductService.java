package io.github.jihu1130.ticketonline.domain.product.service;

import io.github.jihu1130.ticketonline.domain.member.repository.MemberRepository;
import io.github.jihu1130.ticketonline.domain.product.dto.ProductCreateRequest;
import io.github.jihu1130.ticketonline.domain.product.dto.ProductInput;
import io.github.jihu1130.ticketonline.domain.product.dto.ProductResponse;
import io.github.jihu1130.ticketonline.domain.product.dto.ProductUpdateRequest;
import io.github.jihu1130.ticketonline.domain.product.dto.SeatGradeRequest;
import io.github.jihu1130.ticketonline.domain.product.entity.GoodsDetail;
import io.github.jihu1130.ticketonline.domain.product.entity.Performance;
import io.github.jihu1130.ticketonline.domain.product.entity.Product;
import io.github.jihu1130.ticketonline.domain.product.entity.ProductImage;
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
import io.github.jihu1130.ticketonline.global.upload.ImageService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * F-02 상품 등록 / 내 판매 상품 / 수정 / 삭제
 * 티켓은 등급과 회차를 받아 회차 x 등급 조합마다 판매 단위(SaleItem)를 만든다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    static final int MAX_SEAT_GRADES = 10;
    static final int MAX_PERFORMANCES = 20;
    /** 굿즈 부가 사진 최대 장수 */
    static final int MAX_EXTRA_IMAGES = 10;
    /** 예매 마감 시간을 보내지 않았을 때: 공연 시작 1시간 전 마감 */
    static final int DEFAULT_CLOSE_BEFORE_HOURS = 1;

    private final ProductRepository productRepository;
    private final TicketDetailRepository ticketDetailRepository;
    private final GoodsDetailRepository goodsDetailRepository;
    private final SeatGradeRepository seatGradeRepository;
    private final PerformanceRepository performanceRepository;
    private final SaleItemRepository saleItemRepository;
    private final ProductImageRepository productImageRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public ProductResponse create(Long sellerId, ProductCreateRequest request) {
        validate(request.type(), request);

        Product product = Product.builder()
                .seller(memberRepository.getReferenceById(sellerId))
                .type(request.type())
                .title(request.title().trim())
                .imageUrl(blankToNull(request.imageUrl()))
                .maxPerPerson(request.maxPerPerson())
                .saleStartAt(request.saleStartAt())
                .saleEndAt(request.saleEndAt())
                .build();
        productRepository.save(product);

        if (request.type() == ProductType.TICKET) {
            TicketDetail ticket = TicketDetail.builder()
                    .product(product)
                    .venue(request.venue().trim())
                    .seatMapUrl(blankToNull(request.seatMapUrl()))
                    .closeBeforeHours(closeBeforeHours(request))
                    .description(trimToNull(request.description()))
                    .build();
            ticketDetailRepository.save(ticket);
            return saveTicketStock(product, ticket, request);
        }
        GoodsDetail goods = GoodsDetail.builder()
                .product(product)
                .description(request.description().trim())
                .deliveryType(request.deliveryType())
                .build();
        goodsDetailRepository.save(goods);
        List<ProductImage> images = saveImages(product, request.images());
        return saveGoodsStock(product, goods, request, images);
    }

    /** 내가 등록한 상품 (최근 등록 순) */
    public List<ProductResponse> getMyProducts(Long sellerId) {
        return toResponses(productRepository.findBySellerIdOrderByCreatedAtDesc(sellerId));
    }

    /** 판매 시작 전에, 등록한 본인만 수정할 수 있다. 등급·회차·재고는 새로 만든다. */
    @Transactional
    public ProductResponse update(Long memberId, Long productId, ProductUpdateRequest request) {
        Product product = findEditableProduct(memberId, productId);
        validate(product.getType(), request);

        product.update(request.title().trim(), blankToNull(request.imageUrl()), request.maxPerPerson(),
                request.saleStartAt(), request.saleEndAt());
        deleteStock(productId);

        if (product.getType() == ProductType.TICKET) {
            TicketDetail ticket = ticketDetailRepository.findById(productId)
                    .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
            ticket.update(request.venue().trim(), blankToNull(request.seatMapUrl()), closeBeforeHours(request),
                    trimToNull(request.description()));
            return saveTicketStock(product, ticket, request);
        }
        GoodsDetail goods = goodsDetailRepository.findById(productId)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
        goods.update(request.description().trim(), request.deliveryType());
        List<ProductImage> images = saveImages(product, request.images());
        return saveGoodsStock(product, goods, request, images);
    }

    /** 판매 시작 전에, 등록한 본인만 삭제할 수 있다. 판매 전이라 주문 기록은 없다. */
    @Transactional
    public void delete(Long memberId, Long productId) {
        Product product = findEditableProduct(memberId, productId);
        deleteStock(productId);
        if (product.getType() == ProductType.TICKET) {
            ticketDetailRepository.deleteById(productId);
        } else {
            goodsDetailRepository.deleteById(productId);
        }
        productRepository.delete(product);
    }

    // ---------- 저장 ----------

    /** 등급과 회차를 저장하고, 회차 x 등급마다 재고를 만든다. */
    private ProductResponse saveTicketStock(Product product, TicketDetail ticket, ProductInput input) {
        List<SeatGrade> grades = new ArrayList<>();
        for (SeatGradeRequest g : input.seatGrades()) {
            grades.add(SeatGrade.builder()
                    .product(product)
                    .name(g.name().trim())
                    .price(g.price())
                    .seatCount(g.seatCount())
                    .build());
        }
        seatGradeRepository.saveAll(grades);

        List<Performance> performances = input.performances().stream()
                .sorted()
                .map(eventAt -> Performance.builder().product(product).eventAt(eventAt).build())
                .toList();
        performanceRepository.saveAll(performances);

        List<SaleItem> items = new ArrayList<>();
        for (Performance performance : performances) {
            for (SeatGrade grade : grades) {
                items.add(SaleItem.builder()
                        .product(product)
                        .performance(performance)
                        .seatGrade(grade)
                        .price(grade.getPrice())
                        .totalQuantity(grade.getSeatCount())
                        .build());
            }
        }
        saleItemRepository.saveAll(items);
        return ProductResponse.of(product, ticket, null, grades, performances, items, List.of());
    }

    private ProductResponse saveGoodsStock(Product product, GoodsDetail goods, ProductInput input,
                                           List<ProductImage> images) {
        SaleItem item = SaleItem.builder()
                .product(product)
                .price(input.price())
                .totalQuantity(input.quantity())
                .build();
        saleItemRepository.save(item);
        return ProductResponse.of(product, null, goods, List.of(), List.of(), List.of(item), images);
    }

    /** 부가 사진을 보낸 순서대로 저장한다. */
    private List<ProductImage> saveImages(Product product, List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return List.of();
        }
        List<ProductImage> images = new ArrayList<>();
        for (int i = 0; i < urls.size(); i++) {
            images.add(ProductImage.builder().product(product).url(urls.get(i)).sortOrder(i).build());
        }
        productImageRepository.saveAll(images);
        return images;
    }

    /** 재고가 회차·등급을 참조하므로 재고부터 지운다. 부가 사진도 함께 지운다 (수정 시 다시 저장). */
    private void deleteStock(Long productId) {
        productImageRepository.deleteByProductId(productId);
        saleItemRepository.deleteByProductId(productId);
        performanceRepository.deleteByProductId(productId);
        seatGradeRepository.deleteByProductId(productId);
    }

    // ---------- 조회 ----------

    private List<ProductResponse> toResponses(List<Product> products) {
        if (products.isEmpty()) {
            return List.of();
        }
        List<Long> ids = products.stream().map(Product::getId).toList();
        Map<Long, TicketDetail> tickets = ticketDetailRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(TicketDetail::getProductId, Function.identity()));
        Map<Long, GoodsDetail> goods = goodsDetailRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(GoodsDetail::getProductId, Function.identity()));
        Map<Long, List<SeatGrade>> grades = seatGradeRepository.findByProductIdInOrderByIdAsc(ids).stream()
                .collect(Collectors.groupingBy(g -> g.getProduct().getId()));
        Map<Long, List<Performance>> performances = performanceRepository
                .findByProductIdInOrderByEventAtAsc(ids).stream()
                .collect(Collectors.groupingBy(p -> p.getProduct().getId()));
        Map<Long, List<SaleItem>> items = saleItemRepository.findByProductIdInOrderByIdAsc(ids).stream()
                .collect(Collectors.groupingBy(s -> s.getProduct().getId()));
        Map<Long, List<ProductImage>> images = productImageRepository
                .findByProductIdInOrderBySortOrderAsc(ids).stream()
                .collect(Collectors.groupingBy(i -> i.getProduct().getId()));

        return products.stream()
                .map(p -> ProductResponse.of(p, tickets.get(p.getId()), goods.get(p.getId()),
                        grades.getOrDefault(p.getId(), List.of()),
                        performances.getOrDefault(p.getId(), List.of()),
                        items.getOrDefault(p.getId(), List.of()),
                        images.getOrDefault(p.getId(), List.of())))
                .toList();
    }

    private Product findEditableProduct(Long memberId, Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
        if (!product.isOwnedBy(memberId)) {
            throw new CustomException(ErrorCode.FORBIDDEN, "본인이 등록한 상품만 수정하거나 삭제할 수 있습니다.");
        }
        if (product.isSaleStarted(LocalDateTime.now())) {
            throw new CustomException(ErrorCode.ALREADY_ON_SALE);
        }
        return product;
    }

    // ---------- 검증 (등록·수정 공통, 서버 시간 기준) ----------

    private void validate(ProductType type, ProductInput in) {
        if (!in.saleStartAt().isAfter(LocalDateTime.now())) {
            throw invalid("판매 시작 일시는 현재 이후여야 합니다.");
        }
        if (!in.saleEndAt().isAfter(in.saleStartAt())) {
            throw invalid("판매 종료 일시는 판매 시작 일시 이후여야 합니다.");
        }
        checkImageUrl(in.imageUrl());
        if (type == ProductType.TICKET) {
            validateTicket(in);
        } else {
            validateGoods(in);
        }
        validateExtraImages(type, in.images());
    }

    /** 부가 사진: 굿즈만, 최대 10장, 이 사이트에 올린 사진, 같은 사진 중복 불가 */
    private void validateExtraImages(ProductType type, List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return;
        }
        if (type == ProductType.TICKET) {
            throw invalid("부가 사진은 굿즈만 등록할 수 있습니다.");
        }
        if (urls.size() > MAX_EXTRA_IMAGES) {
            throw invalid("부가 사진은 " + MAX_EXTRA_IMAGES + "장까지 등록할 수 있습니다.");
        }
        Set<String> seen = new HashSet<>();
        for (String url : urls) {
            if (isBlank(url) || !ImageService.isUploadedUrl(url)) {
                throw invalid("사진은 이 사이트에 올린 사진만 사용할 수 있습니다.");
            }
            if (!seen.add(url)) {
                throw invalid("같은 부가 사진이 두 번 들어 있습니다.");
            }
        }
    }

    private void validateTicket(ProductInput in) {
        if (isBlank(in.venue())) {
            throw invalid("티켓은 장소를 입력해 주세요.");
        }
        checkImageUrl(in.seatMapUrl());

        List<SeatGradeRequest> grades = in.seatGrades();
        if (grades == null || grades.isEmpty()) {
            throw invalid("좌석 등급을 1개 이상 입력해 주세요.");
        }
        if (grades.size() > MAX_SEAT_GRADES) {
            throw invalid("좌석 등급은 " + MAX_SEAT_GRADES + "개까지 등록할 수 있습니다.");
        }
        Set<String> names = new HashSet<>();
        int seatsPerPerformance = 0;
        for (SeatGradeRequest g : grades) {
            if (g == null) {
                throw invalid("좌석 등급 정보를 입력해 주세요.");
            }
            if (!names.add(g.name().trim().toLowerCase())) {
                throw invalid("좌석 등급 이름이 중복되었습니다: " + g.name().trim());
            }
            seatsPerPerformance += g.seatCount();
        }

        List<LocalDateTime> performances = in.performances();
        if (performances == null || performances.isEmpty()) {
            throw invalid("공연 회차를 1개 이상 입력해 주세요.");
        }
        if (performances.size() > MAX_PERFORMANCES) {
            throw invalid("공연 회차는 " + MAX_PERFORMANCES + "개까지 등록할 수 있습니다.");
        }
        // 공연은 판매 기간 안에 있어도 된다. 회차마다 "공연 시작 N시간 전"에 예매가 마감되므로
        // 그 마감 시각이 판매 시작보다 뒤여야 예매할 시간이 생긴다.
        int closeHours = closeBeforeHours(in);
        Set<LocalDateTime> seen = new HashSet<>();
        for (LocalDateTime eventAt : performances) {
            if (eventAt == null) {
                throw invalid("공연 일시를 입력해 주세요.");
            }
            if (!seen.add(eventAt)) {
                throw invalid("같은 공연 일시가 두 번 입력되었습니다.");
            }
            if (!eventAt.minusHours(closeHours).isAfter(in.saleStartAt())) {
                throw invalid("공연 일시는 판매 시작 일시보다 " + closeHours
                        + "시간(예매 마감 시간) 이상 뒤여야 합니다.");
            }
        }

        // 한도는 회차당 적용되므로 한 회차의 전체 좌석 수와 비교한다
        if (in.maxPerPerson() > seatsPerPerformance) {
            throw invalid("1인 구매 제한 수량은 회차당 전체 좌석 수보다 클 수 없습니다.");
        }
    }

    private void validateGoods(ProductInput in) {
        if (in.price() == null) {
            throw invalid("가격을 입력해 주세요.");
        }
        if (in.quantity() == null) {
            throw invalid("판매 수량을 입력해 주세요.");
        }
        if (isBlank(in.description())) {
            throw invalid("상품 설명을 입력해 주세요.");
        }
        if (in.deliveryType() == null) {
            throw invalid("수령 방법을 선택해 주세요.");
        }
        if (in.maxPerPerson() > in.quantity()) {
            throw invalid("1인 구매 제한 수량은 판매 수량보다 클 수 없습니다.");
        }
    }

    /** 사진은 이 서버에 올린 것만 허용 (외부 주소 차단) */
    private static void checkImageUrl(String url) {
        if (!isBlank(url) && !ImageService.isUploadedUrl(url)) {
            throw invalid("사진은 이 사이트에 올린 사진만 사용할 수 있습니다.");
        }
    }

    private static int closeBeforeHours(ProductInput in) {
        return in.closeBeforeHours() == null ? DEFAULT_CLOSE_BEFORE_HOURS : in.closeBeforeHours();
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private static String blankToNull(String text) {
        return isBlank(text) ? null : text;
    }

    private static String trimToNull(String text) {
        return isBlank(text) ? null : text.trim();
    }

    private static CustomException invalid(String message) {
        return new CustomException(ErrorCode.INVALID_INPUT, message);
    }
}

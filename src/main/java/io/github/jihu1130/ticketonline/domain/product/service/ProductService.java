package io.github.jihu1130.ticketonline.domain.product.service;

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
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** F-02 상품 등록 / 내 판매 상품 / 수정 / 삭제 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final TicketDetailRepository ticketDetailRepository;
    private final GoodsDetailRepository goodsDetailRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public ProductResponse create(Long sellerId, ProductCreateRequest request) {
        validate(request.type(), request.totalQuantity(), request.maxPerPerson(),
                request.saleStartAt(), request.saleEndAt(),
                request.eventAt(), request.venue(), request.description(), request.deliveryType());

        Product product = Product.builder()
                .seller(memberRepository.getReferenceById(sellerId))
                .type(request.type())
                .title(request.title().trim())
                .price(request.price())
                .totalQuantity(request.totalQuantity())
                .maxPerPerson(request.maxPerPerson())
                .saleStartAt(request.saleStartAt())
                .saleEndAt(request.saleEndAt())
                .build();
        productRepository.save(product);

        TicketDetail ticket = null;
        GoodsDetail goods = null;
        if (request.type() == ProductType.TICKET) {
            ticket = TicketDetail.builder()
                    .product(product)
                    .eventAt(request.eventAt())
                    .venue(request.venue().trim())
                    .build();
            ticketDetailRepository.save(ticket);
        } else {
            goods = GoodsDetail.builder()
                    .product(product)
                    .description(request.description().trim())
                    .deliveryType(request.deliveryType())
                    .build();
            goodsDetailRepository.save(goods);
        }
        return ProductResponse.of(product, ticket, goods);
    }

    /** 내가 등록한 상품 (최근 등록 순) */
    public List<ProductResponse> getMyProducts(Long sellerId) {
        List<Product> products = productRepository.findBySellerIdOrderByCreatedAtDesc(sellerId);
        List<Long> ids = products.stream().map(Product::getId).toList();
        Map<Long, TicketDetail> tickets = ticketDetailRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(TicketDetail::getProductId, Function.identity()));
        Map<Long, GoodsDetail> goods = goodsDetailRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(GoodsDetail::getProductId, Function.identity()));
        return products.stream()
                .map(p -> ProductResponse.of(p, tickets.get(p.getId()), goods.get(p.getId())))
                .toList();
    }

    /** 판매 시작 전에, 등록한 본인만 수정할 수 있다. */
    @Transactional
    public ProductResponse update(Long memberId, Long productId, ProductUpdateRequest request) {
        Product product = findEditableProduct(memberId, productId);
        validate(product.getType(), request.totalQuantity(), request.maxPerPerson(),
                request.saleStartAt(), request.saleEndAt(),
                request.eventAt(), request.venue(), request.description(), request.deliveryType());

        product.update(request.title().trim(), request.price(), request.totalQuantity(),
                request.maxPerPerson(), request.saleStartAt(), request.saleEndAt());

        TicketDetail ticket = null;
        GoodsDetail goods = null;
        if (product.getType() == ProductType.TICKET) {
            ticket = ticketDetailRepository.findById(productId)
                    .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
            ticket.update(request.eventAt(), request.venue().trim());
        } else {
            goods = goodsDetailRepository.findById(productId)
                    .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
            goods.update(request.description().trim(), request.deliveryType());
        }
        return ProductResponse.of(product, ticket, goods);
    }

    /** 판매 시작 전에, 등록한 본인만 삭제할 수 있다. 판매 전이라 주문 기록은 없다. */
    @Transactional
    public void delete(Long memberId, Long productId) {
        Product product = findEditableProduct(memberId, productId);
        if (product.getType() == ProductType.TICKET) {
            ticketDetailRepository.deleteById(productId);
        } else {
            goodsDetailRepository.deleteById(productId);
        }
        productRepository.delete(product);
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

    /** 등록과 수정에 같은 규칙을 쓴다. 시간은 서버 시간 기준. */
    private void validate(ProductType type, int totalQuantity, int maxPerPerson,
                          LocalDateTime saleStartAt, LocalDateTime saleEndAt,
                          LocalDateTime eventAt, String venue,
                          String description, DeliveryType deliveryType) {
        if (maxPerPerson > totalQuantity) {
            throw invalid("1인 구매 제한 수량은 판매 수량보다 클 수 없습니다.");
        }
        if (!saleStartAt.isAfter(LocalDateTime.now())) {
            throw invalid("판매 시작 일시는 현재 이후여야 합니다.");
        }
        if (!saleEndAt.isAfter(saleStartAt)) {
            throw invalid("판매 종료 일시는 판매 시작 일시 이후여야 합니다.");
        }
        if (type == ProductType.TICKET) {
            if (eventAt == null || isBlank(venue)) {
                throw invalid("티켓은 공연 일시와 장소를 입력해 주세요.");
            }
            if (!eventAt.isAfter(saleEndAt)) {
                throw invalid("공연 일시는 판매 종료 일시 이후여야 합니다.");
            }
        } else {
            if (isBlank(description) || deliveryType == null) {
                throw invalid("굿즈는 상품 설명과 수령 방법을 입력해 주세요.");
            }
        }
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private static CustomException invalid(String message) {
        return new CustomException(ErrorCode.INVALID_INPUT, message);
    }
}

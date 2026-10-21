package io.github.jihu1130.ticketonline.domain.product.controller;

import io.github.jihu1130.ticketonline.domain.product.dto.ProductCreateRequest;
import io.github.jihu1130.ticketonline.domain.product.dto.ProductResponse;
import io.github.jihu1130.ticketonline.domain.product.dto.ProductUpdateRequest;
import io.github.jihu1130.ticketonline.domain.product.service.ProductService;
import io.github.jihu1130.ticketonline.global.security.LoginMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "상품", description = "F-02 상품 등록 / 수정 / 삭제")
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "상품 등록",
            description = "TICKET이면 eventAt·venue, GOODS면 description·deliveryType을 함께 보낸다")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@AuthenticationPrincipal LoginMember loginMember,
                                  @Valid @RequestBody ProductCreateRequest request) {
        return productService.create(loginMember.id(), request);
    }

    @Operation(summary = "상품 수정", description = "판매 시작 전, 등록한 본인만 가능. 종류는 바꿀 수 없다")
    @PutMapping("/{productId}")
    public ProductResponse update(@AuthenticationPrincipal LoginMember loginMember,
                                  @PathVariable Long productId,
                                  @Valid @RequestBody ProductUpdateRequest request) {
        return productService.update(loginMember.id(), productId, request);
    }

    @Operation(summary = "상품 삭제", description = "판매 시작 전, 등록한 본인만 가능")
    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal LoginMember loginMember, @PathVariable Long productId) {
        productService.delete(loginMember.id(), productId);
    }
}

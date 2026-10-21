package io.github.jihu1130.ticketonline.domain.product.controller;

import io.github.jihu1130.ticketonline.domain.product.dto.ProductResponse;
import io.github.jihu1130.ticketonline.domain.product.service.ProductService;
import io.github.jihu1130.ticketonline.global.security.LoginMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "판매 관리", description = "F-02 내가 등록한 상품")
@RestController
@RequestMapping("/api/sellers/me")
@RequiredArgsConstructor
public class SellerProductController {

    private final ProductService productService;

    @Operation(summary = "내 판매 상품 목록", description = "최근 등록 순")
    @GetMapping("/products")
    public List<ProductResponse> myProducts(@AuthenticationPrincipal LoginMember loginMember) {
        return productService.getMyProducts(loginMember.id());
    }
}

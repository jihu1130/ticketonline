package io.github.jihu1130.ticketonline.domain.order.controller;

import io.github.jihu1130.ticketonline.domain.order.dto.OrderRequest;
import io.github.jihu1130.ticketonline.domain.order.dto.OrderResponse;
import io.github.jihu1130.ticketonline.domain.order.service.OrderService;
import io.github.jihu1130.ticketonline.global.security.LoginMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "구매", description = "F-04 구매 (결제 없이 바로 확정) / F-05 내 구매 내역")
@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "구매",
            description = "결제 없이 바로 구매 확정. 티켓은 saleItemId(회차 x 등급) 필수, 택배 굿즈는 address 필수")
    @PostMapping("/api/products/{productId}/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse purchase(@AuthenticationPrincipal LoginMember loginMember,
                                  @PathVariable Long productId,
                                  @Valid @RequestBody OrderRequest request) {
        return orderService.purchase(loginMember.id(), productId, request);
    }

    @Operation(summary = "내 구매 내역", description = "최근 구매 순")
    @GetMapping("/api/members/me/orders")
    public List<OrderResponse> myOrders(@AuthenticationPrincipal LoginMember loginMember) {
        return orderService.getMyOrders(loginMember.id());
    }
}

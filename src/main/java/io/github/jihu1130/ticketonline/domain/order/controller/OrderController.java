package io.github.jihu1130.ticketonline.domain.order.controller;

import io.github.jihu1130.ticketonline.domain.order.dto.OrderChangeRequest;
import io.github.jihu1130.ticketonline.domain.order.dto.OrderRequest;
import io.github.jihu1130.ticketonline.domain.order.dto.OrderResponse;
import io.github.jihu1130.ticketonline.domain.order.service.OrderChangeService;
import io.github.jihu1130.ticketonline.domain.order.service.OrderService;
import io.github.jihu1130.ticketonline.domain.order.service.PurchaseFacade;
import io.github.jihu1130.ticketonline.global.security.LoginMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "구매", description = "F-04 구매 (결제 없이 바로 확정) / F-05 내 구매 내역, 취소·변경")
@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final PurchaseFacade purchaseFacade;
    private final OrderChangeService orderChangeService;

    @Operation(summary = "구매",
            description = "결제 없이 바로 구매 확정. 티켓은 saleItemId(회차 x 등급) 필수, 택배 굿즈는 address 필수")
    @PostMapping("/api/products/{productId}/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse purchase(@AuthenticationPrincipal LoginMember loginMember,
                                  @PathVariable Long productId,
                                  @Valid @RequestBody OrderRequest request) {
        return purchaseFacade.purchase(loginMember.id(), productId, request);
    }

    @Operation(summary = "내 구매 내역", description = "최근 구매 순")
    @GetMapping("/api/members/me/orders")
    public List<OrderResponse> myOrders(@AuthenticationPrincipal LoginMember loginMember) {
        return orderService.getMyOrders(loginMember.id());
    }

    @Operation(summary = "주문 취소", description = "재고와 1인 한도를 되돌린다. 티켓은 회차 예매 마감 전, 굿즈는 판매 종료 전까지")
    @PostMapping("/api/members/me/orders/{orderId}/cancel")
    public OrderResponse cancel(@AuthenticationPrincipal LoginMember loginMember, @PathVariable Long orderId) {
        return orderChangeService.cancel(loginMember.id(), orderId);
    }

    @Operation(summary = "수량 줄이기 (부분 취소)", description = "지금보다 적은 수량으로. 줄인 만큼 재고와 1인 한도를 되돌린다")
    @PatchMapping("/api/members/me/orders/{orderId}/quantity")
    public OrderResponse reduceQuantity(@AuthenticationPrincipal LoginMember loginMember, @PathVariable Long orderId,
                                        @Valid @RequestBody OrderChangeRequest.Quantity request) {
        return orderChangeService.reduceQuantity(loginMember.id(), orderId, request.quantity());
    }

    @Operation(summary = "배송지 변경", description = "택배로 받는 굿즈만")
    @PatchMapping("/api/members/me/orders/{orderId}/address")
    public OrderResponse changeAddress(@AuthenticationPrincipal LoginMember loginMember, @PathVariable Long orderId,
                                       @Valid @RequestBody OrderChangeRequest.Address request) {
        return orderChangeService.changeAddress(loginMember.id(), orderId, request.address());
    }

    @Operation(summary = "회차·좌석 등급 바꾸기",
            description = "티켓만, 같은 수량으로. 바꿀 쪽이 매진이거나 회차 한도를 넘으면 원래 주문은 그대로")
    @PatchMapping("/api/members/me/orders/{orderId}/item")
    public OrderResponse changeItem(@AuthenticationPrincipal LoginMember loginMember, @PathVariable Long orderId,
                                    @Valid @RequestBody OrderChangeRequest.Item request) {
        return orderChangeService.changeItem(loginMember.id(), orderId, request.saleItemId());
    }
}

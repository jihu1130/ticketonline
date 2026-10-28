package io.github.jihu1130.ticketonline.domain.order.service;

import io.github.jihu1130.ticketonline.domain.member.repository.MemberRepository;
import io.github.jihu1130.ticketonline.domain.order.entity.ProductOrder;
import io.github.jihu1130.ticketonline.domain.order.entity.PurchaseCount;
import io.github.jihu1130.ticketonline.domain.order.entity.PurchaseCountId;
import io.github.jihu1130.ticketonline.domain.order.repository.ProductOrderRepository;
import io.github.jihu1130.ticketonline.domain.order.repository.PurchaseCountRepository;
import io.github.jihu1130.ticketonline.domain.product.entity.SaleItem;
import io.github.jihu1130.ticketonline.domain.product.repository.SaleItemRepository;
import io.github.jihu1130.ticketonline.global.exception.CustomException;
import io.github.jihu1130.ticketonline.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구매 방식 A: 비관적 락.
 * 1인 한도 행 -> 재고 행 순서로 SELECT ... FOR UPDATE 로 잠근 뒤 검사하고 차감한다.
 * 같은 재고를 사려는 요청은 앞 요청이 커밋할 때까지 기다리므로 초과 판매가 생기지 않는다.
 * 잠그는 순서를 항상 같게 해 두 요청이 서로를 기다리는 교착(데드락)을 막는다.
 */
@Component
@ConditionalOnProperty(name = "app.purchase.strategy", havingValue = "pessimistic")
@RequiredArgsConstructor
public class PessimisticPurchaseProcessor implements PurchaseProcessor {

    private final PurchaseCountRepository purchaseCountRepository;
    private final SaleItemRepository saleItemRepository;
    private final ProductOrderRepository productOrderRepository;
    private final MemberRepository memberRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ProductOrder process(PurchaseCommand command) {
        // 1) 1인 한도 행 잠금 (처음 사는 사람은 행을 만든 뒤 잠금)
        purchaseCountRepository.insertIfAbsent(command.memberId(), command.productId(), command.scopeId());
        PurchaseCountId countId = new PurchaseCountId(command.memberId(), command.productId(), command.scopeId());
        PurchaseCount count = purchaseCountRepository.findByIdForUpdate(countId)
                .orElseThrow(() -> new IllegalStateException("구매 수량 기록을 찾을 수 없습니다: " + countId));

        int allowance = count.remainingAllowance(command.maxPerPerson());
        if (command.quantity() > allowance) {
            throw new CustomException(ErrorCode.LIMIT_EXCEEDED, command.limitMessage(allowance));
        }

        // 2) 재고 행 잠금
        SaleItem item = saleItemRepository.findByIdForUpdate(command.saleItemId())
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
        int remaining = item.getRemainingQuantity();
        if (remaining == 0) {
            throw new CustomException(ErrorCode.SOLD_OUT);
        }
        if (command.quantity() > remaining) {
            throw new CustomException(ErrorCode.INSUFFICIENT_STOCK,
                    "남은 수량은 " + remaining + "개입니다. 남은 수량보다 많이 구매할 수 없습니다.");
        }

        // 3) 차감과 주문 저장 (커밋할 때 잠금이 풀린다)
        item.sell(command.quantity());
        count.add(command.quantity());
        ProductOrder order = ProductOrder.builder()
                .saleItem(item)
                .member(memberRepository.getReferenceById(command.memberId()))
                .quantity(command.quantity())
                .address(command.address())
                .build();
        return productOrderRepository.save(order);
    }
}

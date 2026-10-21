package io.github.jihu1130.ticketonline.domain.order.service;

import io.github.jihu1130.ticketonline.domain.member.repository.MemberRepository;
import io.github.jihu1130.ticketonline.domain.order.entity.ProductOrder;
import io.github.jihu1130.ticketonline.domain.order.entity.PurchaseCount;
import io.github.jihu1130.ticketonline.domain.order.entity.PurchaseCountId;
import io.github.jihu1130.ticketonline.domain.order.repository.ProductOrderRepository;
import io.github.jihu1130.ticketonline.domain.order.repository.PurchaseCountRepository;
import io.github.jihu1130.ticketonline.domain.product.entity.SaleItem;
import io.github.jihu1130.ticketonline.domain.product.repository.ProductRepository;
import io.github.jihu1130.ticketonline.domain.product.repository.SaleItemRepository;
import io.github.jihu1130.ticketonline.global.exception.CustomException;
import io.github.jihu1130.ticketonline.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구매 방식 B: 낙관적 락.
 * 재고 행과 1인 한도 행을 잠그지 않고 읽어 검사·차감한 뒤, 저장할 때 @Version 으로 충돌을 확인한다.
 * 예) 버전 5를 읽고 저장 -> UPDATE ... SET sold_quantity = 31, version = 6 WHERE ... AND version = 5
 *     그사이 다른 요청이 먼저 바꿨으면 바뀐 행이 0개 -> 충돌(ObjectOptimisticLockingFailureException)
 * 같은 사람이 동시에 처음 사서 한도 행이 겹치면 기본 키 중복(DataIntegrityViolationException)이 난다.
 * 두 경우 모두 PurchaseFacade가 새 트랜잭션으로 처음부터 다시 시도한다 (최대 3번).
 * 버전이 맞을 때만 저장되므로 초과 판매는 생기지 않는다.
 */
@Component
@ConditionalOnProperty(name = "app.purchase.strategy", havingValue = "optimistic")
@RequiredArgsConstructor
public class OptimisticPurchaseProcessor implements PurchaseProcessor {

    static final int MAX_ATTEMPTS = 3;

    private final PurchaseCountRepository purchaseCountRepository;
    private final SaleItemRepository saleItemRepository;
    private final ProductOrderRepository productOrderRepository;
    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;

    @Override
    public int maxAttempts() {
        return MAX_ATTEMPTS;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ProductOrder process(PurchaseCommand command) {
        // 1) 1인 한도 행 (잠그지 않음, 없으면 새로 만든다)
        PurchaseCountId countId = new PurchaseCountId(command.memberId(), command.productId(), command.scopeId());
        PurchaseCount count = purchaseCountRepository.findById(countId)
                .orElseGet(() -> purchaseCountRepository.save(new PurchaseCount(
                        memberRepository.getReferenceById(command.memberId()),
                        productRepository.getReferenceById(command.productId()),
                        command.scopeId())));

        int allowance = count.remainingAllowance(command.maxPerPerson());
        if (command.quantity() > allowance) {
            throw new CustomException(ErrorCode.LIMIT_EXCEEDED, command.limitMessage(allowance));
        }

        // 2) 재고 행 (잠그지 않음, 버전과 함께 읽는다)
        SaleItem item = saleItemRepository.findById(command.saleItemId())
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
        int remaining = item.getRemainingQuantity();
        if (remaining == 0) {
            throw new CustomException(ErrorCode.SOLD_OUT);
        }
        if (command.quantity() > remaining) {
            throw new CustomException(ErrorCode.INSUFFICIENT_STOCK,
                    "남은 수량은 " + remaining + "개입니다. 남은 수량보다 많이 구매할 수 없습니다.");
        }

        // 3) 차감하고, 버전 확인(UPDATE ... WHERE version = ?)을 주문 저장보다 먼저 DB에 보낸다.
        //    JPA는 저장할 때 INSERT를 UPDATE보다 먼저 보내는데, 주문 INSERT는 외래 키 확인 때문에
        //    재고 행에 읽기 잠금을 건다. 두 요청이 읽기 잠금을 잡은 채 서로의 쓰기 잠금을 기다리면 교착(데드락)이 난다.
        //    재고 UPDATE를 먼저 보내면 재고 행에 바로 쓰기 잠금이 걸려 이 교착이 생기지 않는다.
        //    충돌이면 여기서 ObjectOptimisticLockingFailureException 이 나고 PurchaseFacade가 다시 시도한다.
        item.sell(command.quantity());
        count.add(command.quantity());
        saleItemRepository.flush();

        ProductOrder order = ProductOrder.builder()
                .saleItem(item)
                .member(memberRepository.getReferenceById(command.memberId()))
                .quantity(command.quantity())
                .address(command.address())
                .build();
        return productOrderRepository.save(order);
    }
}

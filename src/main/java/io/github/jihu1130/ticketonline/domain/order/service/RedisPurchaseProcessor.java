package io.github.jihu1130.ticketonline.domain.order.service;

import io.github.jihu1130.ticketonline.domain.member.repository.MemberRepository;
import io.github.jihu1130.ticketonline.domain.order.entity.ProductOrder;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 구매 방식 C: Redis.
 * 1) Redis Lua 스크립트로 1인 한도와 남은 수량을 확인하고 줄인다.
 *    보통은 PurchaseFacade 가 DB 연결을 잡기 전에 이미 해 두었다 (RedisStock.reserveAhead).
 *    매진·한도 초과 요청은 거기서 끝나 DB 를 건드리지 않는다. 1000명이 재고 100개를 사면 900명이 거기서 거절된다.
 * 2) 통과한 요청만 DB 에 기록한다. 순서는 방식 A 와 같이 1인 한도 행 -> 재고 행 (주문 취소·변경과 교착이 생기지 않게).
 *    재고는 "총 수량을 넘지 않을 때만" 늘리는 조건부 UPDATE 라서, Redis 와 DB 가 잠깐 어긋나도 초과 판매는 없다.
 * 3) DB 기록이 실패해 트랜잭션이 되돌려지면 Redis 에서 줄인 것도 되돌린다.
 */
@Component
@ConditionalOnProperty(name = "app.purchase.strategy", havingValue = "redis")
@RequiredArgsConstructor
public class RedisPurchaseProcessor implements PurchaseProcessor {

    private final RedisStock redisStock;
    private final PurchaseCountRepository purchaseCountRepository;
    private final SaleItemRepository saleItemRepository;
    private final ProductOrderRepository productOrderRepository;
    private final MemberRepository memberRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ProductOrder process(PurchaseCommand command) {
        // 1) Redis: 통과하지 못하면 여기서 CustomException (Redis 는 바뀌지 않음)
        //    PurchaseFacade 가 DB 전에 이미 차감했으면 건너뛴다 (그때 되돌리기는 PurchaseFacade 가 맡는다)
        if (!command.reservedInRedis()) {
            redisStock.reserve(command);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        redisStock.release(command);
                    }
                }
            });
        }

        // 2) DB: 1인 한도 행 (Redis 가 이미 확인했지만, 어긋났을 때를 대비해 DB 값으로 한 번 더)
        purchaseCountRepository.addBought(command.memberId(), command.productId(), command.scopeId(), command.quantity());
        int bought = purchaseCountRepository.findBoughtQuantity(command.memberId(), command.productId(), command.scopeId())
                .orElse(command.quantity());
        if (bought > command.maxPerPerson()) {
            redisStock.forgetBought(command);
            throw new CustomException(ErrorCode.LIMIT_EXCEEDED,
                    command.limitMessage(Math.max(0, command.maxPerPerson() - (bought - command.quantity()))));
        }

        // 3) DB: 재고 (총 수량을 넘으면 0행)
        if (saleItemRepository.sellIfAvailable(command.saleItemId(), command.quantity()) == 0) {
            redisStock.forgetStock(command.saleItemId());
            throw new CustomException(ErrorCode.SOLD_OUT);
        }

        // 4) 주문 저장. 재고 엔티티는 UPDATE 뒤에 읽어 바뀐 판매 수량을 본다
        SaleItem item = saleItemRepository.findById(command.saleItemId())
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
        ProductOrder order = ProductOrder.builder()
                .saleItem(item)
                .member(memberRepository.getReferenceById(command.memberId()))
                .quantity(command.quantity())
                .address(command.address())
                .build();
        return productOrderRepository.save(order);
    }
}

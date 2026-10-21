package io.github.jihu1130.ticketonline.domain.order.service;

import io.github.jihu1130.ticketonline.domain.order.dto.OrderRequest;
import io.github.jihu1130.ticketonline.domain.order.dto.OrderResponse;
import io.github.jihu1130.ticketonline.global.exception.CustomException;
import io.github.jihu1130.ticketonline.global.exception.ErrorCode;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * 구매 입구. 충돌하면 처음부터 다시 시도한다 (방식 B만, 방식 A는 1번).
 *
 * 다시 시도는 트랜잭션 바깥에서 해야 한다. 충돌은 커밋할 때 드러나고, 그 트랜잭션은 이미 끝났기 때문이다.
 * 그래서 여기는 @Transactional 을 붙이지 않고, 매 시도마다 OrderService.purchase 가 새 트랜잭션을 연다.
 * 다 실패하면 TOO_MANY_REQUESTS("요청이 많아 처리하지 못했습니다. 다시 시도해 주세요.")
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseFacade {

    private final OrderService orderService;
    private final PurchaseProcessor purchaseProcessor;

    public OrderResponse purchase(Long memberId, Long productId, OrderRequest request) {
        int maxAttempts = purchaseProcessor.maxAttempts();
        for (int attempt = 1; ; attempt++) {
            try {
                return orderService.purchase(memberId, productId, request);
            } catch (OptimisticLockingFailureException | DataIntegrityViolationException e) {
                if (maxAttempts == 1) {
                    throw e; // 다시 시도하지 않는 방식(A)에서는 원래 오류 그대로
                }
                if (attempt >= maxAttempts) {
                    log.debug("구매 {}번 시도 모두 충돌: member={}, product={}", attempt, memberId, productId);
                    throw new CustomException(ErrorCode.TOO_MANY_REQUESTS);
                }
                pause(attempt);
            }
        }
    }

    /** 같은 요청끼리 또 부딪히지 않게 시도마다 조금씩 더 길게, 무작위로 쉰다 (10~30ms, 20~60ms ...) */
    private void pause(int attempt) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(10, 31) * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CustomException(ErrorCode.TOO_MANY_REQUESTS);
        }
    }
}

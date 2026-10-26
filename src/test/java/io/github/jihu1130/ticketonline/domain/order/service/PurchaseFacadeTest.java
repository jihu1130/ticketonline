package io.github.jihu1130.ticketonline.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.github.jihu1130.ticketonline.domain.order.dto.OrderRequest;
import io.github.jihu1130.ticketonline.domain.order.dto.OrderResponse;
import io.github.jihu1130.ticketonline.domain.queue.service.QueueService;
import io.github.jihu1130.ticketonline.global.exception.CustomException;
import io.github.jihu1130.ticketonline.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/** 충돌하면 다시 시도: 방식 B는 최대 3번, 방식 A는 다시 시도하지 않음. 대기열 입장 확인이 먼저 */
class PurchaseFacadeTest {

    private final OrderService orderService = Mockito.mock(OrderService.class);
    private final PurchaseProcessor optimistic = processorWithAttempts(3);
    private final PurchaseProcessor pessimistic = processorWithAttempts(1);
    private final QueueService queueService = Mockito.mock(QueueService.class);
    private final OrderRequest request = new OrderRequest(1L, 1, null);
    private final OrderResponse ok = new OrderResponse(1L, 10L, null, "상품", null, null, null,
            1, 1000, 1000, null, null, null, 1L, null, null);

    @Test
    void optimistic_retriesAfterConflictAndSucceeds() {
        given(orderService.purchase(anyLong(), anyLong(), any()))
                .willThrow(conflict())
                .willThrow(new DataIntegrityViolationException("같은 사람의 첫 구매가 겹침"))
                .willReturn(ok);

        OrderResponse response = new PurchaseFacade(orderService, optimistic, queueService).purchase(2L, 10L, request);

        assertThat(response).isSameAs(ok);
        verify(orderService, times(3)).purchase(2L, 10L, request);
    }

    @Test
    void optimistic_threeConflicts_tooManyRequests() {
        given(orderService.purchase(anyLong(), anyLong(), any())).willThrow(conflict());

        assertThatThrownBy(() -> new PurchaseFacade(orderService, optimistic, queueService).purchase(2L, 10L, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
        verify(orderService, times(3)).purchase(2L, 10L, request);
    }

    @Test
    void businessErrorsAreNotRetried() {
        given(orderService.purchase(anyLong(), anyLong(), any()))
                .willThrow(new CustomException(ErrorCode.SOLD_OUT));

        assertThatThrownBy(() -> new PurchaseFacade(orderService, optimistic, queueService).purchase(2L, 10L, request))
                .extracting("errorCode").isEqualTo(ErrorCode.SOLD_OUT);
        verify(orderService, times(1)).purchase(2L, 10L, request);
    }

    @Test
    void pessimistic_doesNotRetry() {
        given(orderService.purchase(anyLong(), anyLong(), any())).willThrow(conflict());

        assertThatThrownBy(() -> new PurchaseFacade(orderService, pessimistic, queueService).purchase(2L, 10L, request))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        verify(orderService, times(1)).purchase(2L, 10L, request);
    }

    @Test
    void notAdmitted_doesNotPurchase() {
        willThrow(new CustomException(ErrorCode.NOT_ADMITTED)).given(queueService).checkAdmitted(2L, 10L);

        assertThatThrownBy(() -> new PurchaseFacade(orderService, optimistic, queueService).purchase(2L, 10L, request))
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_ADMITTED);
        verify(orderService, never()).purchase(anyLong(), anyLong(), any());
    }

    private static ObjectOptimisticLockingFailureException conflict() {
        return new ObjectOptimisticLockingFailureException("SaleItem", 1L);
    }

    private static PurchaseProcessor processorWithAttempts(int attempts) {
        return new PurchaseProcessor() {
            @Override
            public io.github.jihu1130.ticketonline.domain.order.entity.ProductOrder process(PurchaseCommand command) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int maxAttempts() {
                return attempts;
            }
        };
    }
}

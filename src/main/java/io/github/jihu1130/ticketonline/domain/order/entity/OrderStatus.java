package io.github.jihu1130.ticketonline.domain.order.entity;

/**
 * 주문 상태.
 * 지금은 결제 기능이 없어 구매하면 바로 SUCCESS(구매 확정)로 저장한다.
 * 결제를 붙이면 "PENDING(결제 대기)으로 저장 -> 결제 완료 시 SUCCESS" 단계를 추가한다.
 */
public enum OrderStatus {
    /** 결제 대기 (결제 기능을 붙일 때 사용, 지금은 쓰지 않음) */
    PENDING,
    /** 구매 확정 */
    SUCCESS,
    /** 취소 */
    CANCELED
}

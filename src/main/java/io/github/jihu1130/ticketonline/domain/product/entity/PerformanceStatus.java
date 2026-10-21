package io.github.jihu1130.ticketonline.domain.product.entity;

import java.time.LocalDateTime;

/**
 * 티켓 회차의 예매 상태. DB에 저장하지 않고 조회할 때 서버 시간으로 계산한다.
 * 예) 판매 10/22 22:00 ~ 11/22 22:00, 1시간 전 마감일 때 11/3에 보면
 *     11/2 19:00 회차는 CLOSED(예매 마감), 11/25 회차는 OPEN(예매 중)
 */
public enum PerformanceStatus {

    /** 예매 전: 상품 판매 시작 전 */
    BEFORE,
    /** 예매 중 */
    OPEN,
    /** 매진: 이 회차의 모든 등급 좌석이 팔림 */
    SOLD_OUT,
    /** 예매 마감: 이 회차의 예매 마감 시각(Performance.bookingCloseAt)이 지남 */
    CLOSED;

    /**
     * @param remaining 이 회차의 등급별 남은 좌석 합계
     */
    public static PerformanceStatus of(LocalDateTime saleStartAt, LocalDateTime bookingCloseAt,
                                       int remaining, LocalDateTime now) {
        if (now.isBefore(saleStartAt)) {
            return BEFORE;
        }
        if (!now.isBefore(bookingCloseAt)) {
            return CLOSED;
        }
        return remaining > 0 ? OPEN : SOLD_OUT;
    }
}

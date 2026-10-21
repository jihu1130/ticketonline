package io.github.jihu1130.ticketonline.domain.product.entity;

import java.time.LocalDateTime;
import java.util.Collection;

/** 상품의 판매 상태. DB에 저장하지 않고 조회할 때 서버 시간으로 계산한다. */
public enum ProductStatus {

    /** 판매 예정: 판매 시작 전 */
    UPCOMING,
    /** 판매 중 */
    ON_SALE,
    /** 매진: 지금 살 수 있는 재고가 모두 팔림 */
    SOLD_OUT,
    /** 판매 종료: 판매 종료 시각이 지났거나, 티켓의 모든 회차가 예매 마감됨 */
    ENDED;

    /**
     * 굿즈: 재고가 하나뿐이라 남은 수량으로 정한다.
     * @param remaining 남은 수량
     */
    public static ProductStatus ofGoods(LocalDateTime saleStartAt, LocalDateTime saleEndAt,
                                        int remaining, LocalDateTime now) {
        if (now.isBefore(saleStartAt)) {
            return UPCOMING;
        }
        if (!now.isBefore(saleEndAt)) {
            return ENDED;
        }
        return remaining > 0 ? ON_SALE : SOLD_OUT;
    }

    /**
     * 티켓: 회차별 상태로 정한다.
     * - 예매 중인 회차가 하나라도 있으면 판매 중
     * - 모든 회차가 예매 마감이면 판매 종료
     * - 그 밖(마감되지 않은 회차가 모두 매진)이면 매진
     */
    public static ProductStatus ofTicket(LocalDateTime saleStartAt, LocalDateTime saleEndAt,
                                         Collection<PerformanceStatus> performances, LocalDateTime now) {
        if (now.isBefore(saleStartAt)) {
            return UPCOMING;
        }
        if (!now.isBefore(saleEndAt)) {
            return ENDED;
        }
        if (performances.contains(PerformanceStatus.OPEN)) {
            return ON_SALE;
        }
        if (performances.stream().allMatch(s -> s == PerformanceStatus.CLOSED)) {
            return ENDED;
        }
        return SOLD_OUT;
    }
}

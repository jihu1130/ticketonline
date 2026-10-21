package io.github.jihu1130.ticketonline.domain.product.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 상품·회차 판매 상태 계산 (ProductStatus, PerformanceStatus) */
class SaleStatusTest {

    // 판매 10/22 22:00 ~ 11/22 22:00
    private final LocalDateTime saleStart = LocalDateTime.of(2026, 10, 22, 22, 0);
    private final LocalDateTime saleEnd = LocalDateTime.of(2026, 11, 22, 22, 0);
    private final LocalDateTime duringSale = LocalDateTime.of(2026, 11, 3, 12, 0);

    // ---------- 회차 상태 ----------

    @Test
    void performance_beforeSaleStart() {
        assertThat(PerformanceStatus.of(saleStart, saleEnd, 10, saleStart.minusMinutes(1)))
                .isEqualTo(PerformanceStatus.BEFORE);
    }

    @Test
    void performance_openWhenSeatsLeft() {
        assertThat(PerformanceStatus.of(saleStart, saleEnd, 10, duringSale))
                .isEqualTo(PerformanceStatus.OPEN);
    }

    @Test
    void performance_soldOutWhenNoSeatsLeft() {
        assertThat(PerformanceStatus.of(saleStart, saleEnd, 0, duringSale))
                .isEqualTo(PerformanceStatus.SOLD_OUT);
    }

    @Test
    void performance_closedFromBookingCloseTime() {
        LocalDateTime closeAt = duringSale;
        // 마감 시각이 되면 좌석이 남아 있어도 예매 마감
        assertThat(PerformanceStatus.of(saleStart, closeAt, 10, closeAt)).isEqualTo(PerformanceStatus.CLOSED);
        assertThat(PerformanceStatus.of(saleStart, closeAt, 10, closeAt.minusSeconds(1)))
                .isEqualTo(PerformanceStatus.OPEN);
    }

    @Test
    void performance_userExample() {
        // 1시간 전 마감, 11/3에 보면 11/2 공연은 예매 마감, 11/25 공연은 예매 중
        LocalDateTime nov2Close = Performance.bookingCloseAt(LocalDateTime.of(2026, 11, 2, 19, 0), saleEnd, 1);
        LocalDateTime nov25Close = Performance.bookingCloseAt(LocalDateTime.of(2026, 11, 25, 19, 0), saleEnd, 1);

        assertThat(PerformanceStatus.of(saleStart, nov2Close, 10, duringSale)).isEqualTo(PerformanceStatus.CLOSED);
        assertThat(PerformanceStatus.of(saleStart, nov25Close, 10, duringSale)).isEqualTo(PerformanceStatus.OPEN);
    }

    // ---------- 굿즈 상태 ----------

    @Test
    void goods_upcomingOnSaleSoldOutEnded() {
        assertThat(ProductStatus.ofGoods(saleStart, saleEnd, 10, saleStart.minusMinutes(1)))
                .isEqualTo(ProductStatus.UPCOMING);
        assertThat(ProductStatus.ofGoods(saleStart, saleEnd, 10, saleStart)).isEqualTo(ProductStatus.ON_SALE);
        assertThat(ProductStatus.ofGoods(saleStart, saleEnd, 0, duringSale)).isEqualTo(ProductStatus.SOLD_OUT);
        assertThat(ProductStatus.ofGoods(saleStart, saleEnd, 10, saleEnd)).isEqualTo(ProductStatus.ENDED);
    }

    // ---------- 티켓 상태 (회차 상태로 정함) ----------

    @Test
    void ticket_upcomingBeforeSaleStart() {
        assertThat(ProductStatus.ofTicket(saleStart, saleEnd, List.of(PerformanceStatus.BEFORE),
                saleStart.minusMinutes(1))).isEqualTo(ProductStatus.UPCOMING);
    }

    @Test
    void ticket_onSaleWhenAnyPerformanceOpen() {
        assertThat(ProductStatus.ofTicket(saleStart, saleEnd,
                List.of(PerformanceStatus.CLOSED, PerformanceStatus.SOLD_OUT, PerformanceStatus.OPEN), duringSale))
                .isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    void ticket_soldOutWhenRemainingPerformancesSoldOut() {
        assertThat(ProductStatus.ofTicket(saleStart, saleEnd,
                List.of(PerformanceStatus.CLOSED, PerformanceStatus.SOLD_OUT), duringSale))
                .isEqualTo(ProductStatus.SOLD_OUT);
    }

    @Test
    void ticket_endedWhenAllPerformancesClosed() {
        assertThat(ProductStatus.ofTicket(saleStart, saleEnd,
                List.of(PerformanceStatus.CLOSED, PerformanceStatus.CLOSED), duringSale))
                .isEqualTo(ProductStatus.ENDED);
    }

    @Test
    void ticket_endedAfterSaleEnd() {
        assertThat(ProductStatus.ofTicket(saleStart, saleEnd, List.of(PerformanceStatus.OPEN), saleEnd))
                .isEqualTo(ProductStatus.ENDED);
    }
}

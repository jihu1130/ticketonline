package io.github.jihu1130.ticketonline.domain.order.service;

/**
 * 검사를 마친 구매 요청. PurchaseProcessor가 재고를 줄이고 주문을 만든다.
 *
 * @param scopeId      1인 한도를 세는 범위 (티켓: 회차 번호, 굿즈: PurchaseCountId.WHOLE_PRODUCT)
 * @param maxPerPerson 1인 구매 제한 수량
 * @param address      배송 주소 (택배 굿즈만, 아니면 null)
 * @param ticket       티켓이면 true (안내 문구를 "회차당 n매"로 쓴다)
 */
public record PurchaseCommand(
        Long memberId,
        Long productId,
        Long saleItemId,
        Long scopeId,
        int maxPerPerson,
        int quantity,
        String address,
        boolean ticket,
        // 방식 C: PurchaseFacade 가 DB 를 쓰기 전에 Redis 에서 이미 차감했으면 true (처리기는 다시 차감하지 않음)
        boolean reservedInRedis
) {

    public PurchaseCommand(Long memberId, Long productId, Long saleItemId, Long scopeId, int maxPerPerson,
                           int quantity, String address, boolean ticket) {
        this(memberId, productId, saleItemId, scopeId, maxPerPerson, quantity, address, ticket, false);
    }

    /** 한도 초과 안내 문구. 예) "회차당 2매까지 구매할 수 있어 1매만 더 구매할 수 있습니다." */
    public String limitMessage(int allowance) {
        String unit = ticket ? "매" : "개";
        String limit = (ticket ? "회차당 " : "1인당 ") + maxPerPerson + unit + "까지 구매할 수 있어 ";
        return allowance == 0
                ? limit + "더 구매할 수 없습니다."
                : limit + allowance + unit + "만 더 구매할 수 있습니다.";
    }
}

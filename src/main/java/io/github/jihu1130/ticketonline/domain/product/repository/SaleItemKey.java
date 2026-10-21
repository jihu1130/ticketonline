package io.github.jihu1130.ticketonline.domain.product.repository;

/**
 * 판매 단위의 번호만 담은 조회 결과 (엔티티가 아니다).
 * 구매 전에 재고(SaleItem) 엔티티를 잠금 없이 읽어 두면, 같은 트랜잭션에서 나중에
 * FOR UPDATE로 잠가도 JPA가 처음 읽은 옛 판매 수량을 그대로 써서 초과 판매가 날 수 있다.
 * 그래서 잠그기 전에는 이 번호만 읽는다.
 *
 * @param performanceId 티켓 회차 번호 (굿즈는 null)
 */
public record SaleItemKey(Long id, Long productId, Long performanceId) {
}

package io.github.jihu1130.ticketonline.concurrency;

import org.springframework.test.context.TestPropertySource;

/**
 * 구매 방식 B(낙관적 락)일 때도 취소·변경이 맞는지 OrderChangeTest 를 그대로 다시 실행한다.
 * 취소·변경은 잠금으로, 구매는 버전 비교로 처리하므로 둘이 섞여도 판매 수량이 맞아야 한다.
 */
@TestPropertySource(properties = "app.purchase.strategy=optimistic")
class OptimisticOrderChangeTest extends OrderChangeTest {
}

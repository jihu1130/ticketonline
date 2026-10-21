package io.github.jihu1130.ticketonline;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 앱이 뜨는지 확인. 개발 DB 대신 테스트 DB(ticketonline_test)를 쓴다. */
@SpringBootTest
@ActiveProfiles("test")
class TicketonlineApplicationTests {

	@Test
	void contextLoads() {
	}

}

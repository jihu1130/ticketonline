package io.github.jihu1130.ticketonline.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** @Async 켜기 (구매가 커밋된 뒤 알림을 다른 스레드에서 저장) */
@Configuration
@EnableAsync
public class AsyncConfig {
}

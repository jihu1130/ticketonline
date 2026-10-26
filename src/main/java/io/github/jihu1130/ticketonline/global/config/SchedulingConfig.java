package io.github.jihu1130.ticketonline.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** @Scheduled 작업 켜기 (대기열 1초마다 입장 처리) */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}

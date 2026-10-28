package io.github.jihu1130.ticketonline.domain.admin.dto;

import java.time.LocalDateTime;
import tools.jackson.databind.JsonNode;

/**
 * k6 부하 테스트(load-test/run.py) 최근 결과.
 * medians = 방식(pessimistic/optimistic/redis)별 중앙값, runs = 회차별 전체 결과. 파일 내용을 그대로 넘긴다.
 * 결과 파일이 없으면 exists=false 이고 measuredAt·medians·runs 는 null.
 * scaleMedians = 동시 인원별 중앙값 {"1000": {방식: 중앙값}, ...} (run.py --users). 없으면 scale 값들은 null.
 * stressStages = 한계 측정 단계별 결과 [{target_rps, actual_rps, p95_ms, ...}] (run.py stress). 없으면 stress 값들은 null.
 */
public record LoadTestResultResponse(boolean exists, LocalDateTime measuredAt, JsonNode medians, JsonNode runs,
                                     LocalDateTime scaleMeasuredAt, JsonNode scaleMedians,
                                     LocalDateTime stressMeasuredAt, JsonNode stressStages) {
}

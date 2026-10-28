package io.github.jihu1130.ticketonline.domain.admin.dto;

import java.time.LocalDateTime;
import tools.jackson.databind.JsonNode;

/**
 * k6 부하 테스트(load-test/run.py) 최근 결과.
 * medians = 방식(pessimistic/optimistic/redis)별 중앙값, runs = 회차별 전체 결과. 파일 내용을 그대로 넘긴다.
 * 결과 파일이 없으면 exists=false 이고 나머지는 null.
 */
public record LoadTestResultResponse(boolean exists, LocalDateTime measuredAt, JsonNode medians, JsonNode runs) {

    public static LoadTestResultResponse empty() {
        return new LoadTestResultResponse(false, null, null, null);
    }
}

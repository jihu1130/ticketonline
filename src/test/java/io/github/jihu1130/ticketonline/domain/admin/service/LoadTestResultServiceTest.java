package io.github.jihu1130.ticketonline.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.jihu1130.ticketonline.domain.admin.dto.LoadTestResultResponse;
import io.github.jihu1130.ticketonline.global.exception.CustomException;
import io.github.jihu1130.ticketonline.global.exception.ErrorCode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class LoadTestResultServiceTest {

    private static final String MEDIANS = """
            {"pessimistic": {"tps": 866.6, "p95_ms": 1115.7, "error_rate": 0.0, "oversell": 0},
             "redis": {"tps": 900.1, "p95_ms": 1082.1, "error_rate": 0.0, "oversell": 0}}
            """;
    private static final String RUNS = """
            [{"strategy": "pessimistic", "run": 1, "tps": 632.1, "sold_matches_orders": true},
             {"strategy": "redis", "run": 1, "tps": 900.1, "sold_matches_orders": true}]
            """;

    @TempDir
    Path tempDir;

    private LoadTestResultService service;

    @BeforeEach
    void setUp() {
        service = new LoadTestResultService(tempDir.toString(), JsonMapper.builder().build());
    }

    @Test
    void getLatest_withoutFiles_returnsEmpty() {
        LoadTestResultResponse result = service.getLatest();

        assertThat(result.exists()).isFalse();
        assertThat(result.medians()).isNull();
        assertThat(result.runs()).isNull();
        assertThat(result.scaleMedians()).isNull();
        assertThat(result.scaleMeasuredAt()).isNull();
    }

    @Test
    void getLatest_readsScaleMediansSeparately() throws Exception {
        // 동시 인원별 결과만 있어도 읽는다 (기본 결과는 없음)
        Path scaleDir = Files.createDirectories(tempDir.resolve(LoadTestResultService.SCALE_DIR));
        Files.writeString(scaleDir.resolve(LoadTestResultService.MEDIANS_FILE), """
                {"1000": {"redis": {"tps": 2958.6, "p95_ms": 213.4}},
                 "5000": {"redis": {"tps": 3100.0, "p95_ms": 900.0}, "pessimistic": {"tps": 1500.0}}}
                """);

        LoadTestResultResponse result = service.getLatest();

        assertThat(result.exists()).isFalse();
        assertThat(result.scaleMeasuredAt()).isNotNull();
        assertThat(result.scaleMedians().get("5000").get("redis").get("p95_ms").asDouble()).isEqualTo(900.0);
        assertThat(result.scaleMedians().get("5000").get("pessimistic").get("tps").asDouble()).isEqualTo(1500.0);
    }

    @Test
    void getLatest_readsStressStages() throws Exception {
        Path stressDir = Files.createDirectories(tempDir.resolve(LoadTestResultService.STRESS_DIR));
        Files.writeString(stressDir.resolve(LoadTestResultService.RUNS_FILE), """
                [{"target_rps": 1000, "actual_rps": 998.2, "p95_ms": 45.1},
                 {"target_rps": 2000, "actual_rps": 1500.0, "p95_ms": 12000.0, "stopped": "p95 12000.0ms"}]
                """);

        LoadTestResultResponse result = service.getLatest();

        assertThat(result.exists()).isFalse();
        assertThat(result.scaleMedians()).isNull();
        assertThat(result.stressMeasuredAt()).isNotNull();
        assertThat(result.stressStages().size()).isEqualTo(2);
        assertThat(result.stressStages().get(1).get("stopped").asString()).isEqualTo("p95 12000.0ms");
    }

    @Test
    void getLatest_withoutStressFile_stressIsNull() {
        LoadTestResultResponse result = service.getLatest();

        assertThat(result.stressStages()).isNull();
        assertThat(result.stressMeasuredAt()).isNull();
    }

    @Test
    void getLatest_brokenScaleFile_isInternalError() throws Exception {
        Path scaleDir = Files.createDirectories(tempDir.resolve(LoadTestResultService.SCALE_DIR));
        Files.writeString(scaleDir.resolve(LoadTestResultService.MEDIANS_FILE), "{not json");

        assertThatThrownBy(() -> service.getLatest())
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.INTERNAL_ERROR);
    }

    @Test
    void getLatest_withOnlyOneFile_returnsEmpty() throws Exception {
        Files.writeString(tempDir.resolve(LoadTestResultService.MEDIANS_FILE), MEDIANS);

        assertThat(service.getLatest().exists()).isFalse();
    }

    @Test
    void getLatest_readsMediansAndRuns() throws Exception {
        Files.writeString(tempDir.resolve(LoadTestResultService.MEDIANS_FILE), MEDIANS);
        Path runs = Files.writeString(tempDir.resolve(LoadTestResultService.RUNS_FILE), RUNS);
        LocalDateTime written = LocalDateTime.of(2026, 10, 26, 21, 40);
        Files.setLastModifiedTime(runs, FileTime.from(written.atZone(ZoneId.systemDefault()).toInstant()));

        LoadTestResultResponse result = service.getLatest();

        assertThat(result.exists()).isTrue();
        assertThat(result.measuredAt()).isEqualTo(written);
        assertThat(result.medians().get("pessimistic").get("tps").asDouble()).isEqualTo(866.6);
        assertThat(result.medians().get("redis").get("p95_ms").asDouble()).isEqualTo(1082.1);
        assertThat(result.runs().size()).isEqualTo(2);
        assertThat(result.runs().get(1).get("strategy").asString()).isEqualTo("redis");
    }

    @Test
    void getLatest_brokenFile_isInternalError() throws Exception {
        Files.writeString(tempDir.resolve(LoadTestResultService.MEDIANS_FILE), "{not json");
        Files.writeString(tempDir.resolve(LoadTestResultService.RUNS_FILE), RUNS);

        assertThatThrownBy(() -> service.getLatest())
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.INTERNAL_ERROR);
    }
}

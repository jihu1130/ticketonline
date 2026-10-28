package io.github.jihu1130.ticketonline.domain.admin.service;

import io.github.jihu1130.ticketonline.domain.admin.dto.LoadTestResultResponse;
import io.github.jihu1130.ticketonline.global.exception.CustomException;
import io.github.jihu1130.ticketonline.global.exception.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * load-test/run.py 가 남긴 결과 파일(medians.json, results.json)을 읽는다.
 * run.py 는 측정할 때마다 파일을 덮어쓰므로 항상 가장 최근 측정 결과다.
 */
@Service
public class LoadTestResultService {

    static final String MEDIANS_FILE = "medians.json";
    static final String RUNS_FILE = "results.json";

    private final Path resultsDir;
    private final JsonMapper jsonMapper;

    public LoadTestResultService(@Value("${app.load-test.results-dir}") String resultsDir, JsonMapper jsonMapper) {
        this.resultsDir = Path.of(resultsDir);
        this.jsonMapper = jsonMapper;
    }

    public LoadTestResultResponse getLatest() {
        Path medians = resultsDir.resolve(MEDIANS_FILE);
        Path runs = resultsDir.resolve(RUNS_FILE);
        if (!Files.isRegularFile(medians) || !Files.isRegularFile(runs)) {
            return LoadTestResultResponse.empty();
        }
        try {
            // 측정 시각 = 회차별 결과 파일을 마지막으로 쓴 시각
            LocalDateTime measuredAt = LocalDateTime.ofInstant(Files.getLastModifiedTime(runs).toInstant(),
                    ZoneId.systemDefault());
            return new LoadTestResultResponse(true, measuredAt,
                    jsonMapper.readTree(medians.toFile()), jsonMapper.readTree(runs.toFile()));
        } catch (IOException | JacksonException e) {
            throw new CustomException(ErrorCode.INTERNAL_ERROR, "부하 테스트 결과 파일을 읽지 못했습니다.");
        }
    }
}

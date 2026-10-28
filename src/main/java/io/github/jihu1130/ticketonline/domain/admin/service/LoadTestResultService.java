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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * load-test/run.py 가 남긴 결과 파일을 읽는다.
 *  - 기본 결과(1000명): 결과 폴더의 medians.json, results.json
 *  - 동시 인원별 결과(run.py --users): 결과 폴더/scale/medians.json
 *  - 한계 측정 결과(run.py stress): 결과 폴더/stress/results.json
 * run.py 는 측정할 때마다 파일을 덮어쓰므로 항상 가장 최근 측정 결과다.
 */
@Service
public class LoadTestResultService {

    static final String MEDIANS_FILE = "medians.json";
    static final String RUNS_FILE = "results.json";
    static final String SCALE_DIR = "scale";
    static final String STRESS_DIR = "stress";

    private final Path resultsDir;
    private final JsonMapper jsonMapper;

    public LoadTestResultService(@Value("${app.load-test.results-dir}") String resultsDir, JsonMapper jsonMapper) {
        this.resultsDir = Path.of(resultsDir);
        this.jsonMapper = jsonMapper;
    }

    public LoadTestResultResponse getLatest() {
        Path medians = resultsDir.resolve(MEDIANS_FILE);
        Path runs = resultsDir.resolve(RUNS_FILE);
        Path scaleMedians = resultsDir.resolve(SCALE_DIR).resolve(MEDIANS_FILE);
        Path stressStages = resultsDir.resolve(STRESS_DIR).resolve(RUNS_FILE);
        boolean exists = Files.isRegularFile(medians) && Files.isRegularFile(runs);
        boolean scaleExists = Files.isRegularFile(scaleMedians);
        boolean stressExists = Files.isRegularFile(stressStages);
        // 측정 시각 = 결과 파일을 마지막으로 쓴 시각
        return new LoadTestResultResponse(exists,
                exists ? modifiedAt(runs) : null,
                exists ? read(medians) : null,
                exists ? read(runs) : null,
                scaleExists ? modifiedAt(scaleMedians) : null,
                scaleExists ? read(scaleMedians) : null,
                stressExists ? modifiedAt(stressStages) : null,
                stressExists ? read(stressStages) : null);
    }

    private JsonNode read(Path file) {
        try {
            return jsonMapper.readTree(file.toFile());
        } catch (JacksonException e) {
            throw readFailed();
        }
    }

    private LocalDateTime modifiedAt(Path file) {
        try {
            return LocalDateTime.ofInstant(Files.getLastModifiedTime(file).toInstant(), ZoneId.systemDefault());
        } catch (IOException e) {
            throw readFailed();
        }
    }

    private static CustomException readFailed() {
        return new CustomException(ErrorCode.INTERNAL_ERROR, "부하 테스트 결과 파일을 읽지 못했습니다.");
    }
}

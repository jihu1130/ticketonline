package io.github.jihu1130.ticketonline.domain.admin.controller;

import io.github.jihu1130.ticketonline.domain.admin.dto.LoadTestResultResponse;
import io.github.jihu1130.ticketonline.domain.admin.service.LoadTestResultService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자(ADMIN)만 쓸 수 있다. 권한은 SecurityConfig 에서 확인한다. */
@Tag(name = "관리자", description = "부하 테스트 결과 조회")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final LoadTestResultService loadTestResultService;

    @Operation(summary = "k6 부하 테스트 결과", description = "A/B/C 방식별 중앙값과 회차별 결과 (가장 최근 측정)")
    @GetMapping("/load-tests")
    public LoadTestResultResponse loadTests() {
        return loadTestResultService.getLatest();
    }
}

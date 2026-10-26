package io.github.jihu1130.ticketonline.domain.queue.controller;

import io.github.jihu1130.ticketonline.domain.queue.dto.QueueResponse;
import io.github.jihu1130.ticketonline.domain.queue.service.QueueService;
import io.github.jihu1130.ticketonline.global.security.LoginMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "대기열", description = "F-07 구매하기 -> 대기열 -> 입장한 사람만 구매")
@RestController
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;

    @Operation(summary = "대기열 들어가기",
            description = "이미 줄에 있으면 처음 순번 그대로. 판매 중인 상품만 들어갈 수 있다")
    @PostMapping("/api/products/{productId}/queue")
    public QueueResponse enter(@AuthenticationPrincipal LoginMember loginMember, @PathVariable Long productId) {
        return queueService.enter(loginMember.id(), productId);
    }

    @Operation(summary = "내 대기 순번",
            description = "WAITING: 순번·전체 인원·예상 시간 / ADMITTED: 입장 / NOT_IN_QUEUE: 다시 들어가야 함")
    @GetMapping("/api/products/{productId}/queue/me")
    public QueueResponse me(@AuthenticationPrincipal LoginMember loginMember, @PathVariable Long productId) {
        return queueService.status(loginMember.id(), productId);
    }
}

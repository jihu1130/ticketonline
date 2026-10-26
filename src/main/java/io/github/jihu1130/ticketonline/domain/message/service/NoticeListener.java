package io.github.jihu1130.ticketonline.domain.message.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 구매·취소·변경이 커밋된 뒤 알림을 따로(다른 스레드에서) 저장한다.
 * 구매 요청은 알림 저장을 기다리지 않으므로 구매 응답 시간과 A/B/C 비교에 영향을 주지 않는다.
 * 알림 저장이 실패해도 구매는 이미 끝났으므로 기록만 남긴다.
 * app.notice.enabled=false 이면 저장하지 않는다 (k6 비교 때 DB 부하를 빼고 싶을 때).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoticeListener {

    private final MessageService messageService;

    @Value("${app.notice.enabled:true}")
    private boolean enabled;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotice(NoticeEvent event) {
        if (!enabled) {
            return;
        }
        try {
            messageService.saveNotice(event);
        } catch (RuntimeException e) {
            log.warn("알림 저장 실패: receiver={}, {}", event.receiverId(), e.getMessage());
        }
    }
}

package io.github.jihu1130.ticketonline.domain.queue.dto;

/** 대기열에서 내 상태 */
public enum QueueStatus {
    /** 줄을 서서 기다리는 중 */
    WAITING,
    /** 입장함. 정해진 시간 동안 예매 화면에서 구매할 수 있다 */
    ADMITTED,
    /** 줄에 없음 (들어온 적이 없거나, 오래 순번 확인이 없어 빠졌거나, 입장 시간이 끝남). 다시 들어오면 된다 */
    NOT_IN_QUEUE
}

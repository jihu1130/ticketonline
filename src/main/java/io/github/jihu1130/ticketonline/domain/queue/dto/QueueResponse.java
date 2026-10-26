package io.github.jihu1130.ticketonline.domain.queue.dto;

/**
 * 대기열 상태.
 * WAITING : rank(내 순번, 1부터), totalWaiting(지금 기다리는 전체 인원), estimatedSeconds(입장까지 예상 시간)
 * ADMITTED: admittedSeconds(예매 화면을 더 쓸 수 있는 시간, 대기열을 끈 경우 null)
 */
public record QueueResponse(
        QueueStatus status,
        Long rank,
        Long totalWaiting,
        Long estimatedSeconds,
        Long admittedSeconds
) {

    public static QueueResponse waiting(long rank, long totalWaiting, long estimatedSeconds) {
        return new QueueResponse(QueueStatus.WAITING, rank, totalWaiting, estimatedSeconds, null);
    }

    public static QueueResponse admitted(Long admittedSeconds) {
        return new QueueResponse(QueueStatus.ADMITTED, null, null, null, admittedSeconds);
    }

    public static QueueResponse notInQueue() {
        return new QueueResponse(QueueStatus.NOT_IN_QUEUE, null, null, null, null);
    }
}

package io.github.jihu1130.ticketonline.domain.message.dto;

/** 안 읽은 개수 (헤더 메시지함 옆 숫자는 total, 탭 숫자는 notice·chat) */
public record UnreadCountResponse(long notice, long chat, long total) {

    public static UnreadCountResponse of(long notice, long chat) {
        return new UnreadCountResponse(notice, chat, notice + chat);
    }
}

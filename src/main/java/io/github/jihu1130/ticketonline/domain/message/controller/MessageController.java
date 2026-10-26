package io.github.jihu1130.ticketonline.domain.message.controller;

import io.github.jihu1130.ticketonline.domain.message.dto.MessageResponse;
import io.github.jihu1130.ticketonline.domain.message.dto.UnreadCountResponse;
import io.github.jihu1130.ticketonline.domain.message.service.MessageService;
import io.github.jihu1130.ticketonline.global.security.LoginMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "메시지함", description = "알림(구매·취소·변경 완료, 내 상품 매진). 회원 간 대화는 채팅")
@RestController
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @Operation(summary = "알림 목록", description = "최근 100건")
    @GetMapping("/api/members/me/messages")
    public List<MessageResponse> list(@AuthenticationPrincipal LoginMember loginMember) {
        return messageService.getNotices(loginMember.id());
    }

    @Operation(summary = "안 읽은 개수", description = "notice(알림) + chat(채팅 메시지) = total (헤더 숫자)")
    @GetMapping("/api/members/me/messages/unread-count")
    public UnreadCountResponse unreadCount(@AuthenticationPrincipal LoginMember loginMember) {
        return messageService.countUnread(loginMember.id());
    }

    @Operation(summary = "알림 읽음 표시")
    @PostMapping("/api/members/me/messages/{messageId}/read")
    public MessageResponse read(@AuthenticationPrincipal LoginMember loginMember, @PathVariable Long messageId) {
        return messageService.markRead(loginMember.id(), messageId);
    }

    @Operation(summary = "알림 모두 읽음")
    @PostMapping("/api/members/me/messages/read-all")
    public UnreadCountResponse readAll(@AuthenticationPrincipal LoginMember loginMember) {
        return messageService.markAllRead(loginMember.id());
    }
}

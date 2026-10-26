package io.github.jihu1130.ticketonline.domain.message.controller;

import io.github.jihu1130.ticketonline.domain.message.dto.MessageBox;
import io.github.jihu1130.ticketonline.domain.message.dto.MessageRequest;
import io.github.jihu1130.ticketonline.domain.message.dto.MessageResponse;
import io.github.jihu1130.ticketonline.domain.message.dto.UnreadCountResponse;
import io.github.jihu1130.ticketonline.domain.message.service.MessageService;
import io.github.jihu1130.ticketonline.global.security.LoginMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "메시지함", description = "알림(구매·취소·변경 완료, 내 상품 매진)과 쪽지(상품 문의, 답장)")
@RestController
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @Operation(summary = "메시지 목록", description = "box: NOTICE(알림) / INBOX(받은 쪽지) / SENT(보낸 쪽지). 최근 100건")
    @GetMapping("/api/members/me/messages")
    public List<MessageResponse> list(@AuthenticationPrincipal LoginMember loginMember,
                                      @RequestParam(defaultValue = "NOTICE") MessageBox box) {
        return messageService.getMessages(loginMember.id(), box);
    }

    @Operation(summary = "안 읽은 개수")
    @GetMapping("/api/members/me/messages/unread-count")
    public UnreadCountResponse unreadCount(@AuthenticationPrincipal LoginMember loginMember) {
        return messageService.countUnread(loginMember.id());
    }

    @Operation(summary = "읽음 표시")
    @PostMapping("/api/members/me/messages/{messageId}/read")
    public MessageResponse read(@AuthenticationPrincipal LoginMember loginMember, @PathVariable Long messageId) {
        return messageService.markRead(loginMember.id(), messageId);
    }

    @Operation(summary = "모두 읽음", description = "box: NOTICE 또는 INBOX")
    @PostMapping("/api/members/me/messages/read-all")
    public UnreadCountResponse readAll(@AuthenticationPrincipal LoginMember loginMember,
                                       @RequestParam MessageBox box) {
        return messageService.markAllRead(loginMember.id(), box);
    }

    @Operation(summary = "상품 문의 보내기", description = "그 상품의 판매자에게 쪽지를 보낸다")
    @PostMapping("/api/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse send(@AuthenticationPrincipal LoginMember loginMember,
                                @Valid @RequestBody MessageRequest.Send request) {
        return messageService.send(loginMember.id(), request.productId(), request.content());
    }

    @Operation(summary = "답장", description = "받은 쪽지를 보낸 사람에게")
    @PostMapping("/api/messages/{messageId}/reply")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse reply(@AuthenticationPrincipal LoginMember loginMember, @PathVariable Long messageId,
                                 @Valid @RequestBody MessageRequest.Reply request) {
        return messageService.reply(loginMember.id(), messageId, request.content());
    }
}

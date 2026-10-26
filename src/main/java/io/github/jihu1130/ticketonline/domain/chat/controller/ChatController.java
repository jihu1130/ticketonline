package io.github.jihu1130.ticketonline.domain.chat.controller;

import io.github.jihu1130.ticketonline.domain.chat.dto.ChatMessageResponse;
import io.github.jihu1130.ticketonline.domain.chat.dto.ChatRequest;
import io.github.jihu1130.ticketonline.domain.chat.dto.ChatRoomResponse;
import io.github.jihu1130.ticketonline.domain.chat.service.ChatService;
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

@Tag(name = "채팅", description = "상품 문의 채팅 (상품 하나 x 구매자 한 명 = 방 하나). 화면은 2초마다 새 메시지를 확인한다")
@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "내 채팅방 목록", description = "최근 대화 순, 방마다 안 읽은 개수")
    @GetMapping("/api/chats")
    public List<ChatRoomResponse> rooms(@AuthenticationPrincipal LoginMember loginMember) {
        return chatService.getRooms(loginMember.id());
    }

    @Operation(summary = "상품 판매자와의 채팅방 열기", description = "없으면 만든다. 본인 상품은 문의할 수 없다")
    @PostMapping("/api/chats")
    public ChatRoomResponse open(@AuthenticationPrincipal LoginMember loginMember,
                                 @Valid @RequestBody ChatRequest.Open request) {
        return chatService.openRoom(loginMember.id(), request.productId());
    }

    @Operation(summary = "메시지 가져오기",
            description = "after 없으면 최근 100건, 있으면 그 번호 다음부터. 가져간 메시지까지 읽음으로 표시")
    @GetMapping("/api/chats/{roomId}/messages")
    public List<ChatMessageResponse> messages(@AuthenticationPrincipal LoginMember loginMember, @PathVariable Long roomId,
                                              @RequestParam(required = false) Long after) {
        return chatService.getMessages(loginMember.id(), roomId, after);
    }

    @Operation(summary = "메시지 보내기")
    @PostMapping("/api/chats/{roomId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatMessageResponse send(@AuthenticationPrincipal LoginMember loginMember, @PathVariable Long roomId,
                                    @Valid @RequestBody ChatRequest.Send request) {
        return chatService.send(loginMember.id(), roomId, request.content());
    }
}

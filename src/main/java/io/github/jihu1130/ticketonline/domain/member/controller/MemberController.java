package io.github.jihu1130.ticketonline.domain.member.controller;

import io.github.jihu1130.ticketonline.domain.member.dto.LoginRequest;
import io.github.jihu1130.ticketonline.domain.member.dto.LoginResponse;
import io.github.jihu1130.ticketonline.domain.member.dto.MemberResponse;
import io.github.jihu1130.ticketonline.domain.member.dto.SignupRequest;
import io.github.jihu1130.ticketonline.domain.member.service.MemberService;
import io.github.jihu1130.ticketonline.global.security.LoginMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "회원", description = "F-01 회원가입 / 로그인")
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @Operation(summary = "회원가입", description = "한 계정으로 상품 판매와 구매를 모두 할 수 있다")
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse signup(@Valid @RequestBody SignupRequest request) {
        return memberService.signup(request);
    }

    @Operation(summary = "로그인", description = "성공하면 accessToken(JWT)을 돌려준다")
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return memberService.login(request);
    }

    @Operation(summary = "내 정보", description = "로그인 토큰이 맞는지 확인할 때 쓴다")
    @GetMapping("/me")
    public MemberResponse me(@AuthenticationPrincipal LoginMember loginMember) {
        return memberService.getMember(loginMember.id());
    }
}

package io.github.jihu1130.ticketonline.global.security;

import io.github.jihu1130.ticketonline.domain.member.entity.MemberRole;

/**
 * 토큰에서 꺼낸 로그인 회원 정보.
 * 컨트롤러에서 @AuthenticationPrincipal LoginMember loginMember 로 받는다.
 */
public record LoginMember(Long id, MemberRole role) {
}

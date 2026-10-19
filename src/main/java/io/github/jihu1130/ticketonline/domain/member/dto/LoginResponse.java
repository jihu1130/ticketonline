package io.github.jihu1130.ticketonline.domain.member.dto;

import io.github.jihu1130.ticketonline.domain.member.entity.MemberRole;

/** 로그인 성공 응답. 화면은 accessToken을 저장해 두고 요청마다 Authorization 헤더에 넣는다. */
public record LoginResponse(String accessToken, Long memberId, String name, MemberRole role) {
}

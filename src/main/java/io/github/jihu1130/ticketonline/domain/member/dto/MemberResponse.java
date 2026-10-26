package io.github.jihu1130.ticketonline.domain.member.dto;

import io.github.jihu1130.ticketonline.domain.member.entity.Member;
import io.github.jihu1130.ticketonline.domain.member.entity.MemberRole;
import java.time.LocalDateTime;

/** 회원 정보 (회원가입 결과, 마이페이지). createdAt: 가입일 */
public record MemberResponse(Long memberId, String email, String name, MemberRole role, LocalDateTime createdAt) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(member.getId(), member.getEmail(), member.getName(), member.getRole(),
                member.getCreatedAt());
    }
}

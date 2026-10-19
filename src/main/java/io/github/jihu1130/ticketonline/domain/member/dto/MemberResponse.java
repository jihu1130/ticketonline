package io.github.jihu1130.ticketonline.domain.member.dto;

import io.github.jihu1130.ticketonline.domain.member.entity.Member;
import io.github.jihu1130.ticketonline.domain.member.entity.MemberRole;

public record MemberResponse(Long memberId, String email, String name, MemberRole role) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(member.getId(), member.getEmail(), member.getName(), member.getRole());
    }
}

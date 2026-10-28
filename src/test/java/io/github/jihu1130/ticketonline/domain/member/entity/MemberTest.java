package io.github.jihu1130.ticketonline.domain.member.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MemberTest {

    @Test
    void role_defaultsToUser() {
        Member member = Member.builder().email("a@test.com").password("x").name("a").build();

        assertThat(member.getRole()).isEqualTo(MemberRole.USER);
    }

    @Test
    void role_canBeAdmin() {
        Member member = Member.builder().email("a@test.com").password("x").name("a").role(MemberRole.ADMIN).build();

        assertThat(member.getRole()).isEqualTo(MemberRole.ADMIN);
    }
}

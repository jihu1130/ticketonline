package io.github.jihu1130.ticketonline.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.jihu1130.ticketonline.domain.member.entity.MemberRole;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

class JwtProviderTest {

    private static final String SECRET = "test-secret-key-for-jwt-provider-0123456789";

    @Test
    void createdTokenCanBeParsed() {
        JwtProvider provider = new JwtProvider(SECRET, 60_000);

        LoginMember loginMember = provider.parse(provider.createToken(7L, MemberRole.USER));

        assertThat(loginMember.id()).isEqualTo(7L);
        assertThat(loginMember.role()).isEqualTo(MemberRole.USER);
    }

    @Test
    void tokenSignedWithOtherKeyIsRejected() {
        JwtProvider other = new JwtProvider("another-secret-key-for-jwt-provider-0123456789", 60_000);
        String forged = other.createToken(7L, MemberRole.USER);

        assertThatThrownBy(() -> new JwtProvider(SECRET, 60_000).parse(forged))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        JwtProvider provider = new JwtProvider(SECRET, -1_000);
        String expired = provider.createToken(7L, MemberRole.USER);

        assertThatThrownBy(() -> provider.parse(expired))
                .isInstanceOf(ExpiredJwtException.class);
    }
}

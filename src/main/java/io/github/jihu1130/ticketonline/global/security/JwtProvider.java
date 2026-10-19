package io.github.jihu1130.ticketonline.global.security;

import io.github.jihu1130.ticketonline.domain.member.entity.MemberRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 로그인 토큰(JWT)을 만들고 읽는다. 토큰에는 회원 ID와 역할만 담는다. */
@Component
public class JwtProvider {

    private static final String ROLE_CLAIM = "role";

    private final SecretKey key;
    private final long expirationMs;

    public JwtProvider(@Value("${jwt.secret}") String secret,
                       @Value("${jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String createToken(Long memberId, MemberRole role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .claim(ROLE_CLAIM, role.name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    /**
     * 토큰을 검증하고 회원 정보를 꺼낸다.
     * 서명이 틀리거나 만료된 토큰이면 io.jsonwebtoken.JwtException 을 던진다.
     */
    public LoginMember parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new LoginMember(
                Long.valueOf(claims.getSubject()),
                MemberRole.valueOf(claims.get(ROLE_CLAIM, String.class)));
    }
}

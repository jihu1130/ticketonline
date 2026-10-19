package io.github.jihu1130.ticketonline.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.jihu1130.ticketonline.domain.member.dto.LoginRequest;
import io.github.jihu1130.ticketonline.domain.member.dto.LoginResponse;
import io.github.jihu1130.ticketonline.domain.member.dto.SignupRequest;
import io.github.jihu1130.ticketonline.domain.member.entity.Member;
import io.github.jihu1130.ticketonline.domain.member.entity.MemberRole;
import io.github.jihu1130.ticketonline.domain.member.repository.MemberRepository;
import io.github.jihu1130.ticketonline.global.exception.CustomException;
import io.github.jihu1130.ticketonline.global.exception.ErrorCode;
import io.github.jihu1130.ticketonline.global.security.JwtProvider;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class MemberServiceTest {

    private final MemberRepository memberRepository = Mockito.mock(MemberRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtProvider jwtProvider = new JwtProvider("test-secret-key-for-member-service-0123456789", 60_000);
    private MemberService memberService;

    @BeforeEach
    void setUp() {
        memberService = new MemberService(memberRepository, passwordEncoder, jwtProvider);
    }

    @Test
    void signup_storesEncryptedPassword() {
        given(memberRepository.existsByEmail("buyer@test.com")).willReturn(false);

        memberService.signup(new SignupRequest("buyer@test.com", "password123", "회원"));

        ArgumentCaptor<Member> saved = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPassword()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", saved.getValue().getPassword())).isTrue();
    }

    @Test
    void signup_rejectsDuplicateEmail() {
        given(memberRepository.existsByEmail("buyer@test.com")).willReturn(true);

        assertThatThrownBy(() -> memberService.signup(
                new SignupRequest("buyer@test.com", "password123", "회원")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_EMAIL);
        verify(memberRepository, never()).saveAndFlush(any());
    }

    @Test
    void signup_simultaneousDuplicateIsStillDuplicateEmail() {
        given(memberRepository.existsByEmail("buyer@test.com")).willReturn(false);
        given(memberRepository.saveAndFlush(any())).willThrow(new DataIntegrityViolationException("unique"));

        assertThatThrownBy(() -> memberService.signup(
                new SignupRequest("buyer@test.com", "password123", "회원")))
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_EMAIL);
    }

    @Test
    void login_returnsTokenWithMemberInfo() {
        Member member = savedMember(3L, "seller@test.com", "password123");
        given(memberRepository.findByEmail("seller@test.com")).willReturn(Optional.of(member));

        LoginResponse response = memberService.login(new LoginRequest("seller@test.com", "password123"));

        assertThat(response.memberId()).isEqualTo(3L);
        assertThat(jwtProvider.parse(response.accessToken()).role()).isEqualTo(MemberRole.USER);
    }

    @Test
    void login_wrongPasswordAndUnknownEmailGiveSameError() {
        Member member = savedMember(3L, "seller@test.com", "password123");
        given(memberRepository.findByEmail("seller@test.com")).willReturn(Optional.of(member));
        given(memberRepository.findByEmail("nobody@test.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.login(new LoginRequest("seller@test.com", "wrong-pass")))
                .extracting("errorCode").isEqualTo(ErrorCode.LOGIN_FAILED);
        assertThatThrownBy(() -> memberService.login(new LoginRequest("nobody@test.com", "password123")))
                .extracting("errorCode").isEqualTo(ErrorCode.LOGIN_FAILED);
    }

    private Member savedMember(Long id, String email, String rawPassword) {
        Member member = Member.builder()
                .email(email)
                .password(passwordEncoder.encode(rawPassword))
                .name("회원")
                .build();
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}

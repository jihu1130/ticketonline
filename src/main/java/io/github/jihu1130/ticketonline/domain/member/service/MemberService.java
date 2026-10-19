package io.github.jihu1130.ticketonline.domain.member.service;

import io.github.jihu1130.ticketonline.domain.member.dto.LoginRequest;
import io.github.jihu1130.ticketonline.domain.member.dto.LoginResponse;
import io.github.jihu1130.ticketonline.domain.member.dto.MemberResponse;
import io.github.jihu1130.ticketonline.domain.member.dto.SignupRequest;
import io.github.jihu1130.ticketonline.domain.member.entity.Member;
import io.github.jihu1130.ticketonline.domain.member.repository.MemberRepository;
import io.github.jihu1130.ticketonline.global.exception.CustomException;
import io.github.jihu1130.ticketonline.global.exception.ErrorCode;
import io.github.jihu1130.ticketonline.global.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** F-01 회원가입 / 로그인 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Transactional
    public MemberResponse signup(SignupRequest request) {
        if (memberRepository.existsByEmail(request.email())) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }
        Member member = Member.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .name(request.name())
                .build();
        try {
            // 같은 이메일로 동시에 가입하면 위 확인을 둘 다 통과할 수 있어, DB unique 제약으로 한 번 더 막는다
            memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }
        return MemberResponse.from(member);
    }

    /** 이메일과 비밀번호 중 어느 쪽이 틀렸는지는 알려 주지 않는다. */
    public LoginResponse login(LoginRequest request) {
        Member member = memberRepository.findByEmail(request.email())
                .filter(m -> m.getDeletedAt() == null)
                .orElseThrow(() -> new CustomException(ErrorCode.LOGIN_FAILED));
        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new CustomException(ErrorCode.LOGIN_FAILED);
        }
        String token = jwtProvider.createToken(member.getId(), member.getRole());
        return new LoginResponse(token, member.getId(), member.getName(), member.getRole());
    }

    public MemberResponse getMember(Long memberId) {
        return memberRepository.findById(memberId)
                .filter(m -> m.getDeletedAt() == null)
                .map(MemberResponse::from)
                .orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));
    }
}

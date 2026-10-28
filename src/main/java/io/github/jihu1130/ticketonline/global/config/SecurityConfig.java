package io.github.jihu1130.ticketonline.global.config;

import io.github.jihu1130.ticketonline.global.security.JwtAuthenticationFilter;
import io.github.jihu1130.ticketonline.global.security.JwtProvider;
import io.github.jihu1130.ticketonline.global.security.SecurityErrorHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * JWT 로그인과 접근 권한 (기능 명세서 2항 공통 규칙)
 * - 누구나 : 화면(html·css·js), 회원가입, 로그인, 상품 목록·상세 조회, Swagger
 * - 로그인한 회원 : 그 밖의 모든 기능 (한 계정으로 판매와 구매를 모두 한다)
 * - 관리자(ADMIN) : /api/admin/** (부하 테스트 결과)
 * 본인 상품만 수정·삭제(FORBIDDEN)처럼 상품마다 다른 규칙은 서비스에서 확인한다.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtProvider jwtProvider;
    private final SecurityErrorHandler securityErrorHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/error").permitAll()
                        // 화면 파일(static 폴더): 로그인 확인은 화면 안의 API 호출에서 한다
                        .requestMatchers(HttpMethod.GET, "/", "/*.html", "/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                        // 업로드한 상품 사진은 누구나 볼 수 있다 (올리기는 로그인 필요)
                        .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/members/signup", "/api/members/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/*").permitAll()
                        // 관리자 화면 API (부하 테스트 결과 등). 일반 회원은 FORBIDDEN
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // 상품 등록, 판매 현황, 구매, 대기열 등 나머지는 로그인한 회원 누구나
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtProvider), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

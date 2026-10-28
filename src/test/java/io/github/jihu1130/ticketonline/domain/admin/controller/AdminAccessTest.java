package io.github.jihu1130.ticketonline.domain.admin.controller;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.jihu1130.ticketonline.domain.member.entity.MemberRole;
import io.github.jihu1130.ticketonline.global.security.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** /api/admin/** 는 관리자(ADMIN)만. 일반 회원은 FORBIDDEN, 로그인 안 하면 UNAUTHORIZED. */
@SpringBootTest(properties = "app.load-test.results-dir=build/no-load-test-results")
@ActiveProfiles("test")
class AdminAccessTest {

    @Autowired private WebApplicationContext context;
    @Autowired private JwtProvider jwtProvider;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void notLoggedIn_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/load-tests"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void user_isForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/load-tests")
                        .header("Authorization", "Bearer " + jwtProvider.createToken(1L, MemberRole.USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void admin_canReadResults() throws Exception {
        // 결과 폴더가 없으면 빈 결과
        mockMvc.perform(get("/api/admin/load-tests")
                        .header("Authorization", "Bearer " + jwtProvider.createToken(1L, MemberRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(false));
    }
}

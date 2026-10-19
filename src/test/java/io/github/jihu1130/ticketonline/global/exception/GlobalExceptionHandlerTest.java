package io.github.jihu1130.ticketonline.global.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 모든 오류가 { code, message } 형식과 알맞은 HTTP 상태로 나가는지 확인한다. */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void customException_usesItsErrorCode() throws Exception {
        mockMvc.perform(get("/test/sold-out"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SOLD_OUT"))
                .andExpect(jsonPath("$.message").value("매진되었습니다."));
    }

    @Test
    void customException_canOverrideMessage() throws Exception {
        mockMvc.perform(get("/test/limit"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message").value("1인당 최대 2개까지 구매할 수 있습니다."));
    }

    @Test
    void validationFailure_isInvalidInput() throws Exception {
        mockMvc.perform(post("/test/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("title : 상품명을 입력해 주세요."));
    }

    @Test
    void brokenJson_isInvalidInput() throws Exception {
        mockMvc.perform(post("/test/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void unexpectedException_hidesDetails() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."));
    }

    @RestController
    static class TestController {

        @GetMapping("/test/sold-out")
        void soldOut() {
            throw new CustomException(ErrorCode.SOLD_OUT);
        }

        @GetMapping("/test/limit")
        void limit() {
            throw new CustomException(ErrorCode.LIMIT_EXCEEDED, "1인당 최대 2개까지 구매할 수 있습니다.");
        }

        @PostMapping("/test/valid")
        void valid(@Valid @RequestBody TestRequest request) {
        }

        @GetMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("DB 비밀번호 같은 내부 정보");
        }
    }

    record TestRequest(@NotBlank(message = "상품명을 입력해 주세요.") String title) {
    }
}

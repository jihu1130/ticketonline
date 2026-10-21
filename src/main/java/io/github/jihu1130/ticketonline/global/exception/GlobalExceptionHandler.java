package io.github.jihu1130.ticketonline.global.exception;

import io.github.jihu1130.ticketonline.global.response.ErrorResponse;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 모든 컨트롤러의 예외를 { code, message } 형식으로 바꿔 응답한다. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 서비스에서 던진 CustomException */
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustom(CustomException e) {
        ErrorCode code = e.getErrorCode();
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code, e.getMessage()));
    }

    /**
     * @Valid 검사 실패: 첫 번째로 틀린 항목의 안내 문구를 보낸다.
     * 안내 문구가 이미 한국어로 무엇이 틀렸는지 말하므로 영어 항목 이름은 붙이지 않는다.
     * 목록 안의 항목은 몇 번째인지 붙인다. 예) seatGrades[1].name -> "2번째 좌석 등급: 등급 이름을 입력해 주세요."
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null
                ? toUserMessage(fieldError.getField(), fieldError.getDefaultMessage())
                : ErrorCode.INVALID_INPUT.getMessage();
        return build(ErrorCode.INVALID_INPUT, message);
    }

    /** 목록 항목 이름 -> 화면에 보일 이름 */
    private static final Map<String, String> LIST_NAMES = Map.of(
            "seatGrades", "좌석 등급",
            "performances", "공연 회차");

    private static final Pattern LIST_FIELD = Pattern.compile("^(\\w+)\\[(\\d+)]");

    static String toUserMessage(String field, String message) {
        Matcher m = LIST_FIELD.matcher(field);
        if (m.find()) {
            String name = LIST_NAMES.getOrDefault(m.group(1), "항목");
            int position = Integer.parseInt(m.group(2)) + 1;
            return position + "번째 " + name + ": " + message;
        }
        return message;
    }

    /** JSON 형식이 잘못되었거나, 숫자 자리에 글자가 들어온 경우 */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception e) {
        return build(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.getMessage());
    }

    /** 사진 크기 제한(spring.servlet.multipart) 초과 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException e) {
        return build(ErrorCode.INVALID_INPUT, "사진은 5MB 이하만 올릴 수 있습니다.");
    }

    /** multipart 요청에 file 항목이 없거나 형식이 잘못된 경우 */
    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
    public ResponseEntity<ErrorResponse> handleBadMultipart(Exception e) {
        return build(ErrorCode.INVALID_INPUT, "사진 파일을 선택해 주세요.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException e) {
        return build(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.getMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return build(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.getMessage());
    }

    /** 위에서 처리하지 못한 예외: 원인은 로그로 남기고, 사용자에게는 공통 문구만 보낸다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("처리하지 못한 예외", e);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage());
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode code, String message) {
        return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code, message));
    }
}

package io.github.jihu1130.ticketonline.global.exception;

import lombok.Getter;

/**
 * 서비스에서 실패를 알릴 때 던지는 예외.
 * 예) throw new CustomException(ErrorCode.SOLD_OUT);
 *     throw new CustomException(ErrorCode.LIMIT_EXCEEDED, "1인당 최대 2개까지 구매할 수 있습니다.");
 */
@Getter
public class CustomException extends RuntimeException {

    private final ErrorCode errorCode;

    public CustomException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 기본 문구 대신 상황에 맞는 문구를 보낼 때 */
    public CustomException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}

package io.github.jihu1130.ticketonline.global.response;

import io.github.jihu1130.ticketonline.global.exception.ErrorCode;

/** 오류 응답 형식: { "code": "SOLD_OUT", "message": "매진되었습니다." } */
public record ErrorResponse(String code, String message) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.name(), message);
    }
}

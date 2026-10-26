package io.github.jihu1130.ticketonline.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 기능 명세서 4항 오류 코드 정리. 화면은 code를 보고 안내 문구를 띄운다. */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력한 내용을 다시 확인해 주세요."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 주소를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."),

    // 회원 / 권한 (F-01)
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    /** 다른 사람의 상품을 수정하는 경우 등 */
    FORBIDDEN(HttpStatus.FORBIDDEN, "이 기능을 사용할 권한이 없습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),

    // 상품 (F-02, F-03)
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
    ALREADY_ON_SALE(HttpStatus.CONFLICT, "판매가 시작된 상품은 수정할 수 없습니다."),

    // 구매 (F-04, F-05, F-07)
    NOT_ON_SALE(HttpStatus.BAD_REQUEST, "지금은 구매할 수 없는 상품입니다."),
    SOLD_OUT(HttpStatus.CONFLICT, "매진되었습니다."),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "남은 수량보다 많이 구매할 수 없습니다."),
    LIMIT_EXCEEDED(HttpStatus.CONFLICT, "1인당 구매할 수 있는 수량을 넘었습니다."),
    NOT_ADMITTED(HttpStatus.FORBIDDEN, "대기 순서가 되면 구매할 수 있습니다."),

    // 주문 취소·변경
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
    ALREADY_CANCELED(HttpStatus.CONFLICT, "이미 취소된 주문입니다."),
    CHANGE_CLOSED(HttpStatus.BAD_REQUEST, "취소·변경할 수 있는 기간이 지났습니다."),
    TOO_MANY_REQUESTS(HttpStatus.SERVICE_UNAVAILABLE, "요청이 많아 처리하지 못했습니다. 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String message;
}

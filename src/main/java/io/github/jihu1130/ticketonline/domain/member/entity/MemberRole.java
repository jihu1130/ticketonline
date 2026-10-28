package io.github.jihu1130.ticketonline.domain.member.entity;

/**
 * 회원 역할. 일반 회원(USER)은 한 계정으로 상품 판매와 구매를 모두 할 수 있다.
 * ADMIN은 관리자 화면(부하 테스트 결과 등)을 쓸 수 있다. 회원가입으로는 만들 수 없다.
 */
public enum MemberRole {
    USER,
    ADMIN
}

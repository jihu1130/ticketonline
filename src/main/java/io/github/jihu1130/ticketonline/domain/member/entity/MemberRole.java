package io.github.jihu1130.ticketonline.domain.member.entity;

/**
 * 회원 역할. 일반 회원(USER)은 한 계정으로 상품 판매와 구매를 모두 할 수 있다.
 * ADMIN은 나중에 관리 기능을 만들 때를 위해 남겨 둔다.
 */
public enum MemberRole {
    USER,
    ADMIN
}

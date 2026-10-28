# 티켓온라인 (ticketonline)

> 동시 접속에도 **초과 판매 0건**을 목표로 하는 한정 수량 티켓 · 물품 선착순 판매 서비스

인기 공연 티켓이나 한정판 물품은 판매 시작과 동시에 수많은 요청이 몰립니다.
이때 준비한 수량보다 더 많이 팔리거나(초과 판매), 한 사람이 물량을 독차지하는 문제가 생깁니다.

이 프로젝트는 **같은 선착순 구매 기능을 세 가지 동시성 제어 방식으로 구현**하고,
k6 부하 테스트로 정합성과 성능을 수치로 비교합니다.

<br>

## 핵심 목표

| 항목 | 목표 |
|---|---|
| 초과 판매 건수 | **0건** |
| 1인 구매 제한 초과 | **0건** (같은 사람이 동시에 여러 번 요청해도) |
| 응답 시간 (p95) | 300ms 이하 |
| 에러율 (5xx) | 1% 미만 (매진 응답은 정상으로 봄) |

> 테스트 조건 : 수량 100개 상품 1개에 1,000명이 동시에 1개씩 구매 요청

<br>

## 동시성 제어 방식 비교

| 방식 | 핵심 아이디어 | 장점 | 단점 |
|---|---|---|---|
| A. DB 비관적 락 | `SELECT ... FOR UPDATE`로 재고 행과 1인 구매 수량 행을 잠근 뒤 차감 | 구현이 단순하고 정합성이 확실 | 요청이 줄을 서서 처리 |
| B. DB 낙관적 락 | `@Version` 비교 후 갱신, 충돌하면 다시 시도 (최대 3번) | 락 대기가 없음 | 요청이 몰리면 충돌이 많아 실패(503) 발생 |
| C. Redis | Lua 스크립트로 1인 한도 · 남은 수량 확인과 차감을 한 번에 처리, 통과한 요청만 DB에 기록 | 매진 이후 요청을 DB까지 보내지 않음 | DB 저장이 실패하면 Redis 수량을 되돌려야 함 |

세 방식은 모두 코드에 들어 있고, 서버를 켤 때 설정 `app.purchase.strategy` 로 **하나를 골라** 씁니다.

| 설정 값 | 방식 |
|---|---|
| `pessimistic` (기본값) | A. 비관적 락 |
| `optimistic` | B. 낙관적 락 |
| `redis` | C. Redis |

```bash
./gradlew bootRun --args='--app.purchase.strategy=redis'
```

### 테스트 결과 (k6, 2026-10-26)

방식마다 서버를 새로 켜고 예열 1번 뒤 3번 측정한 **중앙값**입니다.

| 방식 | 초과 판매 | TPS | p95 응답 시간 | 에러율 (5xx) | 성공 / 매진 / 5xx |
|---|---|---|---|---|---|
| A. 비관적 락 | **0건** | 867 | 1,116ms | 0% | 100 / 900 / 0 |
| B. 낙관적 락 | **0건** | 544 | 1,705ms | 10.9% | 100 / 791 / 109 |
| C. Redis | **0건** | 900 | 1,082ms | 0% | 100 / 900 / 0 |

- 세 방식 모두 초과 판매 0건이며, DB 판매 수량 = 주문 수량 합계 = 100
- B의 5xx는 3번 다시 시도해도 충돌한 요청(`TOO_MANY_REQUESTS`)
- p95 목표(300ms)는 세 방식 모두 넘음 (k6와 서버를 같은 PC에서 실행, DB 연결 10개)
- 회차별 결과는 관리자 화면(`/admin.html`) 또는 `load-test/results/` 에서 볼 수 있음

<br>

## 주요 기능

| ID | 기능 | 설명 | 상태 |
|---|---|---|---|
| F-01 | 회원가입 / 로그인 | JWT 로그인. 한 계정으로 판매와 구매를 모두 할 수 있음 | ✅ 완료 |
| F-02 | 상품 등록 | 티켓(회차 · 좌석 등급) / 한정 물품 선택, 사진 업로드, 1인 구매 제한, 판매 기간 | ✅ 완료 |
| F-03 | 상품 목록 / 상세 조회 | 종류별 목록, 회차 · 등급별 남은 수량과 판매 상태 (3초마다 갱신) | ✅ 완료 |
| F-04 | 선착순 구매 | 남은 수량과 1인 제한 안에서만 구매 확정 (방식 A / B / C) | ✅ 완료 |
| F-05 | 1인 구매 수량 제한 | 티켓은 회차별, 물품은 상품별. 동시에 요청해도 제한 수량을 넘지 않음 | ✅ 완료 |
| F-06 | 구매 내역 조회 | 내가 산 티켓과 물품 목록 | ✅ 완료 |
| F-07 | 대기열 | 구매하기 → Redis 대기열에서 순번 안내 → 입장한 사람만 예매 화면에서 구매 | ✅ 완료 |
| F-08 | 주문 취소 / 변경 | 취소, 수량 줄이기, 배송지 변경, 회차 · 등급 변경 (예매 마감 전까지) | ✅ 완료 |
| F-09 | 메시지함 | 구매 · 변경 알림, 판매자 문의 채팅 | ✅ 완료 |
| - | 관리자 화면 | 관리자(ADMIN)만 k6 부하 테스트 결과 조회 | ✅ 완료 |

<br>

## 기술 스택

| 구분 | 기술 |
|---|---|
| Backend | Java 21, Spring Boot 4.1, Spring Data JPA, Spring Security, JWT (jjwt) |
| Database | MySQL 8 |
| Cache | Redis (대기열, 방식 C 재고) |
| Frontend | HTML, CSS, JavaScript (Spring `static` 폴더, fetch) |
| API 문서 | Swagger (springdoc-openapi) |
| 부하 테스트 | k6 |

<br>

## 프로젝트 구조

```
ticketonline/
 ├── load-test/                k6 부하 테스트 (purchase.js, run.py, results/)
 └── src/main/java/io/github/jihu1130/ticketonline
      ├── domain
      │    ├── member     회원
      │    ├── product    상품 (티켓 · 물품, 회차, 좌석 등급, 판매 단위 = 재고)
      │    ├── order      주문, 1인 구매 수량, 구매 방식 A / B / C, 주문 취소 · 변경
      │    ├── queue      대기열 (Redis)
      │    ├── message    알림
      │    ├── chat       판매자 문의 채팅
      │    └── admin      관리자 (부하 테스트 결과)
      └── global
           ├── config     Security, Swagger, 비동기 · 스케줄 설정
           ├── security   JWT 토큰 처리
           ├── exception  오류 코드, 전역 예외 처리
           ├── response   공통 응답 형식
           └── upload     상품 사진 업로드
```

기획 문서(기획서, 기능 명세서, ERD, 개발 순서)는 저장소에 포함되어 있지 않습니다.

<br>

## 실행 방법

### 1. MySQL 데이터베이스 만들기

```sql
CREATE DATABASE ticketonline DEFAULT CHARACTER SET utf8mb4;
```

### 2. 비밀 설정 파일 만들기

DB 비밀번호 기본값은 `root` / `1234`입니다. JWT 서명 키는 기본값이 없어서 **이 파일이 없으면 서버가 시작되지 않습니다.**
이 파일은 `.gitignore`에 들어 있어 GitHub에 올라가지 않습니다.

`src/main/resources/application-secret.yaml`
```yaml
spring:
  datasource:
    password: 내_DB_비밀번호

jwt:
  secret: 32자_이상의_아무_문자열
```

### 3. Redis 실행

대기열(기본으로 켜짐)과 방식 C에 필요합니다. `localhost:6379` 에 Redis를 띄워 주세요.
Windows에서는 Redis Windows 판(5.0.14.1) 압축을 푼 폴더에서 실행합니다.

```bash
./redis-server.exe redis.windows.conf
```

### 4. 서버 실행

```bash
./gradlew bootRun
```

| 주소 | 내용 |
|---|---|
| http://localhost:8888 | 메인 화면 |
| http://localhost:8888/swagger-ui/index.html | API 문서 (Swagger) |
| http://localhost:8888/admin.html | 관리자 화면 (ADMIN 계정만) |

> Swagger에서 로그인이 필요한 API를 시험하려면, 로그인 API로 받은 `accessToken`을 오른쪽 위 **Authorize** 버튼에 넣으세요.

### 5. (선택) 관리자 계정과 예시 상품 넣기

개발 DB에 관리자 계정(`admin@admin.admin` / `admin`)과 실행 시각 기준 예시 상품 6개(판매 중 · 판매 예정 · 오늘 오픈)를 만듭니다.
관리자 계정이 이미 있으면 아무것도 하지 않습니다.

```bash
./gradlew test --tests "*DevDataSeed*" --rerun
```

<br>

## 테스트

```bash
./gradlew test
```

- 테스트 전용 DB `ticketonline_test` 를 자동으로 만들어 씁니다 (개발 DB는 건드리지 않음).
- 동시성 테스트는 실제 MySQL에서 1,000명 동시 구매 등을 방식 A / B / C 로 각각 실행합니다.
- Redis 테스트는 Redis 15번 DB를 쓰며, Redis가 꺼져 있으면 건너뜁니다.

### k6 부하 테스트

MySQL, Redis, k6, Java 21이 필요합니다. 방식마다 서버를 새로 띄워 같은 조건으로 측정하고 `load-test/results/` 에 결과를 남깁니다.
자세한 내용은 [load-test/README.md](load-test/README.md)를 참고하세요.

```bash
py -3 load-test/run.py
```

<br>

## 오류 응답 형식

모든 오류는 같은 형식으로 응답합니다.

```json
{ "code": "SOLD_OUT", "message": "매진되었습니다." }
```

전체 오류 코드는 기능 명세서 4항을 참고하세요.

<br>

## 개발 진행 상황

- [x] 0단계 개발 환경 세팅
- [x] 1단계 Entity & Repository
- [x] 2단계 공통 오류 처리
- [x] 3단계 회원가입 / 로그인 (F-01)
- [x] 4단계 상품 등록 (F-02)
- [x] 5단계 상품 목록 / 상세 조회 (F-03)
- [x] 6단계 선착순 구매 - 방식 A 비관적 락 (F-04, F-05)
- [x] 7단계 동시성 테스트
- [x] 8단계 방식 B 낙관적 락
- [x] 9단계 방식 C Redis
- [x] 10단계 화면 마무리
- [x] 11단계 k6 부하 테스트 & 결과 정리
- [ ] 12단계 선택 기능 - 구매 내역(F-06), 대기열(F-07) 완료 / 방식 C 개선(DB 연결 전에 Redis에서 거절) 진행 중
- [ ] 13단계 마무리 (결과 보고서, 발표 자료)

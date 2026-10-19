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
| 에러율 | 1% 미만 (매진 응답은 정상으로 봄) |

> 테스트 조건 : 수량 100개 상품 1개에 1,000명이 동시에 구매 요청

<br>

## 동시성 제어 방식 비교

| 방식 | 핵심 아이디어 | 예상 장점 | 예상 단점 |
|---|---|---|---|
| A. DB 비관적 락 | `SELECT ... FOR UPDATE`로 상품 행을 잠근 뒤 수량 차감 | 구현이 단순하고 정합성이 확실 | 요청이 줄을 서서 처리량 저하 |
| B. DB 낙관적 락 | `version` 비교 후 갱신, 충돌하면 재시도 (최대 3회) | 락 대기가 없음 | 요청이 몰리면 재시도 폭증 |
| C. Redis 원자 연산 | `DECRBY`로 수량을 먼저 차감, 성공한 요청만 DB 저장 | 처리량이 가장 높을 것으로 예상 | Redis–DB 불일치 복구 로직 필요 |

### 테스트 결과

| 방식 | 초과 판매 | TPS | p95 응답 시간 | 에러율 |
|---|---|---|---|---|
| A. 비관적 락 | 측정 예정 | 측정 예정 | 측정 예정 | 측정 예정 |
| B. 낙관적 락 | 측정 예정 | 측정 예정 | 측정 예정 | 측정 예정 |
| C. Redis | 측정 예정 | 측정 예정 | 측정 예정 | 측정 예정 |

<br>

## 주요 기능

| ID | 기능 | 설명 | 상태 |
|---|---|---|---|
| F-01 | 회원가입 / 로그인 | JWT 로그인. 한 계정으로 판매와 구매를 모두 할 수 있음 | ✅ 완료 |
| F-02 | 상품 등록 | 등록할 때 종류(티켓 / 한정 물품)를 선택, 1인 구매 제한 설정 | 🔨 진행 예정 |
| F-03 | 상품 목록 / 상세 조회 | 종류별 목록, 남은 수량과 판매 상태 표시 | 🔨 진행 예정 |
| F-04 | 선착순 구매 | 남은 수량과 1인 제한 안에서만 구매 확정 (핵심 기능) | 🔨 진행 예정 |
| F-05 | 1인 구매 수량 제한 | 여러 번 나눠 사거나 동시에 요청해도 제한 수량을 넘지 않음 | 🔨 진행 예정 |
| F-06 | 구매 내역 조회 | 내가 산 티켓과 물품 목록 | 선택 기능 |
| F-07 | 대기열 안내 | 요청이 몰리면 줄을 세워 순서대로 입장 | 선택 기능 |

<br>

## 기술 스택

| 구분 | 기술 |
|---|---|
| Backend | Java 21, Spring Boot 4.1, Spring Data JPA, Spring Security, JWT (jjwt) |
| Database | MySQL 8 |
| Cache / 재고 | Redis (방식 C) |
| Frontend | HTML, CSS, JavaScript (Spring `static` 폴더) |
| API 문서 | Swagger (springdoc-openapi) |
| 부하 테스트 | k6 |

<br>

## 프로젝트 구조

```
ticketonline/
 ├── proposal/                         기획 문서
 │    ├── 웹프로잭트_기획서.docx
 │    ├── 웹프로잭트_기능명세서.docx
 │    ├── ERD01.html  ERD02.html         ERD (브라우저로 열기)
 │    └── 개발순서.txt
 └── ticketonline/                     Spring Boot 프로젝트
      └── src/main/java/io/github/jihu1130/ticketonline
           ├── domain
           │    ├── member     회원 (controller / service / repository / entity / dto)
           │    ├── product    상품 (티켓 · 물품)
           │    └── order      주문, 1인 구매 수량
           └── global
                ├── config     Security, Swagger 설정
                ├── security   JWT 토큰 처리
                ├── exception  오류 코드, 전역 예외 처리
                └── response   공통 응답 형식
```

<br>

## 실행 방법

### 1. MySQL 데이터베이스 만들기

```sql
CREATE DATABASE ticketonline DEFAULT CHARACTER SET utf8mb4;
```

### 2. DB 비밀번호 설정

기본값은 `root` / `1234`입니다. 다르면 아래 파일을 직접 만들어 주세요.
이 파일은 `.gitignore`에 들어 있어 GitHub에 올라가지 않습니다.

`ticketonline/src/main/resources/application-secret.yaml`
```yaml
spring:
  datasource:
    password: 내_DB_비밀번호
```

### 3. 서버 실행

```bash
cd ticketonline
./gradlew bootRun
```

| 주소 | 내용 |
|---|---|
| http://localhost:8888 | 메인 화면 |
| http://localhost:8888/swagger-ui/index.html | API 문서 (Swagger) |

> Swagger에서 로그인이 필요한 API를 시험하려면, 로그인 API로 받은 `accessToken`을 오른쪽 위 **Authorize** 버튼에 넣으세요.

<br>

## 오류 응답 형식

모든 오류는 같은 형식으로 응답합니다.

```json
{ "code": "SOLD_OUT", "message": "매진되었습니다." }
```

전체 오류 코드는 [기능 명세서](proposal/웹프로잭트_기능명세서.docx) 4항을 참고하세요.

<br>

## 개발 진행 상황

- [x] 0단계 개발 환경 세팅
- [x] 1단계 Entity & Repository
- [x] 2단계 공통 오류 처리
- [x] 3단계 회원가입 / 로그인 (F-01)
- [ ] 4단계 상품 등록 (F-02)
- [ ] 5단계 상품 목록 / 상세 조회 (F-03)
- [ ] 6단계 선착순 구매 - 방식 A 비관적 락 (F-04, F-05)
- [ ] 7단계 동시성 테스트
- [ ] 8단계 방식 B 낙관적 락
- [ ] 9단계 방식 C Redis
- [ ] 10단계 화면 마무리
- [ ] 11단계 k6 부하 테스트 & 결과 정리

자세한 순서는 [개발순서.txt](proposal/개발순서.txt)를 참고하세요.

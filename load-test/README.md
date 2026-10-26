# k6 부하 테스트 (11단계)

구매 방식 A(비관적 락) / B(낙관적 락) / C(Redis)를 같은 조건으로 실행해 비교합니다.

## 조건
- 재고 100개 굿즈(1인 1개)에 회원 1000명이 동시에 1개씩 구매 요청
- 대기열·알림은 끄고 구매 처리만 비교 (`app.queue.enabled=false`, `app.notice.enabled=false`)
- DB 연결 수는 기본값(10)으로 세 방식 모두 같게
- 방식마다 서버를 새로 띄우고, 기록하지 않는 예열 1번 뒤 3번 측정해 중앙값을 씀
- 부하 테스트 전용 DB `ticketonline_load`, Redis 3번 DB 사용 (개발 데이터와 섞이지 않음)

## 측정 방법
1. 1000명이 상품 조회로 서버와 연결을 먼저 맺어 둡니다 (2초에 걸쳐 나눠서).
   한꺼번에 연결하면 Windows의 연결 대기 줄(약 200)이 넘쳐 연결 자체가 거절되므로, 구매 처리 대신 연결 한계를 재게 됩니다.
2. 같은 시각에 1000명이 열린 연결로 동시에 구매 요청을 보냅니다. 아래 값은 이 구매 요청만 집계합니다.

| 항목 | 계산 |
|---|---|
| 초과 판매 건수 | DB의 판매 수량·주문 수량 합계가 재고(100)를 넘은 만큼 |
| 처리량 (TPS) | 구매 요청 1000건 / 출발부터 마지막 응답까지 걸린 시간 |
| 응답 시간 (p95) | 구매 요청 응답 시간의 95번째 백분위 |
| 에러율 | 5xx 응답 비율 (매진 409는 정상 응답). 방식 B의 503 TOO_MANY_REQUESTS(3번 충돌)도 포함 |

## 실행
준비물: MySQL, Redis(`C:\redis`), k6, Java 21. DB 비밀번호는 `src/main/resources/application-secret.yaml`에서 읽습니다.

```bash
py -3 load-test/run.py
```

방식 하나만 n번: `py -3 load-test/run.py redis 1` (pessimistic / optimistic / redis)

결과는 `load-test/results/`에 저장됩니다 (`results.md` 표, `results.json` 회차별 값, `medians.json` 중앙값).
`load-test/work/`(테스트 회원 토큰, 서버 로그)는 커밋하지 않습니다.

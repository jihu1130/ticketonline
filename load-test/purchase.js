// k6 부하 테스트 (11단계): 재고 100개 굿즈에 1000명이 동시에 1개씩 구매한다.
// run.py 가 준비한 상품·회원 토큰을 받아 실행한다. 직접 실행 예:
//   k6 run -e BASE_URL=http://localhost:8891 -e PRODUCT_ID=1 -e SALE_ITEM_ID=1 -e TOKENS=work/tokens.json purchase.js
//
// 진행
//   1) 1000명이 상품 조회(GET)로 서버와 연결을 먼저 맺어 둔다 (2초에 걸쳐 나눠서).
//      한꺼번에 1000개 연결을 맺으면 PC(Windows)의 연결 대기 줄(약 200)이 넘쳐 연결 자체가 거절되기 때문.
//   2) setup 에서 정한 같은 시각에 1000명이 그 연결로 동시에 구매(POST)한다. 측정은 이 구매 요청만 한다.
//
// 판정 (기획서 3-2)
//   201 성공, 409 매진(SOLD_OUT 등) = 정상 응답
//   5xx = 서버 오류 -> 에러율. 방식 B 의 503 TOO_MANY_REQUESTS(3번 충돌)도 여기에 들어간다
import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Trend } from "k6/metrics";
import { SharedArray } from "k6/data";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8891";
const PRODUCT_ID = __ENV.PRODUCT_ID;
const SALE_ITEM_ID = Number(__ENV.SALE_ITEM_ID);
const VUS = Number(__ENV.VUS || 1000);
const CONNECT_SPREAD_MS = 2000; // 연결을 나눠 맺는 시간
const tokens = new SharedArray("tokens", () => JSON.parse(open(__ENV.TOKENS)));

const purchased = new Counter("purchase_success");
const soldOut = new Counter("purchase_sold_out");
const serverErrors = new Counter("server_errors");
const otherErrors = new Counter("other_errors");
const noResponse = new Counter("no_response");
// 출발 시각부터 이 요청의 응답을 받을 때까지(ms). 가장 큰 값 = 1000건을 모두 처리하는 데 걸린 시간 -> TPS
const finishedAt = new Trend("purchase_finished_at", true);

// 201·409 를 "기대한 응답"으로 보아 http_req_failed 에는 그 밖의 응답(5xx, 401 등)만 잡힌다
http.setResponseCallback(http.expectedStatuses(200, 201, 409));

export const options = {
  scenarios: {
    rush: {
      executor: "per-vu-iterations", // 사람마다 1번
      vus: VUS,
      iterations: 1,
      maxDuration: "2m",
    },
  },
  // 구매 요청만 따로 집계되도록 이름표(name:purchase) 기준 항목을 만든다 (조건은 항상 통과)
  thresholds: {
    "http_req_duration{name:purchase}": ["max>=0"],
    "http_reqs{name:purchase}": ["count>=0"],
  },
  summaryTrendStats: ["avg", "min", "med", "p(90)", "p(95)", "p(99)", "max"],
};

/** 모든 사람이 같은 시각에 출발하도록 출발 시각을 정한다 (연결 맺는 시간 + 여유 1초) */
export function setup() {
  return { startAt: Date.now() + CONNECT_SPREAD_MS + 1000 };
}

export default function (data) {
  const token = tokens[(__VU - 1) % tokens.length];
  const headers = { "Content-Type": "application/json", Authorization: `Bearer ${token}` };

  // 1) 연결 미리 맺기
  sleep(((__VU - 1) % VUS) * (CONNECT_SPREAD_MS / VUS) / 1000);
  http.get(`${BASE_URL}/api/products/${PRODUCT_ID}`, { headers, tags: { name: "warmup" } });

  // 2) 출발 시각까지 기다렸다가 동시에 구매
  const wait = data.startAt - Date.now();
  if (wait > 0) sleep(wait / 1000);
  const res = http.post(
    `${BASE_URL}/api/products/${PRODUCT_ID}/orders`,
    JSON.stringify({ saleItemId: SALE_ITEM_ID, quantity: 1 }),
    { headers, timeout: "60s", tags: { name: "purchase" } },
  );
  finishedAt.add(Date.now() - data.startAt);
  if (res.status === 201) purchased.add(1);
  else if (res.status === 409) soldOut.add(1);
  else if (res.status >= 500) serverErrors.add(1);
  else if (res.status === 0) noResponse.add(1);
  else otherErrors.add(1);
  check(res, { "201 또는 409": (r) => r.status === 201 || r.status === 409 });
}

// 결과를 run.py 가 읽을 JSON 으로 남긴다
export function handleSummary(data) {
  const out = __ENV.SUMMARY || "work/summary.json";
  return { [out]: JSON.stringify(data, null, 2) };
}

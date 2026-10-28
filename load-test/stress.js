// k6 한계 측정: 조회 · 대기열 · 구매 · 기타 요청을 섞어 정한 초당 요청 수(RATE)로 DURATION 동안 보낸다.
// run.py stress 가 RATE 를 단계별로 올리며 이 스크립트를 여러 번 실행한다. 직접 실행 예:
//   k6 run -e BASE_URL=http://localhost:8891 -e ITEMS=1:1,2:2 -e TOKENS=work/tokens.json -e RATE=500 stress.js
//
// 요청 비율 (RATE 를 나눠 가진다)
//   구경 60% : 상품 목록 20%, 상품 상세 40% (실제 상세 화면은 3초마다 다시 조회)
//   구매 30% : 내 대기열 상태 조회 -> 줄에 없으면 줄 서기, 입장했으면 구매 (실제 대기열 화면은 1초마다 조회)
//   기타 10% : 구매 내역, 안 읽은 알림 수
// 사용자마다 상품 하나를 정해 둔다 (사용자 i -> 상품 i % 상품 수)
//
// 판정: 5xx 와 무응답(연결 실패 등)만 오류로 센다. 409(매진·한도 초과), 403(입장 전 구매) 는 정상 응답
// 오류가 너무 많거나 너무 느려지면 그 단계에서 스스로 멈춘다 (PC 보호)
import http from "k6/http";
import { Counter } from "k6/metrics";
import { SharedArray } from "k6/data";
import exec from "k6/execution";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8891";
const RATE = Number(__ENV.RATE || 200);
const DURATION = __ENV.DURATION || "30s";
const MAX_VUS = Number(__ENV.MAX_VUS || 2000);
const ITEMS = (__ENV.ITEMS || "").split(",").map((pair) => pair.split(":").map(Number)); // [[상품, 판매 단위], ...]
const tokens = new SharedArray("tokens", () => JSON.parse(open(__ENV.TOKENS)));

const serverErrors = new Counter("server_errors");
const noResponse = new Counter("no_response");
const purchased = new Counter("purchase_success");

http.setResponseCallback(http.expectedStatuses(200, 201, 403, 409));

function scenario(fn, share) {
  const rate = Math.max(1, Math.round(RATE * share));
  return {
    executor: "constant-arrival-rate",
    exec: fn,
    rate,
    timeUnit: "1s",
    duration: DURATION,
    // 요청 하나가 끝나야 다음을 보낼 수 있으므로 느려질수록 사용자가 더 필요하다. 모자라면 dropped_iterations 로 남는다
    preAllocatedVUs: Math.min(Math.max(10, Math.round(rate / 5)), MAX_VUS),
    maxVUs: Math.max(10, Math.round(MAX_VUS * share)),
  };
}

export const options = {
  scenarios: {
    list: scenario("list", 0.2),
    detail: scenario("detail", 0.4),
    queue: scenario("queue", 0.3),
    misc: scenario("misc", 0.1),
  },
  thresholds: {
    // 오류 20% 이상이거나 p95 가 10초를 넘으면 10초 지켜본 뒤 멈춘다
    http_req_failed: [{ threshold: "rate<0.2", abortOnFail: true, delayAbortEval: "10s" }],
    http_req_duration: [{ threshold: "p(95)<10000", abortOnFail: true, delayAbortEval: "10s" }],
    // 요청 종류별 항목을 만들기 위한 것 (조건은 항상 통과)
    "http_req_duration{group:list}": ["max>=0"],
    "http_req_duration{group:detail}": ["max>=0"],
    "http_req_duration{group:queue}": ["max>=0"],
    "http_req_duration{group:purchase}": ["max>=0"],
    "http_req_duration{group:misc}": ["max>=0"],
  },
  summaryTrendStats: ["avg", "med", "p(95)", "p(99)", "max"],
};

function user() {
  const i = exec.scenario.iterationInTest % tokens.length;
  return { i, headers: { "Content-Type": "application/json", Authorization: `Bearer ${tokens[i]}` } };
}

function record(res) {
  if (res.status === 0) noResponse.add(1);
  else if (res.status >= 500) serverErrors.add(1);
  return res;
}

export function list() {
  record(http.get(`${BASE_URL}/api/products`, { tags: { group: "list" }, timeout: "30s" }));
}

export function detail() {
  const [productId] = ITEMS[exec.scenario.iterationInTest % ITEMS.length];
  record(http.get(`${BASE_URL}/api/products/${productId}`, { tags: { group: "detail" }, timeout: "30s" }));
}

export function queue() {
  const { i, headers } = user();
  const [productId, saleItemId] = ITEMS[i % ITEMS.length];
  const res = record(http.get(`${BASE_URL}/api/products/${productId}/queue/me`, { headers, tags: { group: "queue" }, timeout: "30s" }));
  if (res.status !== 200) return;
  const status = res.json("status");
  if (status === "NOT_IN_QUEUE") {
    record(http.post(`${BASE_URL}/api/products/${productId}/queue`, null, { headers, tags: { group: "queue" }, timeout: "30s" }));
  } else if (status === "ADMITTED") {
    const order = record(http.post(`${BASE_URL}/api/products/${productId}/orders`,
      JSON.stringify({ saleItemId, quantity: 1 }), { headers, tags: { group: "purchase" }, timeout: "30s" }));
    if (order.status === 201) purchased.add(1);
  }
}

export function misc() {
  const { i, headers } = user();
  const path = i % 2 === 0 ? "/api/members/me/orders" : "/api/members/me/messages/unread-count";
  record(http.get(`${BASE_URL}${path}`, { headers, tags: { group: "misc" }, timeout: "30s" }));
}

export function handleSummary(data) {
  const out = __ENV.SUMMARY || "work/stress-summary.json";
  return { [out]: JSON.stringify(data, null, 2) };
}

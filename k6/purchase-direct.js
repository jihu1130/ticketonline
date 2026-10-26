// 시나리오 1: 대기열 없이 구매 API 에 1000명이 한꺼번에 요청 (재고 100, 1인 1개)
// 실행:  k6 run k6/purchase-direct.js
// 옵션:  -e BASE_URL=http://localhost:8895  -e USERS=1000  -e STRATEGY=pessimistic (결과 표시용)
// 준비:  k6/seed.sql (처음 한 번), 회차마다 k6/reset.sql + Redis FLUSHDB
import http from 'k6/http';
import { check, fail } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8895';
const USERS = Number(__ENV.USERS || 1000);
const PASSWORD = 'k6-test-pass1';
const PRODUCT_TITLE = 'k6 부하 테스트 굿즈';
const LOGIN_BATCH = 50;

const success = new Counter('purchase_success');
const soldOut = new Counter('purchase_sold_out');
const tooMany = new Counter('purchase_too_many_requests');
const unexpected = new Counter('purchase_unexpected');
const unexpectedRate = new Rate('purchase_unexpected_rate');
const purchaseDuration = new Trend('purchase_duration', true);

export const options = {
    setupTimeout: '180s',
    scenarios: {
        purchase: {
            executor: 'per-vu-iterations',
            vus: USERS,
            iterations: 1,
            maxDuration: '60s',
        },
    },
    thresholds: {
        purchase_duration: ['p(95)<=300'],
        purchase_unexpected_rate: ['rate<0.01'],
    },
    summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

// 매진(409)과 혼잡(503)은 예상한 응답이라 http_req_failed 에 넣지 않는다
http.setResponseCallback(http.expectedStatuses(200, 201, 409, 503));

export function setup() {
    const products = http.get(`${BASE_URL}/api/products?type=GOODS`).json();
    const product = products.find((p) => p.title === PRODUCT_TITLE);
    if (!product) fail(`상품을 찾지 못했습니다: ${PRODUCT_TITLE} (seed.sql 실행 여부 확인)`);

    const tokens = [];
    for (let start = 1; start <= USERS; start += LOGIN_BATCH) {
        const requests = [];
        for (let n = start; n < start + LOGIN_BATCH && n <= USERS; n++) {
            const email = `k6-${String(n).padStart(4, '0')}@test.com`;
            requests.push(['POST', `${BASE_URL}/api/members/login`,
                JSON.stringify({ email, password: PASSWORD }),
                { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' } }]);
        }
        for (const res of http.batch(requests)) {
            if (res.status !== 200) fail(`로그인 실패: ${res.status} ${res.body}`);
            tokens.push(res.json('accessToken'));
        }
    }
    return { productId: product.id, tokens };
}

export default function (data) {
    const token = data.tokens[__VU - 1];
    const res = http.post(`${BASE_URL}/api/products/${data.productId}/orders`,
        JSON.stringify({ quantity: 1 }),
        {
            headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
            tags: { name: 'purchase' },
        });

    purchaseDuration.add(res.timings.duration);
    const code = res.status === 201 ? 'OK' : safeCode(res);

    if (code === 'OK') success.add(1);
    else if (code === 'SOLD_OUT') soldOut.add(1);
    else if (code === 'TOO_MANY_REQUESTS') tooMany.add(1);
    else {
        unexpected.add(1);
        console.warn(`예상 못 한 응답: ${res.status} ${res.error || res.body}`);
    }
    unexpectedRate.add(!['OK', 'SOLD_OUT', 'TOO_MANY_REQUESTS'].includes(code));

    check(res, { '구매 성공 또는 매진/혼잡 응답': () => code !== 'UNKNOWN' });
}

function safeCode(res) {
    try {
        return res.json('code') || 'UNKNOWN';
    } catch (e) {
        return 'UNKNOWN';
    }
}

export function handleSummary(data) {
    const m = data.metrics;
    const count = (name) => (m[name] ? m[name].values.count : 0);
    const d = m.purchase_duration.values;
    const lines = [
        '',
        `=== 시나리오 1 결과 (${__ENV.STRATEGY || '방식 미지정'}, ${USERS}명, 재고 100) ===`,
        `성공 ${count('purchase_success')} / 매진 ${count('purchase_sold_out')} / 혼잡(503) ${count('purchase_too_many_requests')} / 예상 못 한 응답 ${count('purchase_unexpected')}`,
        `응답 시간 ms: 평균 ${d.avg.toFixed(0)}, 중앙 ${d.med.toFixed(0)}, p95 ${d['p(95)'].toFixed(0)}, p99 ${d['p(99)'].toFixed(0)}, 최대 ${d.max.toFixed(0)}`,
        `기준(p95<=300ms, 예상 못 한 응답<1%): ${Object.values(m.purchase_duration.thresholds || {}).every((t) => t.ok)
            && Object.values(m.purchase_unexpected_rate.thresholds || {}).every((t) => t.ok) ? '통과' : '미달'}`,
        '',
    ];
    return { stdout: lines.join('\n') };
}

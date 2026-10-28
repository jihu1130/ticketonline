// 관리자 화면: k6 부하 테스트 결과 (load-test/run.py 가 남긴 가장 최근 측정)

const box = document.getElementById("admin");
const STRATEGY_LABEL = { pessimistic: "A 비관적 락", optimistic: "B 낙관적 락", redis: "C Redis" };
// 목표 (기획서): p95 300ms 이하, 에러율 1% 미만, 초과 판매 0
const GOAL_P95_MS = 300;
const GOAL_ERROR_RATE = 1;

if (!auth.token()) {
  location.href = `/login.html?redirect=${encodeURIComponent("/admin.html")}`;
}

async function load() {
  try {
    render(await api("/api/admin/load-tests"));
  } catch (e) {
    if (e.status === 401) return;
    const message = e.status === 403 ? "관리자만 볼 수 있는 화면입니다." : e.message;
    box.innerHTML = `<div class="empty">${escapeHtml(message)}</div>`;
  }
}

function render(result) {
  if (!result.exists && !result.scaleMedians && !result.stressStages) {
    box.innerHTML = `<div class="empty">측정 결과가 없습니다. load-test/run.py 로 측정하면 여기에 표시됩니다.</div>`;
    return;
  }
  box.innerHTML = (result.exists ? renderBase(result) : "")
    + (result.scaleMedians ? renderScale(result) : "")
    + (result.stressStages ? renderStress(result) : "");
}

/** 한계 측정 (run.py stress). 초당 요청 수를 올린 단계별 결과와, 멈춘 이유 */
function renderStress(result) {
  const rows = result.stressStages.map((s) => `
    <tr>
      <th>${num(s.target_rps)}</th>
      <td>${num(s.actual_rps)}</td>
      <td class="${s.p95_ms > GOAL_P95_MS ? "is-bad" : ""}">${num(s.p95_ms)}</td>
      <td class="${s.error_rate >= GOAL_ERROR_RATE ? "is-bad" : ""}">${num(s.error_rate)}</td>
      <td class="${s.dropped > 0 ? "is-bad" : ""}">${num(s.dropped)}</td>
      <td>${num(s.purchases)}</td>
      <td>${num(s.cpu_avg)} / ${num(s.cpu_max)}</td>
      <td>${num(s.mem_free_min_mb)}</td>
      <td>${groupP95(s.p95_by_group)}</td>
    </tr>
    ${s.stopped ? `<tr><td colspan="9" class="stress-stop">여기서 멈춤: ${escapeHtml(s.stopped)}</td></tr>` : ""}`);
  return `
    <section class="panel admin-section">
      <div class="section-head"><h2>한계 측정 (방식 C, 대기열 켬)</h2><span class="booking-meta">측정 ${formatDateTime(result.stressMeasuredAt)}</span></div>
      <div class="table-scroll">
        <table class="result-table">
          <thead><tr><th>목표 req/s</th><th>실제 req/s</th><th>p95 (ms)</th><th>오류율 (%)</th><th>못 보낸 요청</th><th>구매 성공</th><th>CPU 평균 / 최대 (%)</th><th>남은 메모리 (MB)</th><th>종류별 p95 (ms)</th></tr></thead>
          <tbody>${rows.join("")}</tbody>
        </table>
      </div>
      <p class="booking-meta">목록 20% · 상세 40% · 대기열/구매 30% · 구매 내역/알림 10%를 섞어 단계마다 30초씩 보냈습니다. 못 보낸 요청 = 응답이 느려 정한 속도로 보내지 못한 요청. k6 와 서버를 같은 PC 에서 실행한 결과입니다.</p>
    </section>`;
}

function groupP95(groups) {
  const names = { list: "목록", detail: "상세", queue: "대기열", purchase: "구매", misc: "기타" };
  return Object.entries(names)
    .filter(([key]) => groups?.[key] != null)
    .map(([key, name]) => `${name} ${num(groups[key])}`)
    .join(" · ");
}

/** 동시 인원별 중앙값 (run.py --users). 인원마다 A·B·C 를 이어서 보여 준다 */
function renderScale(result) {
  const levels = Object.keys(result.scaleMedians).sort((a, b) => Number(a) - Number(b));
  const rows = levels.flatMap((users) =>
    Object.entries(result.scaleMedians[users]).map(([strategy, m], i) => `
      <tr class="${i === 0 ? "group-start" : ""}">
        <td>${i === 0 ? `${num(Number(users))}명` : ""}</td>
        <th>${label(strategy)}</th>
        <td>${num(m.tps)}</td>
        <td class="${m.p95_ms > GOAL_P95_MS ? "is-bad" : ""}">${num(m.p95_ms)}</td>
        <td class="${m.error_rate >= GOAL_ERROR_RATE ? "is-bad" : ""}">${num(m.error_rate)}</td>
        <td>${num(m.success)}</td>
        <td>${num(m.sold_out)}</td>
        <td>${num(m.server_errors)}</td>
        <td class="${m.no_response > 0 ? "is-bad" : ""}">${num(m.no_response)}</td>
        <td class="${m.oversell > 0 ? "is-bad" : ""}">${num(m.oversell)}</td>
      </tr>`));
  return `
    <section class="panel admin-section">
      <div class="section-head"><h2>동시 인원별 결과 (중앙값)</h2><span class="booking-meta">측정 ${formatDateTime(result.scaleMeasuredAt)}</span></div>
      <div class="table-scroll">
        <table class="result-table">
          <thead><tr><th>인원</th><th>방식</th><th>TPS</th><th>p95 (ms)</th><th>에러율 (%)</th><th>성공</th><th>매진</th><th>서버 오류</th><th>무응답</th><th>초과 판매</th></tr></thead>
          <tbody>${rows.join("")}</tbody>
        </table>
      </div>
      <p class="booking-meta">재고 100개는 그대로 두고 동시 인원만 늘린 결과입니다. 무응답 = 연결 거절 등으로 응답을 받지 못한 요청.</p>
    </section>`;
}

function renderBase(result) {
  const strategies = Object.keys(result.medians);
  return `
    <section class="panel admin-section">
      <div class="section-head"><h2>방식별 결과 (1,000명, 중앙값)</h2><span class="booking-meta">측정 ${formatDateTime(result.measuredAt)}</span></div>
      <div class="table-scroll">
        <table class="result-table">
          <thead><tr><th>방식</th><th>TPS</th><th>p95 (ms)</th><th>에러율 (%)</th><th>성공</th><th>매진</th><th>서버 오류</th><th>초과 판매</th></tr></thead>
          <tbody>${strategies.map((s) => renderMedian(s, result.medians[s])).join("")}</tbody>
        </table>
      </div>
      <p class="booking-meta">빨간 값은 목표를 넘은 값입니다. 서버 오류는 B 방식에서 3번 다시 시도해도 실패한 요청(TOO_MANY_REQUESTS)입니다.</p>
    </section>

    <section class="panel admin-section">
      <div class="section-head"><h2>회차별 결과</h2><span class="booking-meta">${result.runs.length}회</span></div>
      <div class="table-scroll">
        <table class="result-table">
          <thead><tr><th>방식</th><th>회차</th><th>요청</th><th>성공</th><th>매진</th><th>서버 오류</th><th>TPS</th><th>p95</th><th>p99</th><th>평균</th><th>최대</th><th>에러율 (%)</th><th>초과 판매</th><th>DB 일치</th></tr></thead>
          <tbody>${result.runs.map(renderRun).join("")}</tbody>
        </table>
      </div>
      <p class="booking-meta">시간 단위는 ms. DB 일치 = 판매 수량과 주문 수량이 같은지.</p>
    </section>`;
}

function renderMedian(strategy, m) {
  return `
    <tr>
      <th>${label(strategy)}</th>
      <td>${num(m.tps)}</td>
      <td class="${m.p95_ms > GOAL_P95_MS ? "is-bad" : ""}">${num(m.p95_ms)}</td>
      <td class="${m.error_rate >= GOAL_ERROR_RATE ? "is-bad" : ""}">${num(m.error_rate)}</td>
      <td>${num(m.success)}</td>
      <td>${num(m.sold_out)}</td>
      <td>${num(m.server_errors)}</td>
      <td class="${m.oversell > 0 ? "is-bad" : ""}">${num(m.oversell)}</td>
    </tr>`;
}

function renderRun(r) {
  return `
    <tr>
      <th>${label(r.strategy)}</th>
      <td>${num(r.run)}</td>
      <td>${num(r.requests)}</td>
      <td>${num(r.success)}</td>
      <td>${num(r.sold_out)}</td>
      <td>${num(r.server_errors)}</td>
      <td>${num(r.tps)}</td>
      <td class="${r.p95_ms > GOAL_P95_MS ? "is-bad" : ""}">${num(r.p95_ms)}</td>
      <td>${num(r.p99_ms)}</td>
      <td>${num(r.avg_ms)}</td>
      <td>${num(r.max_ms)}</td>
      <td class="${r.error_rate >= GOAL_ERROR_RATE ? "is-bad" : ""}">${num(r.error_rate)}</td>
      <td class="${r.oversell > 0 ? "is-bad" : ""}">${num(r.oversell)}</td>
      <td class="${r.sold_matches_orders === false ? "is-bad" : ""}">${r.sold_matches_orders === false ? "불일치" : "일치"}</td>
    </tr>`;
}

function label(strategy) {
  return escapeHtml(STRATEGY_LABEL[strategy] ?? String(strategy));
}

function num(value) {
  return typeof value === "number" ? value.toLocaleString("ko-KR") : "-";
}

load();

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
  if (!result.exists) {
    box.innerHTML = `<div class="empty">측정 결과가 없습니다. load-test/run.py 로 측정하면 여기에 표시됩니다.</div>`;
    return;
  }
  const strategies = Object.keys(result.medians);
  box.innerHTML = `
    <section class="panel admin-section">
      <div class="section-head"><h2>방식별 결과 (중앙값)</h2><span class="booking-meta">측정 ${formatDateTime(result.measuredAt)}</span></div>
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

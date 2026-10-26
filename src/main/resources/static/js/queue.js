// 대기열 화면 (F-07): 구매하기 -> 이 화면에서 순서를 기다림 -> 입장하면 예매 화면으로 넘어간다.
// 1초마다 내 순번을 확인한다. 순번 확인이 30초 동안 없으면 서버가 줄에서 빼므로 화면이 숨겨져도 계속 확인한다.

const POLL_MS = 1000;

const productId = new URLSearchParams(location.search).get("id");
const queueBox = document.getElementById("queue");
const bookingUrl = `/booking.html?id=${encodeURIComponent(productId ?? "")}`;
let product = null;
let firstRank = null;
let timer = null;
let polling = false;

if (!auth.token()) {
  location.href = `/login.html?redirect=${encodeURIComponent(location.pathname + location.search)}`;
}

function showError(text) {
  clearInterval(timer);
  queueBox.innerHTML = `
    <div class="empty">
      ${escapeHtml(text)}
      ${productId ? `<p><a class="link" href="/product.html?id=${encodeURIComponent(productId)}">상품으로 돌아가기</a></p>` : ""}
    </div>`;
}

/** 125 -> "약 2분 5초", 0~1 -> "곧 입장" */
function formatWait(seconds) {
  if (seconds <= 1) return "곧 입장합니다";
  const min = Math.floor(seconds / 60);
  const sec = seconds % 60;
  return `약 ${min > 0 ? `${min}분 ` : ""}${sec > 0 || min === 0 ? `${sec}초` : ""}`.trim();
}

function render(state) {
  if (firstRank === null || state.rank > firstRank) firstRank = state.rank;
  // 처음 순번에서 얼마나 앞으로 왔는지
  const percent = firstRank > 1 ? Math.round(((firstRank - state.rank) / (firstRank - 1)) * 100) : 100;
  const ahead = state.rank - 1;
  const behind = Math.max(state.totalWaiting - state.rank, 0);

  queueBox.innerHTML = `
    <div class="panel queue-box">
      <p class="queue-product">${escapeHtml(product?.title ?? "")}</p>
      <h1>접속 대기 중입니다</h1>
      <p class="booking-meta">순서가 되면 예매 화면으로 자동으로 넘어갑니다.</p>
      <div class="queue-rank">
        <span>내 대기 순번</span>
        <strong>${state.rank.toLocaleString()}<em>번째</em></strong>
      </div>
      <div class="queue-bar"><div style="width:${percent}%"></div></div>
      <dl class="queue-stats">
        <div><dt>예상 대기 시간</dt><dd>${formatWait(state.estimatedSeconds)}</dd></div>
        <div><dt>내 앞</dt><dd>${ahead.toLocaleString()}명</dd></div>
        <div><dt>내 뒤</dt><dd>${behind.toLocaleString()}명</dd></div>
      </dl>
      <p class="queue-note">이 화면을 닫거나 새로고침하지 않고 기다려 주세요. 30초 넘게 연결이 끊기면 순번이 사라집니다.</p>
      <a class="btn btn-secondary" href="/product.html?id=${encodeURIComponent(productId)}">대기 그만두기</a>
    </div>`;
}

/** 서버 응답에 따라: 입장 -> 예매 화면, 줄에 없음 -> 다시 줄 서기, 대기 중 -> 순번 표시 */
async function handle(state) {
  if (state.status === "ADMITTED") {
    clearInterval(timer);
    location.replace(bookingUrl);
    return;
  }
  if (state.status === "NOT_IN_QUEUE") {
    firstRank = null;
    state = await enter();
    if (state.status !== "WAITING") return handle(state);
  }
  render(state);
}

function enter() {
  return api(`/api/products/${encodeURIComponent(productId)}/queue`, { method: "POST" });
}

async function poll() {
  if (polling) return;
  polling = true;
  try {
    await handle(await api(`/api/products/${encodeURIComponent(productId)}/queue/me`));
  } catch (e) {
    if (e.status === 401) {
      // api() 가 로그인 화면으로 보낸다 (로그인 후 이 화면으로 돌아옴)
    } else if (e.code !== "NETWORK_ERROR") {
      showError(e.message);
    }
    // 잠깐의 연결 오류는 다음 확인 때 다시 시도한다
  } finally {
    polling = false;
  }
}

async function start() {
  if (!productId) {
    showError("상품을 찾을 수 없습니다.");
    return;
  }
  try {
    product = await api(`/api/products/${encodeURIComponent(productId)}`);
    document.title = `대기열 - ${product.title} - 티켓온라인`;
    await handle(await enter());
    timer = setInterval(poll, POLL_MS);
  } catch (e) {
    if (e.status === 401) {
      // api() 가 로그인 화면으로 보낸다 (로그인 후 이 화면으로 돌아옴)
      return;
    }
    showError(e.message);
  }
}

start();

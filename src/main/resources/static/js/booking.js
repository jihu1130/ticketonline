// 예매 화면 (F-04): 티켓은 회차 -> 좌석 등급 -> 수량, 굿즈는 수량 -> (택배면) 배송지를 고르고 구매한다.
// 결제 기능 없이 바로 구매가 확정된다. 남은 수량은 3초마다 다시 불러오고, 고른 값은 그대로 둔다.
// 대기열에서 입장한 사람만 쓸 수 있다. 입장하지 않았거나 입장 시간이 끝나면 대기열 화면으로 돌려보낸다.

const REFRESH_MS = 3000;
const MAX_PER_ORDER = 100; // 서버 OrderRequest 와 같은 값

const productId = new URLSearchParams(location.search).get("id");
const bookingBox = document.getElementById("booking");
let product = null;
let refreshTimer = null;
let refreshing = false;
let done = false;

// 고른 값
const picked = { performanceId: null, saleItemId: null, quantity: 1 };

if (!auth.token()) {
  location.href = `/login.html?redirect=${encodeURIComponent(location.pathname + location.search)}`;
}

const isTicket = () => product.type === "TICKET";
const unit = () => (isTicket() ? "매" : "개");

function showError(text) {
  bookingBox.innerHTML = `<div class="empty">${escapeHtml(text)}</div>`;
}

// ---------- 처음 그리기 ----------

function render() {
  const p = product;
  document.title = `${isTicket() ? "예매하기" : "구매하기"} - ${p.title} - 티켓온라인`;

  if (p.status !== "ON_SALE") {
    bookingBox.innerHTML = `
      <div class="empty">
        지금은 ${isTicket() ? "예매" : "구매"}할 수 없는 상품입니다. (${PRODUCT_STATUS[p.status].label})
        <p><a class="link" href="/product.html?id=${p.id}">상품으로 돌아가기</a></p>
      </div>`;
    return;
  }

  const poster = p.imageUrl
    ? `<img class="booking-poster" src="${p.imageUrl}" alt="">`
    : `<div class="booking-poster poster-empty">${TYPE_LABEL[p.type]}</div>`;
  const where = isTicket()
    ? `<p class="booking-meta">${escapeHtml(p.venue ?? "")}</p>`
    : `<p class="booking-meta">수령 방법: ${DELIVERY_LABEL[p.deliveryType] ?? ""}</p>`;

  const ticketSteps = `
    <h3 class="step-title"><span>1</span>공연 회차</h3>
    <div class="choice-list" id="perf-choices"></div>
    <h3 class="step-title"><span>2</span>좌석 등급</h3>
    <div class="choice-list" id="grade-choices"></div>
    <h3 class="step-title"><span>3</span>수량</h3>`;
  const goodsSteps = `
    <h3 class="step-title"><span>1</span>수량</h3>
    <div id="goods-left" class="booking-meta"></div>`;
  const addressStep = !isTicket() && p.deliveryType === "PARCEL" ? `
    <h3 class="step-title"><span>2</span>배송지</h3>
    <div class="field">
      <input id="address" maxlength="255" placeholder="예: 서울시 송파구 올림픽로 424">
    </div>` : "";

  bookingBox.innerHTML = `
    <div class="page-head">
      <a class="back-link" href="/product.html?id=${p.id}">← 상품으로</a>
      <h1>${isTicket() ? "예매하기" : "구매하기"}</h1>
    </div>
    <div class="booking-layout">
      <aside class="panel booking-summary">
        ${poster}
        <div>
          <div class="detail-badges"><span class="badge">${TYPE_LABEL[p.type]}</span>${statusBadge(PRODUCT_STATUS, p.status)}</div>
          <p class="booking-title">${escapeHtml(p.title)}</p>
          ${where}
          <p class="booking-meta">1인 ${isTicket() ? `회차당 ${p.maxPerPerson}매` : `${p.maxPerPerson}개`}까지</p>
        </div>
      </aside>
      <section class="panel booking-main">
        ${isTicket() ? ticketSteps : goodsSteps}
        <div class="stepper">
          <button type="button" id="qty-minus" aria-label="수량 줄이기">−</button>
          <span id="qty">1</span>
          <button type="button" id="qty-plus" aria-label="수량 늘리기">+</button>
        </div>
        <p class="hint" id="qty-hint"></p>
        ${addressStep}
        <div class="booking-total">
          <dl id="picked-list"></dl>
          <div class="booking-sum"><span>구매 금액</span><strong id="total-price">-</strong></div>
        </div>
        <button class="btn" type="button" id="buy-button">구매하기</button>
        <p class="hint buy-hint">결제 기능 없이 바로 구매가 확정됩니다.</p>
        <p class="hint buy-hint" id="admit-left"></p>
        <p class="message" id="buy-message"></p>
      </section>
    </div>`;

  // 회차는 예매 중인 첫 회차를 미리 골라 둔다. 좌석 등급은 직접 고른다.
  if (isTicket()) {
    picked.performanceId = product.performances.find((perf) => perf.status === "OPEN")?.id ?? null;
  } else {
    picked.saleItemId = product.saleItems[0]?.id ?? null;
  }

  bookingBox.addEventListener("click", onClick);
  document.getElementById("address")?.addEventListener("input", updateSummary);
  renderChoices();
}

function onClick(event) {
  const choice = event.target.closest("button.choice");
  if (choice && !choice.disabled) {
    if (choice.dataset.kind === "perf") {
      picked.performanceId = Number(choice.dataset.id);
      picked.saleItemId = null; // 회차를 바꾸면 등급을 다시 고른다
    } else {
      picked.saleItemId = Number(choice.dataset.id);
    }
    showMessage(document.getElementById("buy-message"), "");
    renderChoices();
    return;
  }
  if (event.target.id === "qty-minus") changeQuantity(-1);
  if (event.target.id === "qty-plus") changeQuantity(+1);
  if (event.target.id === "buy-button") buy();
}

// ---------- 고르는 부분 (3초마다 남은 수량만 바뀐다) ----------

function renderChoices() {
  if (isTicket()) {
    renderPerformanceChoices();
    renderGradeChoices();
  } else {
    const item = product.saleItems[0];
    document.getElementById("goods-left").textContent = item
      ? `남은 수량 ${item.remainingQuantity.toLocaleString()} / ${item.totalQuantity.toLocaleString()}`
      : "";
  }
  clampQuantity();
  updateSummary();
}

function renderPerformanceChoices() {
  updateChoices(document.getElementById("perf-choices"), "perf", product.performances.map((perf) => {
    const open = perf.status === "OPEN";
    return {
      id: perf.id,
      title: formatDateTime(perf.eventAt),
      sub: open ? `예매 마감 ${formatShortDateTime(perf.bookingCloseAt)}` : PERFORMANCE_STATUS[perf.status].label,
      disabled: !open,
      selected: perf.id === picked.performanceId,
    };
  }));
}

function renderGradeChoices() {
  const box = document.getElementById("grade-choices");
  if (!picked.performanceId) {
    box.innerHTML = `<p class="hint">예매할 수 있는 회차를 먼저 골라 주세요.</p>`;
    return;
  }
  updateChoices(box, "grade", product.seatGrades
    .map((g) => ({ g, item: itemOf(picked.performanceId, g.id) }))
    .filter(({ item }) => item)
    .map(({ g, item }) => ({
      id: item.id,
      title: `${g.name} · ${won(g.price)}`,
      sub: item.remainingQuantity > 0 ? `남은 좌석 ${item.remainingQuantity.toLocaleString()}` : "매진",
      disabled: item.remainingQuantity === 0,
      selected: item.id === picked.saleItemId,
    })));
}

/**
 * 선택 버튼 목록을 그린다. 같은 버튼들이 이미 있으면 새로 만들지 않고 글자와 상태만 바꾼다.
 * 3초마다 버튼을 통째로 다시 만들면, 그 순간 누른 클릭이 사라지거나 키보드 위치가 풀리기 때문이다.
 */
function updateChoices(box, kind, choices) {
  const buttons = [...box.querySelectorAll("button.choice")];
  const sameButtons = buttons.length === choices.length
    && buttons.every((b, i) => b.dataset.kind === kind && Number(b.dataset.id) === choices[i].id);
  if (!sameButtons) {
    box.innerHTML = choices.map((c) => `
      <button type="button" class="choice" data-kind="${kind}" data-id="${c.id}">
        <strong></strong><span></span>
      </button>`).join("");
  }
  box.querySelectorAll("button.choice").forEach((button, i) => {
    const c = choices[i];
    button.querySelector("strong").textContent = c.title;
    button.querySelector("span").textContent = c.sub;
    button.disabled = c.disabled;
    button.classList.toggle("selected", c.selected);
    button.setAttribute("aria-pressed", String(c.selected));
  });
}

function itemOf(performanceId, seatGradeId) {
  return product.saleItems.find((s) => s.performanceId === performanceId && s.seatGradeId === seatGradeId) ?? null;
}

function pickedItem() {
  return product.saleItems.find((s) => s.id === picked.saleItemId) ?? null;
}

function pickedPerformance() {
  return product.performances.find((perf) => perf.id === picked.performanceId) ?? null;
}

// ---------- 수량 ----------

/** 고를 수 있는 최대 수량: 1인 한도, 남은 수량, 한 번에 100개 중 작은 값 (이전 구매 합산은 서버가 확인) */
function maxQuantity() {
  const item = pickedItem();
  const left = item ? item.remainingQuantity : product.maxPerPerson;
  return Math.max(1, Math.min(product.maxPerPerson, left, MAX_PER_ORDER));
}

function changeQuantity(delta) {
  picked.quantity += delta;
  clampQuantity();
  updateSummary();
}

function clampQuantity() {
  picked.quantity = Math.min(Math.max(picked.quantity, 1), maxQuantity());
  document.getElementById("qty").textContent = picked.quantity;
  document.getElementById("qty-minus").disabled = picked.quantity <= 1;
  document.getElementById("qty-plus").disabled = picked.quantity >= maxQuantity();
  document.getElementById("qty-hint").textContent = isTicket()
    ? `1인 회차당 ${product.maxPerPerson}매까지 (이미 구매한 수량은 구매할 때 함께 확인합니다)`
    : `1인 ${product.maxPerPerson}개까지 (이미 구매한 수량은 구매할 때 함께 확인합니다)`;
}

// ---------- 선택 내역과 구매 버튼 ----------

function updateSummary() {
  const item = pickedItem();
  const perf = pickedPerformance();
  const rows = [];
  if (isTicket()) {
    rows.push(["회차", perf ? formatDateTime(perf.eventAt) : "-"]);
    rows.push(["좌석 등급", item ? escapeHtml(product.seatGrades.find((g) => g.id === item.seatGradeId)?.name ?? "") : "선택해 주세요"]);
  }
  rows.push(["수량", `${picked.quantity}${unit()}`]);
  document.getElementById("picked-list").innerHTML = rows.map(([k, v]) => `<dt>${k}</dt><dd>${v}</dd>`).join("");
  document.getElementById("total-price").textContent = item ? won(item.price * picked.quantity) : "-";

  const button = document.getElementById("buy-button");
  let label = "구매하기";
  if (product.status !== "ON_SALE") label = PRODUCT_STATUS[product.status].label;
  else if (isTicket() && (!perf || perf.status !== "OPEN")) label = "예매할 수 있는 회차를 골라 주세요";
  else if (!item) label = "좌석 등급을 골라 주세요";
  else if (item.remainingQuantity === 0) label = "매진";
  else if (document.getElementById("address") && !document.getElementById("address").value.trim()) label = "배송지를 입력해 주세요";
  button.textContent = label;
  button.disabled = label !== "구매하기";
}

// ---------- 구매 ----------

async function buy() {
  const message = document.getElementById("buy-message");
  const item = pickedItem();
  if (!item) return;
  const perf = pickedPerformance();
  const gradeName = isTicket() ? product.seatGrades.find((g) => g.id === item.seatGradeId)?.name : null;
  const what = isTicket() ? `${formatDateTime(perf.eventAt)} / ${gradeName}` : product.title;
  if (!confirm(`${what}\n${picked.quantity}${unit()} · ${won(item.price * picked.quantity)}\n\n결제 없이 바로 구매가 확정됩니다. 구매할까요?`)) {
    return;
  }

  const button = document.getElementById("buy-button");
  button.disabled = true;
  try {
    const address = document.getElementById("address")?.value.trim() || null;
    const order = await api(`/api/products/${product.id}/orders`, {
      method: "POST",
      body: { saleItemId: item.id, quantity: picked.quantity, address },
    });
    renderDone(order);
  } catch (e) {
    if (e.status === 401) {
      // api() 가 로그인 화면으로 보낸다 (로그인 후 이 화면으로 돌아옴)
      return;
    }
    if (e.code === "NOT_ADMITTED") {
      goQueue();
      return;
    }
    showMessage(message, e.message);
    await reload();
  }
}

/** 주문 완료 화면 */
function renderDone(order) {
  done = true;
  clearInterval(refreshTimer);
  const rows = [
    ["주문번호", order.orderId],
    ["상품", escapeHtml(order.title)],
  ];
  if (order.type === "TICKET") {
    rows.push(["회차", formatDateTime(order.eventAt)]);
    rows.push(["좌석 등급", escapeHtml(order.gradeName ?? "")]);
  }
  rows.push(["수량", `${order.quantity}${order.type === "TICKET" ? "매" : "개"}`]);
  rows.push(["구매 금액", won(order.totalPrice)]);
  if (order.address) rows.push(["배송지", escapeHtml(order.address)]);
  rows.push(["주문 시각", formatDateTime(order.orderedAt)]);

  bookingBox.innerHTML = `
    <div class="panel booking-done">
      <div class="done-icon">✓</div>
      <h1>구매가 확정되었습니다</h1>
      <p class="booking-meta">결제 기능 없이 바로 구매가 확정되었습니다.</p>
      <dl class="spec">${rows.map(([k, v]) => `<dt>${k}</dt><dd>${v}</dd>`).join("")}</dl>
      <div class="done-actions">
        <a class="btn" href="/my-orders.html">구매 내역 보기</a>
        <a class="btn btn-secondary" href="/product.html?id=${order.productId}">상품으로 돌아가기</a>
      </div>
    </div>`;
  window.scrollTo({ top: 0 });
}

// ---------- 불러오기 ----------

async function load() {
  if (!productId) {
    showError("상품을 찾을 수 없습니다.");
    return;
  }
  try {
    const queue = await api(`/api/products/${encodeURIComponent(productId)}/queue/me`);
    if (queue.status !== "ADMITTED") {
      goQueue();
      return;
    }
    product = await api(`/api/products/${encodeURIComponent(productId)}`);
    render();
    if (product.status === "ON_SALE") {
      refreshTimer = setInterval(refresh, REFRESH_MS);
      startAdmitTimer(queue.admittedSeconds);
    }
  } catch (e) {
    showError(e.message);
  }
}

/** 대기열로 돌아가 다시 줄을 선다 (입장하지 않았거나 입장 시간이 끝남) */
function goQueue() {
  location.replace(`/queue.html?id=${encodeURIComponent(productId)}`);
}

/** 입장 후 예매 화면을 쓸 수 있는 남은 시간 표시. 끝나면 대기열로 (대기열을 끈 경우 seconds = null) */
function startAdmitTimer(seconds) {
  if (seconds == null) return;
  const endAt = Date.now() + seconds * 1000;
  let admitTimer = null;
  const tick = () => {
    if (done) return;
    const left = Math.max(0, Math.ceil((endAt - Date.now()) / 1000));
    const box = document.getElementById("admit-left");
    if (box) box.textContent = `예매 가능 시간 ${Math.floor(left / 60)}:${String(left % 60).padStart(2, "0")} 남음`;
    if (left === 0) {
      clearInterval(admitTimer);
      alert("예매 가능 시간이 끝났습니다. 대기열에서 다시 순서를 기다려 주세요.");
      goQueue();
    }
  };
  admitTimer = setInterval(tick, 1000);
  tick();
}

async function refresh() {
  if (document.hidden || refreshing || done) return;
  await reload();
}

/** 남은 수량을 다시 불러온다. 고른 등급이 그사이 매진되면 선택을 풀고 알려 준다 */
async function reload() {
  refreshing = true;
  try {
    product = await api(`/api/products/${encodeURIComponent(productId)}`);
    if (done) return;
    if (product.status !== "ON_SALE") {
      clearInterval(refreshTimer);
      render();
      return;
    }
    const perf = pickedPerformance();
    if (isTicket() && perf && perf.status !== "OPEN") {
      picked.performanceId = null;
      picked.saleItemId = null;
      showMessage(document.getElementById("buy-message"), "고른 회차의 예매가 마감되었습니다. 다른 회차를 골라 주세요.");
    }
    const item = pickedItem();
    if (isTicket() && item && item.remainingQuantity === 0) {
      picked.saleItemId = null;
      showMessage(document.getElementById("buy-message"), "고른 좌석 등급이 매진되었습니다. 다른 등급을 골라 주세요.");
    }
    renderChoices();
  } catch (e) {
    // 잠깐의 연결 오류는 다음 갱신 때 다시 시도한다
  } finally {
    refreshing = false;
  }
}

load();

// 상품 상세: 사진, 기본 정보, 구매, (티켓) 회차별 등급·남은 좌석, 좌석 배치도, 설명
// 남은 수량과 판매 상태는 3초마다 다시 불러온다.
// 구매는 결제 없이 바로 구매 확정된다 (F-04).

const REFRESH_MS = 3000;
const BUY_LABEL = { UPCOMING: "판매 예정", ON_SALE: "구매하기", SOLD_OUT: "매진", ENDED: "판매 종료" };

const productId = new URLSearchParams(location.search).get("id");
const detailBox = document.getElementById("product-detail");
let product = null;
let refreshTimer = null;
let refreshing = false;

function showError(text) {
  detailBox.innerHTML = `<div class="empty">${escapeHtml(text)}</div>`;
}

// ---------- 처음 그리기 ----------

function render() {
  const p = product;
  const isTicket = p.type === "TICKET";
  document.title = `${p.title} - 티켓온라인`;
  document.querySelectorAll(".gnb a").forEach((a) => a.classList.toggle("active", a.href.endsWith(`type=${p.type}`)));

  // 대표 사진 + (굿즈) 부가 사진
  const gallery = [p.imageUrl, ...(p.images ?? [])].filter(Boolean);
  const media = gallery.length
    ? `<img class="detail-poster" id="main-image" src="${gallery[0]}" alt="">`
    : `<div class="detail-poster poster-empty">${TYPE_LABEL[p.type]}</div>`;
  const thumbs = gallery.length > 1
    ? `<div class="thumbs">${gallery.map((url, i) =>
        `<img src="${url}" alt="" class="${i === 0 ? "active" : ""}">`).join("")}</div>`
    : "";

  detailBox.innerHTML = `
    <div class="detail-top">
      <div class="detail-media">${media}${thumbs}</div>
      <div class="detail-info">
        <div class="detail-badges"><span class="badge">${TYPE_LABEL[p.type]}</span><span id="status-badge"></span></div>
        <h1 class="detail-title">${escapeHtml(p.title)}</h1>
        <dl class="spec">${renderSpec(p)}</dl>
        <div id="goods-stock"></div>
        <div id="buy-box"></div>
      </div>
    </div>
    ${isTicket ? `
      <section class="detail-section">
        <h2>회차별 좌석</h2>
        <div id="performance-list"></div>
      </section>` : ""}
    ${isTicket && p.seatMapUrl ? `
      <section class="detail-section">
        <h2>좌석 배치도</h2>
        <a href="${p.seatMapUrl}" target="_blank" rel="noopener"><img class="seat-map" src="${p.seatMapUrl}" alt="좌석 배치도"></a>
      </section>` : ""}
    ${p.description ? `
      <section class="detail-section">
        <h2>${isTicket ? "공연 소개" : "상품 설명"}</h2>
        <p class="description">${escapeHtml(p.description)}</p>
      </section>` : ""}`;

  bindGallery();
  renderBuyBox();
  renderStock();
}

/** 기본 정보 표 */
function renderSpec(p) {
  const rows = [];
  if (p.type === "TICKET") {
    const days = p.performances.map((perf) => perf.eventAt);
    rows.push(["장소", escapeHtml(p.venue ?? "")]);
    rows.push(["공연 기간", formatPeriod(days[0], days[days.length - 1])]);
    rows.push(["가격", p.seatGrades.map((g) => `${escapeHtml(g.name)} ${won(g.price)}`).join(" · ")]);
    rows.push(["1인 구매 제한", `회차당 ${p.maxPerPerson}매`]);
    rows.push(["예매 마감", p.closeBeforeHours > 0 ? `공연 시작 ${p.closeBeforeHours}시간 전` : "공연 시작 시각"]);
  } else {
    rows.push(["가격", p.saleItems[0] ? won(p.saleItems[0].price) : ""]);
    rows.push(["수령 방법", DELIVERY_LABEL[p.deliveryType] ?? ""]);
    rows.push(["1인 구매 제한", `${p.maxPerPerson}개`]);
  }
  rows.push(["판매 기간", `${formatDateTime(p.saleStartAt)} ~ ${formatDateTime(p.saleEndAt)}`]);
  return rows.map(([key, value]) => `<dt>${key}</dt><dd>${value}</dd>`).join("");
}

/** 썸네일을 누르면 큰 사진을 바꾼다 */
function bindGallery() {
  const main = document.getElementById("main-image");
  const thumbs = document.querySelectorAll(".thumbs img");
  thumbs.forEach((thumb) => thumb.addEventListener("click", () => {
    main.src = thumb.src;
    thumbs.forEach((t) => t.classList.toggle("active", t === thumb));
  }));
}

// ---------- 3초마다 바뀌는 부분 (상태, 남은 수량) ----------

function renderStock() {
  const p = product;
  document.getElementById("status-badge").innerHTML = statusBadge(PRODUCT_STATUS, p.status);
  updateBuyOptions();
  if (p.type === "GOODS") {
    const item = p.saleItems[0];
    document.getElementById("goods-stock").innerHTML = item ? stockBar(item.remainingQuantity, item.totalQuantity) : "";
  } else {
    document.getElementById("performance-list").innerHTML = p.performances.map(renderPerformance).join("");
  }
}

function stockBar(remaining, total) {
  const percent = total > 0 ? Math.round((remaining / total) * 100) : 0;
  return `
    <div class="stock">
      <div class="stock-text"><span>남은 수량</span><strong>${remaining.toLocaleString()} / ${total.toLocaleString()}</strong></div>
      <div class="stock-bar"><span style="width:${percent}%"></span></div>
    </div>`;
}

/** 회차 하나: 일시, 예매 상태, 예매 마감 시각, 등급별 가격·남은 좌석 */
function renderPerformance(perf) {
  const grades = product.seatGrades.map((g) => {
    const item = product.saleItems.find((s) => s.performanceId === perf.id && s.seatGradeId === g.id);
    const remaining = item ? item.remainingQuantity : 0;
    const total = item ? item.totalQuantity : g.seatCount;
    const left = remaining === 0
      ? `<span class="grade-left none">매진</span>`
      : `<span class="grade-left">남은 좌석 ${remaining.toLocaleString()} / ${total.toLocaleString()}</span>`;
    return `
      <li class="grade-row">
        <span class="grade-name">${escapeHtml(g.name)}</span>
        <span class="grade-price">${won(g.price)}</span>
        ${left}
      </li>`;
  }).join("");
  return `
    <article class="performance${perf.status === "CLOSED" ? " is-closed" : ""}">
      <div class="performance-head">
        <strong>${formatDateTime(perf.eventAt)}</strong>
        ${statusBadge(PERFORMANCE_STATUS, perf.status)}
        <span class="close-at">예매 마감 ${formatDateTime(perf.bookingCloseAt)}</span>
      </div>
      <ul class="grade-list">${grades}</ul>
    </article>`;
}

// ---------- 구매 (결제 없이 바로 확정) ----------

/** 구매 입력칸: 처음 한 번만 그린다 (3초 갱신 때 고른 값이 지워지지 않게) */
function renderBuyBox() {
  const p = product;
  const isTicket = p.type === "TICKET";
  const ticketFields = isTicket ? `
    <div class="field">
      <label for="perf-select">공연 회차</label>
      <select id="perf-select"></select>
    </div>
    <div class="field">
      <label for="grade-select">좌석 등급</label>
      <select id="grade-select"></select>
    </div>` : "";
  const addressField = !isTicket && p.deliveryType === "PARCEL" ? `
    <div class="field">
      <label for="address">배송 주소</label>
      <input id="address" maxlength="255" placeholder="예: 서울시 송파구 올림픽로 424">
    </div>` : "";
  document.getElementById("buy-box").innerHTML = `
    <form id="buy-form" class="buy-form" novalidate>
      ${ticketFields}
      <div class="field">
        <label for="quantity">수량</label>
        <input id="quantity" type="number" min="1" max="${p.maxPerPerson}" value="1">
        <p class="hint">${isTicket ? `1인 회차당 ${p.maxPerPerson}매까지` : `1인 ${p.maxPerPerson}개까지`}</p>
      </div>
      ${addressField}
      <div class="buy-total"><span>구매 금액</span><strong id="total-price"></strong></div>
      <button class="btn" type="submit" id="buy-button"></button>
      <p class="hint buy-hint">결제 기능 없이 바로 구매가 확정됩니다.</p>
      <p class="message" id="buy-message"></p>
    </form>`;

  const form = document.getElementById("buy-form");
  form.addEventListener("change", (event) => {
    if (event.target.id === "perf-select") fillGradeOptions();
    updateBuyState();
  });
  form.addEventListener("input", updateBuyState);
  form.addEventListener("submit", (event) => {
    event.preventDefault();
    buy();
  });
}

/** 회차·등급 목록과 남은 수량을 최신 값으로 바꾼다. 고른 값은 그대로 둔다 */
function updateBuyOptions() {
  if (product.type === "TICKET") {
    const select = document.getElementById("perf-select");
    const selected = select.value;
    select.innerHTML = product.performances.map((perf) => `
      <option value="${perf.id}" ${perf.status === "OPEN" ? "" : "disabled"}>
        ${formatDateTime(perf.eventAt)} · ${PERFORMANCE_STATUS[perf.status].label}
      </option>`).join("");
    const open = product.performances.find((perf) => perf.status === "OPEN");
    select.value = isSelectable(select, selected) ? selected : (open ? String(open.id) : String(product.performances[0]?.id ?? ""));
    fillGradeOptions();
  }
  updateBuyState();
}

function isSelectable(select, value) {
  return [...select.options].some((o) => o.value === value && !o.disabled);
}

function fillGradeOptions() {
  const perfId = Number(document.getElementById("perf-select").value);
  const select = document.getElementById("grade-select");
  const selected = select.value;
  const items = product.saleItems.filter((s) => s.performanceId === perfId);
  select.innerHTML = product.seatGrades.map((g) => {
    const item = items.find((s) => s.seatGradeId === g.id);
    if (!item) return "";
    const left = item.remainingQuantity === 0 ? "매진" : `남은 좌석 ${item.remainingQuantity.toLocaleString()}`;
    return `<option value="${item.id}" ${item.remainingQuantity === 0 ? "disabled" : ""}>
      ${escapeHtml(g.name)} ${won(g.price)} · ${left}</option>`;
  }).join("");
  const firstLeft = [...select.options].find((o) => !o.disabled);
  select.value = isSelectable(select, selected) ? selected : (firstLeft?.value ?? select.options[0]?.value ?? "");
}

/** 지금 고른 판매 단위 (티켓: 회차 x 등급, 굿즈: 1개) */
function selectedItem() {
  if (product.type === "TICKET") {
    const id = Number(document.getElementById("grade-select").value);
    return product.saleItems.find((s) => s.id === id) ?? null;
  }
  return product.saleItems[0] ?? null;
}

/** 금액과 버튼 상태. 최종 판단(한도·재고·마감)은 서버가 한다 */
function updateBuyState() {
  const item = selectedItem();
  const quantity = Number(document.getElementById("quantity").value) || 0;
  document.getElementById("total-price").textContent = item && quantity > 0 ? won(item.price * quantity) : "-";

  const button = document.getElementById("buy-button");
  let label = "구매하기";
  if (product.status !== "ON_SALE") {
    label = BUY_LABEL[product.status];
  } else if (product.type === "TICKET") {
    const perf = product.performances.find((x) => x.id === Number(document.getElementById("perf-select").value));
    if (!perf || perf.status !== "OPEN") label = "예매할 수 없는 회차";
    else if (!item || item.remainingQuantity === 0) label = "매진";
  } else if (!item || item.remainingQuantity === 0) {
    label = "매진";
  }
  button.textContent = label;
  button.disabled = label !== "구매하기";
}

async function buy() {
  const message = document.getElementById("buy-message");
  if (!auth.token()) {
    // 로그인 후 이 상품으로 돌아온다
    location.href = `/login.html?redirect=${encodeURIComponent(location.pathname + location.search)}`;
    return;
  }
  const item = selectedItem();
  const quantity = Number(document.getElementById("quantity").value);
  if (!item || !Number.isInteger(quantity) || quantity < 1) {
    showMessage(message, "구매할 항목과 수량을 확인해 주세요.");
    return;
  }
  const what = product.type === "TICKET"
    ? `${document.getElementById("perf-select").selectedOptions[0].textContent.trim().split(" · ")[0]} / ${
        product.seatGrades.find((g) => g.id === item.seatGradeId)?.name ?? ""}`
    : product.title;
  if (!confirm(`${what}\n${quantity}${product.type === "TICKET" ? "매" : "개"} · ${won(item.price * quantity)}\n\n결제 없이 바로 구매가 확정됩니다. 구매할까요?`)) {
    return;
  }

  const button = document.getElementById("buy-button");
  button.disabled = true;
  try {
    const address = document.getElementById("address")?.value.trim() || null;
    await api(`/api/products/${product.id}/orders`, {
      method: "POST",
      body: { saleItemId: item.id, quantity, address },
    });
    message.className = "message success";
    message.innerHTML = `구매가 확정되었습니다. <a href="/my-orders.html">구매 내역 보기</a>`;
  } catch (e) {
    if (e.status === 401) {
      location.href = `/login.html?redirect=${encodeURIComponent(location.pathname + location.search)}`;
      return;
    }
    showMessage(message, e.message);
  }
  await reloadStock();
}

// ---------- 불러오기 ----------

async function load() {
  if (!productId) {
    showError("상품을 찾을 수 없습니다.");
    return;
  }
  try {
    product = await api(`/api/products/${encodeURIComponent(productId)}`);
    render();
    if (product.status !== "ENDED") {
      refreshTimer = setInterval(refresh, REFRESH_MS);
    }
  } catch (e) {
    showError(e.message);
  }
}

async function refresh() {
  // 다른 탭을 보고 있거나 앞의 요청이 아직 끝나지 않았으면 건너뛴다
  if (document.hidden || refreshing) return;
  await reloadStock();
}

/** 남은 수량과 상태를 다시 불러와 바뀌는 부분만 다시 그린다 */
async function reloadStock() {
  refreshing = true;
  try {
    product = await api(`/api/products/${encodeURIComponent(productId)}`);
    renderStock();
    if (product.status === "ENDED") clearInterval(refreshTimer);
  } catch (e) {
    // 삭제된 상품이면 멈추고, 잠깐의 연결 오류는 다음 갱신 때 다시 시도한다
    if (e.status === 404) {
      clearInterval(refreshTimer);
      showError(e.message);
    }
  } finally {
    refreshing = false;
  }
}

load();

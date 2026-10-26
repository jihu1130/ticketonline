// 상품 상세: 사진, 기본 정보, (티켓) 회차별 등급·남은 좌석, 좌석 배치도, 설명
// 남은 수량과 판매 상태는 3초마다 다시 불러온다.
// 구매는 "예매하기/구매하기" 버튼 -> 대기열(queue.html) -> 입장 후 예매 화면(booking.html)에서 한다.

const REFRESH_MS = 3000;
const CLOSED_LABEL = { UPCOMING: "판매 예정", SOLD_OUT: "매진", ENDED: "판매 종료" };

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
        <button class="btn" type="button" id="book-button"></button>
        <p class="hint buy-hint">${isTicket ? "회차와 좌석 등급은 예매 화면에서 고릅니다." : "수량과 배송지는 구매 화면에서 입력합니다."}</p>
        <button class="btn btn-secondary inquiry-toggle" type="button" id="inquiry-button">판매자에게 문의</button>
        <div class="inquiry-form" id="inquiry-form" hidden>
          <textarea maxlength="1000" placeholder="문의 내용을 입력해 주세요. 답장은 메시지함의 받은 쪽지로 옵니다."></textarea>
          <button class="btn-small primary" type="button" id="inquiry-send">문의 보내기</button>
          <p class="message" id="inquiry-message"></p>
        </div>
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
  document.getElementById("book-button").addEventListener("click", goBooking);
  bindInquiry();
  renderStock();
}

/** 판매자에게 문의: 로그인하지 않았으면 로그인 후 이 화면으로 돌아온다 */
function bindInquiry() {
  const form = document.getElementById("inquiry-form");
  const textarea = form.querySelector("textarea");
  const message = document.getElementById("inquiry-message");
  document.getElementById("inquiry-button").addEventListener("click", () => {
    if (!auth.token()) {
      location.href = `/login.html?redirect=${encodeURIComponent(location.pathname + location.search)}`;
      return;
    }
    form.hidden = !form.hidden;
    if (!form.hidden) textarea.focus();
  });
  document.getElementById("inquiry-send").addEventListener("click", async () => {
    const content = textarea.value.trim();
    if (!content) {
      showMessage(message, "문의 내용을 입력해 주세요.");
      return;
    }
    try {
      await api("/api/messages", { method: "POST", body: { productId: product.id, content } });
      textarea.value = "";
      showMessage(message, "문의를 보냈습니다. 메시지함의 보낸 쪽지에서 확인할 수 있습니다.", "success");
    } catch (e) {
      showMessage(message, e.message);
    }
  });
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

/** 대기열을 거쳐 예매 화면으로. 로그인하지 않았으면 로그인한 뒤 대기열로 돌아온다 */
function goBooking() {
  const target = `/queue.html?id=${product.id}`;
  location.href = auth.token() ? target : `/login.html?redirect=${encodeURIComponent(target)}`;
}

// ---------- 3초마다 바뀌는 부분 (상태, 남은 수량) ----------

function renderStock() {
  const p = product;
  document.getElementById("status-badge").innerHTML = statusBadge(PRODUCT_STATUS, p.status);
  renderBookButton();
  if (p.type === "GOODS") {
    const item = p.saleItems[0];
    document.getElementById("goods-stock").innerHTML = item ? stockBar(item.remainingQuantity, item.totalQuantity) : "";
  } else {
    document.getElementById("performance-list").innerHTML = p.performances.map(renderPerformance).join("");
  }
}

/** 판매 중일 때만 누를 수 있다. 아니면 상태를 버튼에 보여 준다 */
function renderBookButton() {
  const button = document.getElementById("book-button");
  const onSale = product.status === "ON_SALE";
  button.textContent = onSale ? (product.type === "TICKET" ? "예매하기" : "구매하기") : CLOSED_LABEL[product.status];
  button.disabled = !onSale;
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

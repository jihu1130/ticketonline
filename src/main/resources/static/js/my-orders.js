// 내 구매 내역 (F-05): 최근 구매 순
// 기한(티켓은 회차 예매 마감, 굿즈는 판매 종료) 전까지 취소·수량 줄이기·배송지 변경·회차/등급 바꾸기를 할 수 있다.

const ORDER_STATUS = { PENDING: "결제 대기", SUCCESS: "구매 확정", CANCELED: "취소" };
const orderBox = document.getElementById("order-list");
const pageMessage = document.getElementById("page-message");
let orders = [];

if (!auth.token()) {
  location.href = `/login.html?redirect=${encodeURIComponent("/my-orders.html")}`;
}

const unitOf = (o) => (o.type === "TICKET" ? "매" : "개");
const changeable = (o) => o.status === "SUCCESS" && o.changeableUntil && new Date(o.changeableUntil) > new Date();

function renderOrder(o) {
  const thumb = o.imageUrl
    ? `<img class="order-thumb" src="${o.imageUrl}" alt="">`
    : `<div class="order-thumb poster-empty">${TYPE_LABEL[o.type]}</div>`;
  const option = o.type === "TICKET"
    ? `${formatDateTime(o.eventAt)} · ${escapeHtml(o.gradeName ?? "")}`
    : (o.address ? `배송지 ${escapeHtml(o.address)}` : "현장 수령");
  return `
    <article class="order-item" data-id="${o.orderId}">
      <a href="/product.html?id=${o.productId}">${thumb}</a>
      <div class="order-body">
        <div class="order-head">
          <span class="badge">${TYPE_LABEL[o.type]}</span>
          <span class="order-status ${o.status.toLowerCase()}">${ORDER_STATUS[o.status] ?? o.status}</span>
        </div>
        <a class="order-title" href="/product.html?id=${o.productId}">${escapeHtml(o.title)}</a>
        <p class="order-meta">${option}</p>
        <p class="order-meta">${won(o.unitPrice)} × ${o.quantity}${unitOf(o)}</p>
        <p class="order-meta muted">주문 ${formatDateTime(o.orderedAt)} · 주문번호 ${o.orderId}</p>
      </div>
      <strong class="order-price">${won(o.totalPrice)}</strong>
      ${renderActions(o)}
    </article>`;
}

/** 취소·변경 버튼. 기한이 지났거나 취소된 주문은 버튼 없이 안내만 */
function renderActions(o) {
  if (o.status !== "SUCCESS") return "";
  if (!changeable(o)) {
    return `<div class="order-actions"><span class="order-deadline">취소·변경 기간이 지났습니다.</span></div>`;
  }
  const buttons = [`<button type="button" class="btn-small danger" data-action="cancel">주문 취소</button>`];
  if (o.quantity > 1) buttons.push(`<button type="button" class="btn-small" data-action="quantity">수량 줄이기</button>`);
  if (o.address) buttons.push(`<button type="button" class="btn-small" data-action="address">배송지 변경</button>`);
  if (o.type === "TICKET") buttons.push(`<button type="button" class="btn-small" data-action="item">회차·등급 변경</button>`);
  return `
    <div class="order-actions">
      <span class="order-deadline">${formatDateTime(o.changeableUntil)}까지 취소·변경 가능</span>
      <div class="order-buttons">${buttons.join("")}</div>
      <div class="order-edit" hidden></div>
    </div>`;
}

// ---------- 버튼 ----------

orderBox.addEventListener("click", async (event) => {
  const button = event.target.closest("button[data-action]");
  if (!button) return;
  const article = button.closest(".order-item");
  const order = orders.find((o) => String(o.orderId) === article.dataset.id);
  const edit = article.querySelector(".order-edit");
  const action = button.dataset.action;

  if (action === "close") {
    edit.hidden = true;
    edit.innerHTML = "";
    return;
  }
  // 취소·저장은 요청이 끝날 때까지 버튼을 잠가 두 번 보내지 않게 한다
  const requests = {
    cancel: () => cancelOrder(order),
    "save-quantity": () => save(order, edit, "quantity", { quantity: Number(edit.querySelector("select").value) }),
    "save-address": () => save(order, edit, "address", { address: edit.querySelector("input").value.trim() }),
    "save-item": () => save(order, edit, "item", { saleItemId: Number(edit.querySelector("#item-grade").value) }),
  };
  if (requests[action]) {
    button.disabled = true;
    try {
      await requests[action]();
    } finally {
      button.disabled = false; // 성공하면 목록을 다시 그려 이 버튼은 사라진다
    }
    return;
  }

  edit.hidden = false;
  if (action === "quantity") edit.innerHTML = quantityForm(order);
  if (action === "address") edit.innerHTML = addressForm(order);
  if (action === "item") await openItemForm(order, edit);
});

async function cancelOrder(order) {
  const what = order.type === "TICKET" ? `${formatDateTime(order.eventAt)} ${order.gradeName}` : order.title;
  if (!confirm(`${what} ${order.quantity}${unitOf(order)} 주문을 취소할까요?\n취소하면 되돌릴 수 없습니다.`)) return;
  try {
    await api(`/api/members/me/orders/${order.orderId}/cancel`, { method: "POST" });
    await loadOrders(`주문번호 ${order.orderId}을(를) 취소했습니다.`);
  } catch (e) {
    handleError(e, pageMessage);
  }
}

/** 수량 줄이기: 1 ~ (지금 수량 - 1) */
function quantityForm(order) {
  const options = Array.from({ length: order.quantity - 1 }, (_, i) => i + 1)
    .map((n) => `<option value="${n}">${n}${unitOf(order)} (${won(order.unitPrice * n)})</option>`).join("");
  return `
    <label class="edit-label">바꿀 수량 <span class="hint">줄인 만큼 취소됩니다</span></label>
    <div class="edit-row">
      <select>${options}</select>
      ${editButtons("save-quantity")}
    </div>
    <p class="message"></p>`;
}

function addressForm(order) {
  return `
    <label class="edit-label">새 배송지</label>
    <div class="edit-row">
      <input maxlength="255" value="${escapeHtml(order.address ?? "")}">
      ${editButtons("save-address")}
    </div>
    <p class="message"></p>`;
}

/** 회차·등급 바꾸기: 예매 중인 회차와 남은 좌석을 불러와 고른다 (같은 수량 그대로) */
async function openItemForm(order, edit) {
  edit.innerHTML = `<p class="loading">회차 정보를 불러오는 중입니다...</p>`;
  let product;
  try {
    product = await api(`/api/products/${order.productId}`);
  } catch (e) {
    edit.innerHTML = `<p class="message">${escapeHtml(e.message)}</p>`;
    return;
  }
  const performances = product.performances.filter((p) => p.status === "OPEN" || p.id === order.performanceId);
  const perfOptions = performances.map((p) =>
    `<option value="${p.id}" ${p.id === order.performanceId ? "selected" : ""}>${formatDateTime(p.eventAt)}</option>`).join("");
  edit.innerHTML = `
    <label class="edit-label">바꿀 회차와 좌석 등급 <span class="hint">${order.quantity}매 그대로 옮깁니다</span></label>
    <div class="edit-row">
      <select id="item-perf">${perfOptions}</select>
      <select id="item-grade"></select>
      ${editButtons("save-item")}
    </div>
    <p class="message"></p>`;
  const perfSelect = edit.querySelector("#item-perf");
  const fillGrades = () => {
    const performanceId = Number(perfSelect.value);
    edit.querySelector("#item-grade").innerHTML = product.saleItems
      .filter((item) => item.performanceId === performanceId)
      .map((item) => {
        const grade = product.seatGrades.find((g) => g.id === item.seatGradeId);
        const current = item.id === order.saleItemId;
        const enough = item.remainingQuantity >= order.quantity;
        const label = current ? "지금 등급" : (enough ? `남은 좌석 ${item.remainingQuantity}` : "좌석 부족");
        return `<option value="${item.id}" ${current || !enough ? "disabled" : ""}>${escapeHtml(grade?.name ?? "")} · ${won(item.price)} (${label})</option>`;
      }).join("");
    const first = edit.querySelector("#item-grade option:not([disabled])");
    if (first) first.selected = true;
  };
  perfSelect.addEventListener("change", fillGrades);
  fillGrades();
}

function editButtons(saveAction) {
  return `
    <button type="button" class="btn-small primary" data-action="${saveAction}">저장</button>
    <button type="button" class="btn-small" data-action="close">닫기</button>`;
}

async function save(order, edit, kind, body) {
  const message = edit.querySelector(".message");
  if (kind === "item" && !body.saleItemId) {
    showMessage(message, "바꿀 좌석 등급을 골라 주세요.");
    return;
  }
  try {
    await api(`/api/members/me/orders/${order.orderId}/${kind}`, { method: "PATCH", body });
    const done = { quantity: "수량을 바꿨습니다.", address: "배송지를 바꿨습니다.", item: "회차·좌석 등급을 바꿨습니다." };
    await loadOrders(`주문번호 ${order.orderId}: ${done[kind]}`);
  } catch (e) {
    handleError(e, message);
  }
}

function handleError(e, element) {
  if (e.status === 401) {
    // api() 가 로그인 화면으로 보낸다 (로그인 후 이 화면으로 돌아옴)
    return;
  }
  showMessage(element, e.message);
}

// ---------- 불러오기 ----------

async function loadOrders(doneMessage) {
  try {
    orders = await api("/api/members/me/orders");
    orderBox.innerHTML = orders.length === 0
      ? `<div class="empty">아직 구매한 상품이 없습니다.</div>`
      : `<div class="order-list">${orders.map(renderOrder).join("")}</div>`;
    showMessage(pageMessage, doneMessage ?? "", "success");
  } catch (e) {
    if (e.status === 401) {
      // api() 가 로그인 화면으로 보낸다 (로그인 후 이 화면으로 돌아옴)
      return;
    }
    orderBox.innerHTML = `<div class="empty">${escapeHtml(e.message)}</div>`;
  }
}

loadOrders();

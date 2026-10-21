// 내 구매 내역 (F-05): 최근 구매 순

const ORDER_STATUS = { PENDING: "결제 대기", SUCCESS: "구매 확정", CANCELED: "취소" };
const orderBox = document.getElementById("order-list");

if (!auth.token()) {
  location.href = `/login.html?redirect=${encodeURIComponent("/my-orders.html")}`;
}

function renderOrder(o) {
  const thumb = o.imageUrl
    ? `<img class="order-thumb" src="${o.imageUrl}" alt="">`
    : `<div class="order-thumb poster-empty">${TYPE_LABEL[o.type]}</div>`;
  const option = o.type === "TICKET"
    ? `${formatDateTime(o.eventAt)} · ${escapeHtml(o.gradeName ?? "")}`
    : (o.address ? `배송지 ${escapeHtml(o.address)}` : "현장 수령");
  const unit = o.type === "TICKET" ? "매" : "개";
  return `
    <article class="order-item">
      <a href="/product.html?id=${o.productId}">${thumb}</a>
      <div class="order-body">
        <div class="order-head">
          <span class="badge">${TYPE_LABEL[o.type]}</span>
          <span class="order-status ${o.status.toLowerCase()}">${ORDER_STATUS[o.status] ?? o.status}</span>
        </div>
        <a class="order-title" href="/product.html?id=${o.productId}">${escapeHtml(o.title)}</a>
        <p class="order-meta">${option}</p>
        <p class="order-meta">${won(o.unitPrice)} × ${o.quantity}${unit}</p>
        <p class="order-meta muted">주문 ${formatDateTime(o.orderedAt)} · 주문번호 ${o.orderId}</p>
      </div>
      <strong class="order-price">${won(o.totalPrice)}</strong>
    </article>`;
}

async function loadOrders() {
  try {
    const orders = await api("/api/members/me/orders");
    orderBox.innerHTML = orders.length === 0
      ? `<div class="empty">아직 구매한 상품이 없습니다.</div>`
      : `<div class="order-list">${orders.map(renderOrder).join("")}</div>`;
  } catch (e) {
    if (e.status === 401) {
      location.href = `/login.html?redirect=${encodeURIComponent("/my-orders.html")}`;
      return;
    }
    orderBox.innerHTML = `<div class="empty">${escapeHtml(e.message)}</div>`;
  }
}

loadOrders();

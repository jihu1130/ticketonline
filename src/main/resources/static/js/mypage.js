// 마이페이지: 내 정보, 안 읽은 알림·채팅 수, 최근 주문 3건, 바로가기

const box = document.getElementById("mypage");
const ORDER_STATUS = { PENDING: "결제 대기", SUCCESS: "구매 확정", CANCELED: "취소" };

if (!auth.token()) {
  location.href = `/login.html?redirect=${encodeURIComponent("/mypage.html")}`;
}

async function load() {
  try {
    const [me, orders, unread] = await Promise.all([
      api("/api/members/me"),
      api("/api/members/me/orders"),
      api("/api/members/me/messages/unread-count"),
    ]);
    render(me, orders, unread);
  } catch (e) {
    if (e.status !== 401) box.innerHTML = `<div class="empty">${escapeHtml(e.message)}</div>`;
  }
}

function render(me, orders, unread) {
  const active = orders.filter((o) => o.status === "SUCCESS");
  const recent = orders.slice(0, 3);
  box.innerHTML = `
    <div class="mypage-grid">
      <section class="panel mypage-profile">
        <div class="avatar">${escapeHtml(me.name.slice(0, 1))}</div>
        <div>
          <p class="mypage-name">${escapeHtml(me.name)}님</p>
          <p class="booking-meta">${escapeHtml(me.email)}</p>
          <p class="booking-meta">가입일 ${me.createdAt ? formatDateTime(me.createdAt).slice(0, 10) : "-"}</p>
        </div>
      </section>
      <section class="mypage-stats">
        <a class="stat-card" href="/my-orders.html"><span>구매 확정</span><strong>${active.length}</strong><em>건</em></a>
        <a class="stat-card" href="/messages.html"><span>안 읽은 알림</span><strong>${unread.notice}</strong><em>건</em></a>
        <a class="stat-card" href="/messages.html?tab=chat"><span>안 읽은 채팅</span><strong>${unread.chat}</strong><em>건</em></a>
      </section>
    </div>

    <section class="panel mypage-section">
      <div class="section-head"><h2>최근 주문</h2><a class="link" href="/my-orders.html">전체 보기 →</a></div>
      ${recent.length === 0
        ? `<p class="booking-meta">아직 구매한 상품이 없습니다. <a class="link" href="/">상품 보러 가기</a></p>`
        : `<ul class="recent-orders">${recent.map(renderOrder).join("")}</ul>`}
    </section>

    <section class="mypage-links">
      <a class="link-card" href="/my-orders.html"><strong>구매 내역</strong><span>주문 취소·수량·배송지·회차 변경</span></a>
      <a class="link-card" href="/messages.html"><strong>메시지함</strong><span>알림과 판매자 문의 채팅</span></a>
      <a class="link-card" href="/seller.html"><strong>판매 관리</strong><span>상품 등록과 판매 현황</span></a>
    </section>`;
}

function renderOrder(o) {
  const option = o.type === "TICKET" ? `${formatDateTime(o.eventAt)} · ${escapeHtml(o.gradeName ?? "")}` : TYPE_LABEL[o.type];
  return `
    <li>
      <a href="/product.html?id=${o.productId}">${escapeHtml(o.title)}</a>
      <span class="booking-meta">${option} · ${o.quantity}${o.type === "TICKET" ? "매" : "개"}</span>
      <span class="order-status ${o.status.toLowerCase()}">${ORDER_STATUS[o.status] ?? o.status}</span>
    </li>`;
}

load();

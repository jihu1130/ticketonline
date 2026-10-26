// 메시지함: 알림 탭과 채팅 탭
// 채팅: 왼쪽 대화 목록 + 오른쪽 대화창. 열린 방은 2초마다 "마지막으로 받은 번호 이후" 메시지만, 목록은 10초마다 다시 가져온다.
// 휴대폰(좁은 화면)에서는 목록과 대화창을 번갈아 전체 화면으로 보여 준다.

const ROOM_POLL_MS = 2000;
const LIST_POLL_MS = 10000;

const tabs = document.getElementById("message-tabs");
const pageMessage = document.getElementById("page-message");
const readAllButton = document.getElementById("read-all");
const noticePane = document.getElementById("notice-pane");
const chatPane = document.getElementById("chat-pane");
const chatList = document.getElementById("chat-list");
const chatRoom = document.getElementById("chat-room");

const params = new URLSearchParams(location.search);
let tab = params.get("tab") === "chat" || params.get("room") ? "chat" : "notice";
let rooms = [];
let openRoomId = params.get("room") ? Number(params.get("room")) : null;
let lastMessageId = null; // 열린 방에서 마지막으로 받은 메시지 번호
let roomTimer = null;
let listTimer = null;
let polling = false;
let sending = false;

if (!auth.token()) {
  location.href = `/login.html?redirect=${encodeURIComponent(location.pathname + location.search)}`;
}

// ---------- 탭 ----------

function showTab(name) {
  tab = name;
  tabs.querySelectorAll(".tab").forEach((t) => t.classList.toggle("active", t.dataset.tab === name));
  noticePane.hidden = name !== "notice";
  chatPane.hidden = name !== "chat";
  readAllButton.hidden = name !== "notice";
  showMessage(pageMessage, "");
  clearInterval(listTimer);
  if (name === "notice") {
    stopRoom();
    loadNotices();
  } else {
    loadRooms().then(() => { if (openRoomId) openRoom(openRoomId); });
    listTimer = setInterval(loadRooms, LIST_POLL_MS);
  }
  updateUrl();
}

function updateUrl() {
  const query = tab === "chat" ? (openRoomId ? `?tab=chat&room=${openRoomId}` : "?tab=chat") : "";
  history.replaceState(null, "", location.pathname + query);
}

tabs.addEventListener("click", (event) => {
  const button = event.target.closest(".tab");
  if (button && button.dataset.tab !== tab) showTab(button.dataset.tab);
});

/** 탭 숫자와 헤더 숫자를 함께 바꾼다 */
async function refreshCounts() {
  const count = await refreshUnreadCount();
  if (!count) return;
  const set = (name, n) => {
    const badge = tabs.querySelector(`[data-tab="${name}"] .nav-count`);
    badge.textContent = n > 99 ? "99+" : String(n);
    badge.hidden = n === 0;
  };
  set("notice", count.notice);
  set("chat", count.chat);
}

// ---------- 알림 ----------

async function loadNotices() {
  try {
    const notices = await api("/api/members/me/messages");
    noticePane.innerHTML = notices.length === 0
      ? `<div class="empty">받은 알림이 없습니다.</div>`
      : `<div class="message-list">${notices.map(renderNotice).join("")}</div>`;
    refreshCounts();
  } catch (e) {
    handleError(e, noticePane);
  }
}

function renderNotice(n) {
  const product = n.productId
    ? `<a class="message-product" href="/product.html?id=${n.productId}">${escapeHtml(n.productTitle ?? "")}</a>`
    : "";
  return `
    <article class="message-item ${n.read ? "" : "unread"}" data-id="${n.id}">
      <div class="message-head">
        ${n.read ? "" : `<span class="unread-dot" aria-label="안 읽음"></span>`}
        <strong>티켓온라인</strong>
        ${product}
        <span class="message-time">${formatDateTime(n.createdAt)}</span>
      </div>
      <p class="message-content">${escapeHtml(n.content)}</p>
    </article>`;
}

noticePane.addEventListener("click", async (event) => {
  const item = event.target.closest(".message-item");
  if (!item || event.target.closest("a") || !item.classList.contains("unread")) return;
  try {
    await api(`/api/members/me/messages/${item.dataset.id}/read`, { method: "POST" });
    item.classList.remove("unread");
    item.querySelector(".unread-dot")?.remove();
    refreshCounts();
  } catch (e) {
    // 읽음 표시는 다음에 다시 시도된다
  }
});

readAllButton.addEventListener("click", async () => {
  try {
    await api("/api/members/me/messages/read-all", { method: "POST" });
    await loadNotices();
  } catch (e) {
    showMessage(pageMessage, e.message);
  }
});

// ---------- 채팅 목록 ----------

async function loadRooms() {
  try {
    rooms = await api("/api/chats");
    renderRooms();
    refreshCounts();
  } catch (e) {
    handleError(e, chatList);
  }
}

function renderRooms() {
  if (rooms.length === 0) {
    chatList.innerHTML = `<div class="chat-list-empty">아직 대화가 없습니다.<br>상품 상세의 "판매자에게 문의"로 시작할 수 있습니다.</div>`;
    return;
  }
  chatList.innerHTML = rooms.map((r) => `
    <button type="button" class="chat-item ${r.id === openRoomId ? "active" : ""}" data-id="${r.id}">
      <span class="chat-item-top">
        <strong>${escapeHtml(r.partnerName)}</strong>
        <span class="chat-role">${r.iAmSeller ? "구매 문의" : "판매자"}</span>
        <span class="chat-time">${r.lastMessageAt ? shortTime(r.lastMessageAt) : ""}</span>
      </span>
      <span class="chat-item-product">${escapeHtml(r.productTitle)}</span>
      <span class="chat-item-bottom">
        <span class="chat-preview">${r.lastMessage ? escapeHtml(r.lastMessage) : "아직 메시지가 없습니다."}</span>
        ${r.unreadCount > 0 && r.id !== openRoomId ? `<em class="nav-count">${r.unreadCount > 99 ? "99+" : r.unreadCount}</em>` : ""}
      </span>
    </button>`).join("");
}

chatList.addEventListener("click", (event) => {
  const item = event.target.closest(".chat-item");
  if (item) openRoom(Number(item.dataset.id));
});

// ---------- 대화창 ----------

function openRoom(roomId) {
  const room = rooms.find((r) => r.id === roomId);
  if (!room) {
    showMessage(pageMessage, "채팅방을 찾을 수 없습니다.");
    openRoomId = null;
    updateUrl();
    return;
  }
  stopRoom();
  openRoomId = roomId;
  lastMessageId = null;
  updateUrl();
  chatPane.classList.add("room-open"); // 좁은 화면에서는 대화창만 보인다
  chatRoom.innerHTML = `
    <header class="chat-room-head">
      <button type="button" class="chat-back" id="chat-back" aria-label="목록으로">←</button>
      <div>
        <strong>${escapeHtml(room.partnerName)}</strong>
        <a class="message-product" href="/product.html?id=${room.productId}">${escapeHtml(room.productTitle)}</a>
      </div>
    </header>
    <div class="chat-messages" id="chat-messages"><p class="loading">대화를 불러오는 중입니다...</p></div>
    <form class="chat-input" id="chat-form">
      <textarea id="chat-text" rows="1" maxlength="1000" placeholder="메시지를 입력하세요" title="Enter 보내기, Shift+Enter 줄바꿈"></textarea>
      <button type="submit" class="btn-small primary">보내기</button>
    </form>
    <p class="message chat-error" id="chat-error"></p>`;
  renderRooms();
  document.getElementById("chat-back").addEventListener("click", closeRoom);
  const text = document.getElementById("chat-text");
  text.addEventListener("keydown", (event) => {
    if (event.key === "Enter" && !event.shiftKey && !event.isComposing) {
      event.preventDefault();
      sendMessage();
    }
  });
  text.addEventListener("input", () => autoGrow(text));
  document.getElementById("chat-form").addEventListener("submit", (event) => {
    event.preventDefault();
    sendMessage();
  });
  text.focus();
  pollRoom();
  roomTimer = setInterval(pollRoom, ROOM_POLL_MS);
}

function closeRoom() {
  stopRoom();
  openRoomId = null;
  chatPane.classList.remove("room-open");
  chatRoom.innerHTML = `<div class="chat-empty">왼쪽에서 대화를 고르거나, 상품 상세의 "판매자에게 문의"로 대화를 시작하세요.</div>`;
  updateUrl();
  loadRooms();
}

function stopRoom() {
  clearInterval(roomTimer);
  roomTimer = null;
}

/** 새 메시지만 받아 아래에 붙인다. 처음에는 최근 100건 */
async function pollRoom() {
  if (polling || !openRoomId) return;
  polling = true;
  const roomId = openRoomId;
  try {
    const query = lastMessageId ? `?after=${lastMessageId}` : "";
    const messages = await api(`/api/chats/${roomId}/messages${query}`);
    if (roomId !== openRoomId) return; // 그사이 다른 방을 열었으면 버린다
    appendMessages(messages, lastMessageId === null);
    if (messages.length > 0) refreshCounts();
  } catch (e) {
    if (e.status === 401) {
      handleError(e, chatRoom);
    } else if (e.code !== "NETWORK_ERROR") {
      showMessage(document.getElementById("chat-error"), e.message);
      stopRoom();
    }
  } finally {
    polling = false;
  }
}

function appendMessages(messages, first) {
  const box = document.getElementById("chat-messages");
  if (!box) return;
  if (first) {
    box.innerHTML = messages.length === 0 ? `<p class="chat-start">첫 메시지를 보내 대화를 시작하세요.</p>` : "";
  }
  if (messages.length === 0) return;
  box.querySelector(".chat-start")?.remove();
  const nearBottom = box.scrollHeight - box.scrollTop - box.clientHeight < 80;
  let lastDay = box.dataset.lastDay ?? "";
  const html = [];
  for (const m of messages) {
    if (box.querySelector(`[data-id="${m.id}"]`)) continue; // 보낸 직후 받은 것과 겹치지 않게
    const day = m.createdAt.slice(0, 10);
    if (day !== lastDay) {
      html.push(`<div class="chat-day">${formatDate(m.createdAt)}</div>`);
      lastDay = day;
    }
    html.push(`
      <div class="bubble-row ${m.mine ? "mine" : ""}" data-id="${m.id}">
        <div class="bubble">${escapeHtml(m.content)}</div>
        <span class="bubble-time">${clock(m.createdAt)}</span>
      </div>`);
  }
  box.dataset.lastDay = lastDay;
  box.insertAdjacentHTML("beforeend", html.join(""));
  lastMessageId = Math.max(lastMessageId ?? 0, messages[messages.length - 1].id);
  // 처음 열 때와 내가 맨 아래를 보고 있을 때만 아래로 내린다 (위 대화를 읽는 중이면 그대로)
  if (first || nearBottom || messages.some((m) => m.mine)) box.scrollTop = box.scrollHeight;
}

async function sendMessage() {
  const text = document.getElementById("chat-text");
  const content = text.value.trim();
  if (!content || sending) return;
  sending = true;
  const error = document.getElementById("chat-error");
  try {
    const roomId = openRoomId;
    const sent = await api(`/api/chats/${roomId}/messages`, { method: "POST", body: { content } });
    text.value = "";
    autoGrow(text);
    showMessage(error, "");
    // 그사이 상대가 보낸 메시지가 있으면 먼저 받고, 내 메시지를 붙인다
    await pollRoom();
    if (roomId === openRoomId) appendMessages([sent], false);
    loadRooms();
  } catch (e) {
    showMessage(error, e.message);
  } finally {
    sending = false;
    text.focus();
  }
}

function autoGrow(textarea) {
  textarea.style.height = "auto";
  textarea.style.height = `${Math.min(textarea.scrollHeight, 120)}px`;
}

// ---------- 시간 표시 ----------

/** "오후 3:05" */
function clock(iso) {
  const d = new Date(iso);
  const h = d.getHours();
  return `${h < 12 ? "오전" : "오후"} ${h % 12 === 0 ? 12 : h % 12}:${String(d.getMinutes()).padStart(2, "0")}`;
}

/** 오늘이면 시각, 아니면 "10.26" */
function shortTime(iso) {
  const d = new Date(iso);
  const now = new Date();
  return d.toDateString() === now.toDateString() ? clock(iso) : `${d.getMonth() + 1}.${d.getDate()}`;
}

/** "2026년 10월 26일 (월)" */
function formatDate(iso) {
  const d = new Date(iso);
  return `${d.getFullYear()}년 ${d.getMonth() + 1}월 ${d.getDate()}일 (${"일월화수목금토"[d.getDay()]})`;
}

function handleError(e, box) {
  if (e.status === 401) {
    // api() 가 로그인 화면으로 보낸다 (로그인 후 이 화면으로 돌아옴)
    return;
  }
  box.innerHTML = `<div class="empty">${escapeHtml(e.message)}</div>`;
}

showTab(tab);

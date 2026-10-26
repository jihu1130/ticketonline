// 메시지함: 알림 / 받은 쪽지 / 보낸 쪽지
// 받은 메시지를 누르면 펼쳐지면서 읽음으로 바뀐다. 받은 쪽지에는 답장할 수 있다.

const listBox = document.getElementById("message-list");
const tabs = document.getElementById("message-tabs");
const pageMessage = document.getElementById("page-message");
const readAllButton = document.getElementById("read-all");
let box = new URLSearchParams(location.search).get("box") ?? "NOTICE";
let messages = [];

if (!auth.token()) {
  location.href = `/login.html?redirect=${encodeURIComponent(location.pathname + location.search)}`;
}

function renderMessage(m) {
  const who = box === "SENT"
    ? `받는 사람 ${escapeHtml(m.receiverName)}`
    : (m.senderName ? escapeHtml(m.senderName) : "티켓온라인");
  const product = m.productId
    ? `<a class="message-product" href="/product.html?id=${m.productId}">${escapeHtml(m.productTitle ?? "")}</a>`
    : "";
  const unread = box !== "SENT" && !m.read;
  return `
    <article class="message-item ${unread ? "unread" : ""}" data-id="${m.id}">
      <div class="message-head">
        ${unread ? `<span class="unread-dot" aria-label="안 읽음"></span>` : ""}
        <strong>${who}</strong>
        ${product}
        <span class="message-time">${formatDateTime(m.createdAt)}</span>
      </div>
      <p class="message-content">${escapeHtml(m.content)}</p>
      ${m.canReply ? `
        <div class="message-reply" hidden>
          <textarea maxlength="1000" placeholder="답장을 입력해 주세요"></textarea>
          <div class="edit-row">
            <button type="button" class="btn-small primary" data-action="send-reply">답장 보내기</button>
          </div>
          <p class="message"></p>
        </div>
        <button type="button" class="btn-small" data-action="reply">답장</button>` : ""}
    </article>`;
}

function render() {
  tabs.querySelectorAll(".tab").forEach((tab) => tab.classList.toggle("active", tab.dataset.box === box));
  readAllButton.hidden = box === "SENT";
  const empty = { NOTICE: "받은 알림이 없습니다.", INBOX: "받은 쪽지가 없습니다.", SENT: "보낸 쪽지가 없습니다." };
  listBox.innerHTML = messages.length === 0
    ? `<div class="empty">${empty[box]}</div>`
    : `<div class="message-list">${messages.map(renderMessage).join("")}</div>`;
}

/** 탭 숫자와 헤더 숫자를 함께 바꾼다 */
async function refreshCounts() {
  const count = await refreshUnreadCount();
  if (!count) return;
  const set = (name, n) => {
    const badge = tabs.querySelector(`[data-box="${name}"] .nav-count`);
    badge.textContent = n > 99 ? "99+" : String(n);
    badge.hidden = n === 0;
  };
  set("NOTICE", count.notice);
  set("INBOX", count.inbox);
}

async function load() {
  try {
    messages = await api(`/api/members/me/messages?box=${box}`);
    render();
    refreshCounts();
  } catch (e) {
    if (e.status === 401) {
      location.href = `/login.html?redirect=${encodeURIComponent(location.pathname + location.search)}`;
      return;
    }
    listBox.innerHTML = `<div class="empty">${escapeHtml(e.message)}</div>`;
  }
}

// ---------- 동작 ----------

tabs.addEventListener("click", (event) => {
  const tab = event.target.closest(".tab");
  if (!tab || tab.dataset.box === box) return;
  box = tab.dataset.box;
  history.replaceState(null, "", `?box=${box}`);
  showMessage(pageMessage, "");
  load();
});

readAllButton.addEventListener("click", async () => {
  try {
    await api(`/api/members/me/messages/read-all?box=${box}`, { method: "POST" });
    await load();
  } catch (e) {
    showMessage(pageMessage, e.message);
  }
});

listBox.addEventListener("click", async (event) => {
  const item = event.target.closest(".message-item");
  if (!item || event.target.closest("a")) return;
  const message = messages.find((m) => String(m.id) === item.dataset.id);
  const action = event.target.closest("button")?.dataset.action;

  if (action === "reply") {
    const form = item.querySelector(".message-reply");
    form.hidden = !form.hidden;
    if (!form.hidden) form.querySelector("textarea").focus();
  } else if (action === "send-reply") {
    await sendReply(message, item);
  } else if (!event.target.closest(".message-reply")) {
    item.classList.toggle("open");
  }

  // 받은 메시지를 처음 누르면 읽음으로
  if (box !== "SENT" && !message.read) {
    try {
      await api(`/api/members/me/messages/${message.id}/read`, { method: "POST" });
      message.read = true;
      item.classList.remove("unread");
      item.querySelector(".unread-dot")?.remove();
      refreshCounts();
    } catch (e) {
      // 읽음 표시는 다음에 다시 시도된다
    }
  }
});

async function sendReply(message, item) {
  const textarea = item.querySelector(".message-reply textarea");
  const result = item.querySelector(".message-reply .message");
  const content = textarea.value.trim();
  if (!content) {
    showMessage(result, "답장을 입력해 주세요.");
    return;
  }
  try {
    await api(`/api/messages/${message.id}/reply`, { method: "POST", body: { content } });
    textarea.value = "";
    item.querySelector(".message-reply").hidden = true;
    showMessage(pageMessage, "답장을 보냈습니다. 보낸 쪽지에서 확인할 수 있습니다.", "success");
  } catch (e) {
    showMessage(result, e.message);
  }
}

load();

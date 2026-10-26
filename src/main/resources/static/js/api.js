// 모든 화면이 함께 쓰는 API 호출과 로그인 정보 관리

const TOKEN_KEY = "accessToken";
const MEMBER_KEY = "loginMember";

const auth = {
  save(loginResponse) {
    localStorage.setItem(TOKEN_KEY, loginResponse.accessToken);
    localStorage.setItem(MEMBER_KEY, JSON.stringify({
      memberId: loginResponse.memberId,
      name: loginResponse.name,
      role: loginResponse.role,
    }));
  },
  token() {
    return localStorage.getItem(TOKEN_KEY);
  },
  member() {
    const saved = localStorage.getItem(MEMBER_KEY);
    return saved ? JSON.parse(saved) : null;
  },
  clear() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(MEMBER_KEY);
  },
};

/** 서버가 보낸 { code, message } 오류를 담는 예외 */
class ApiError extends Error {
  constructor(status, code, message) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

/**
 * API 호출. 로그인했다면 토큰을 자동으로 붙인다.
 * 실패하면 ApiError(code, message)를 던진다.
 */
async function api(path, { method = "GET", body } = {}) {
  const headers = { "Content-Type": "application/json" };
  const token = auth.token();
  if (token) headers.Authorization = `Bearer ${token}`;

  let response;
  try {
    response = await fetch(path, {
      method,
      headers,
      body: body ? JSON.stringify(body) : undefined,
    });
  } catch (e) {
    throw new ApiError(0, "NETWORK_ERROR", "서버에 연결할 수 없습니다.");
  }

  const data = await response.json().catch(() => null);
  if (!response.ok) {
    if (response.status === 401 && data?.code === "UNAUTHORIZED") goLogin(Boolean(token));
    throw new ApiError(response.status, data?.code ?? "UNKNOWN", data?.message ?? "요청을 처리하지 못했습니다.");
  }
  return data;
}

/**
 * 로그인이 필요한 요청이 401 이면 어느 화면에서든 로그인 화면으로 보낸다. 로그인하면 지금 화면으로 돌아온다.
 * 토큰이 있었는데 401 이면 로그인 시간(2시간)이 지난 것이라 저장된 로그인 정보를 지우고 안내한다.
 * 같은 화면에서 요청 여러 개가 동시에 401 이어도 한 번만 이동한다.
 */
let movingToLogin = false;
function goLogin(expired) {
  auth.clear();
  if (movingToLogin || location.pathname === "/login.html" || location.pathname === "/signup.html") return;
  movingToLogin = true;
  const back = encodeURIComponent(location.pathname + location.search);
  location.href = `/login.html?redirect=${back}${expired ? "&expired=1" : ""}`;
}

/** 사진 업로드 (multipart). 성공하면 "/uploads/..." 주소를 돌려준다. */
async function uploadImage(file) {
  const form = new FormData();
  form.append("file", file);
  const headers = {};
  const token = auth.token();
  if (token) headers.Authorization = `Bearer ${token}`;

  let response;
  try {
    response = await fetch("/api/images", { method: "POST", headers, body: form });
  } catch (e) {
    throw new ApiError(0, "NETWORK_ERROR", "서버에 연결할 수 없습니다.");
  }
  const data = await response.json().catch(() => null);
  if (!response.ok) {
    if (response.status === 401 && data?.code === "UNAUTHORIZED") goLogin(Boolean(token));
    throw new ApiError(response.status, data?.code ?? "UNKNOWN", data?.message ?? "사진을 올리지 못했습니다.");
  }
  return data.url;
}

/** 안내 문구를 보여 준다. type: "error" | "success" */
function showMessage(element, text, type = "error") {
  element.textContent = text;
  element.className = `message ${type}`;
}

/** 상단 메뉴: 로그인 상태에 따라 다르게 보여 준다 */
function renderHeader() {
  const nav = document.querySelector(".nav");
  if (!nav) return;
  const member = auth.member();
  if (!member) {
    nav.innerHTML = `<a href="/login.html">로그인</a><a href="/signup.html">회원가입</a>`;
    return;
  }
  const adminBadge = member.role === "ADMIN" ? `<span class="badge">관리자</span>` : "";
  nav.innerHTML = `
    ${adminBadge}
    <a class="nav-messages" href="/messages.html" aria-label="메시지함"><span class="nav-label">메시지함</span><em class="nav-count" id="unread-count" hidden></em></a>
    <a href="/my-orders.html">구매 내역</a>
    <a href="/seller.html">판매 관리</a>
    <a class="nav-me" href="/mypage.html" aria-label="마이페이지"><span class="nav-label">${escapeHtml(member.name)}님</span></a>
    <button class="btn-text" id="logout-button">로그아웃</button>`;
  document.getElementById("logout-button").addEventListener("click", () => {
    auth.clear();
    location.href = "/";
  });
  refreshUnreadCount();
}

/** 헤더 메시지함 옆 안 읽은 개수. 메시지함 화면에서 읽음 처리한 뒤에도 부른다 */
async function refreshUnreadCount() {
  const badge = document.getElementById("unread-count");
  if (!badge) return null;
  try {
    const count = await api("/api/members/me/messages/unread-count");
    badge.textContent = count.total > 99 ? "99+" : String(count.total);
    badge.hidden = count.total === 0;
    return count;
  } catch (e) {
    return null; // 개수를 못 불러와도 화면은 그대로 쓴다
  }
}

function escapeHtml(text) {
  const div = document.createElement("div");
  div.textContent = text;
  return div.innerHTML;
}

document.addEventListener("DOMContentLoaded", renderHeader);

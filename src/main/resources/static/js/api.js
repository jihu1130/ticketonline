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
    // 토큰이 만료되었으면 저장된 로그인 정보를 지운다
    if (response.status === 401 && data?.code === "UNAUTHORIZED") auth.clear();
    throw new ApiError(response.status, data?.code ?? "UNKNOWN", data?.message ?? "요청을 처리하지 못했습니다.");
  }
  return data;
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
    if (response.status === 401 && data?.code === "UNAUTHORIZED") auth.clear();
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
    <a href="/my-orders.html">구매 내역</a>
    <a href="/seller.html">판매 관리</a>
    <span>${escapeHtml(member.name)}님</span>
    <button class="btn-text" id="logout-button">로그아웃</button>`;
  document.getElementById("logout-button").addEventListener("click", () => {
    auth.clear();
    location.href = "/";
  });
}

function escapeHtml(text) {
  const div = document.createElement("div");
  div.textContent = text;
  return div.innerHTML;
}

document.addEventListener("DOMContentLoaded", renderHeader);

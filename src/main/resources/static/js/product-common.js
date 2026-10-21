// 상품 목록(메인)과 상품 상세 화면이 함께 쓰는 표시 도우미

const TYPE_LABEL = { TICKET: "티켓", GOODS: "굿즈" };
const DELIVERY_LABEL = { PARCEL: "택배", PICKUP: "현장 수령" };

/** 상품 판매 상태 (서버 ProductStatus) */
const PRODUCT_STATUS = {
  UPCOMING: { label: "판매 예정", cls: "upcoming" },
  ON_SALE: { label: "판매 중", cls: "on-sale" },
  SOLD_OUT: { label: "매진", cls: "sold-out" },
  ENDED: { label: "판매 종료", cls: "ended" },
};

/** 티켓 회차 예매 상태 (서버 PerformanceStatus) */
const PERFORMANCE_STATUS = {
  BEFORE: { label: "예매 전", cls: "upcoming" },
  OPEN: { label: "예매 중", cls: "on-sale" },
  SOLD_OUT: { label: "매진", cls: "sold-out" },
  CLOSED: { label: "예매 마감", cls: "ended" },
};

function statusBadge(table, status) {
  const s = table[status];
  return s ? `<span class="status-badge ${s.cls}">${s.label}</span>` : "";
}

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];
const pad2 = (n) => String(n).padStart(2, "0");
const ymd = (d) => `${d.getFullYear()}.${pad2(d.getMonth() + 1)}.${pad2(d.getDate())}`;
const md = (d) => `${pad2(d.getMonth() + 1)}.${pad2(d.getDate())}`;
const hm = (d) => `${pad2(d.getHours())}:${pad2(d.getMinutes())}`;

/** "2026-11-02T19:00:00" -> "2026.11.02 (월) 19:00" */
function formatDateTime(text) {
  if (!text) return "";
  const d = new Date(text);
  return `${ymd(d)} (${WEEKDAYS[d.getDay()]}) ${hm(d)}`;
}

/** "2026-10-22T20:00:00" -> "10.22 (목) 20:00" (카드처럼 좁은 곳) */
function formatShortDateTime(text) {
  if (!text) return "";
  const d = new Date(text);
  return `${md(d)} (${WEEKDAYS[d.getDay()]}) ${hm(d)}`;
}

/** 기간을 짧게: "2026.11.02 ~ 11.25" (같은 날이면 하루만, 해가 다르면 끝에도 연도) */
function formatPeriod(from, to) {
  if (!from) return "";
  const a = new Date(from);
  const b = new Date(to ?? from);
  if (ymd(a) === ymd(b)) return ymd(a);
  return `${ymd(a)} ~ ${a.getFullYear() === b.getFullYear() ? md(b) : ymd(b)}`;
}

function won(n) {
  return `${Number(n).toLocaleString()}원`;
}

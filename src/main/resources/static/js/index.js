// 메인: 상품 목록. 상단 메뉴의 ?type= 값으로 종류를 거른다 (없으면 전체)

const LIST_TITLES = { TICKET: "티켓 오픈", GOODS: "한정 굿즈" };
const listBox = document.getElementById("product-list");
const typeParam = new URLSearchParams(location.search).get("type");
const listType = LIST_TITLES[typeParam] ? typeParam : null;

document.getElementById("list-title").textContent = listType ? LIST_TITLES[listType] : "전체 상품";
document.querySelectorAll(".gnb a").forEach((a) =>
  a.classList.toggle("active", listType !== null && a.href.endsWith(`type=${listType}`)));

/** 카드: 포스터 + 상태 배지, 상품명, (티켓) 장소·공연 기간, 판매 기간, 최저가 */
function renderCard(p) {
  const poster = p.imageUrl
    ? `<img class="poster" src="${p.imageUrl}" alt="">`
    : `<div class="poster poster-empty">${TYPE_LABEL[p.type]}</div>`;
  const ticketInfo = p.type === "TICKET"
    ? `<p class="meta">${escapeHtml(p.venue ?? "")}</p>
       <p class="meta">${formatPeriod(p.firstEventAt, p.lastEventAt)}</p>`
    : "";
  const sale = p.status === "UPCOMING"
    ? `<p class="meta open-at">${formatShortDateTime(p.saleStartAt)} 판매 오픈</p>`
    : `<p class="meta">판매 ${formatPeriod(p.saleStartAt, p.saleEndAt)}</p>`;
  const price = p.minPrice === null
    ? ""
    : `<p class="price">${won(p.minPrice)}${p.type === "TICKET" ? "~" : ""}</p>`;
  const closed = p.status === "SOLD_OUT" || p.status === "ENDED";
  return `
    <a class="product-card${closed ? " is-closed" : ""}" href="/product.html?id=${p.id}">
      <div class="poster-wrap">${poster}${statusBadge(PRODUCT_STATUS, p.status)}</div>
      <p class="title">${escapeHtml(p.title)}</p>
      ${ticketInfo}
      ${sale}
      ${price}
    </a>`;
}

async function loadProducts() {
  try {
    const products = await api(listType ? `/api/products?type=${listType}` : "/api/products");
    if (products.length === 0) {
      listBox.className = "empty";
      listBox.textContent = "아직 등록된 상품이 없습니다.";
      return;
    }
    listBox.className = "product-grid";
    listBox.innerHTML = products.map(renderCard).join("");
  } catch (e) {
    listBox.className = "empty";
    listBox.textContent = e.message;
  }
}

loadProducts();

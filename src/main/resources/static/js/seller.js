// 판매 관리 화면: 상품 등록 / 내 상품 목록 / 수정 / 삭제
// 티켓은 좌석 등급과 회차를 여러 개 입력하고, 서버가 회차 x 등급마다 재고를 만든다.

const form = document.getElementById("product-form");
const message = document.getElementById("message");
const listBox = document.getElementById("product-list");
const submitButton = document.getElementById("submit-button");
const cancelButton = document.getElementById("cancel-button");
const gradeRows = document.getElementById("grade-rows");
const performanceRows = document.getElementById("performance-rows");

const TYPE_NAME = { TICKET: "티켓", GOODS: "굿즈" };
const DELIVERY_NAME = { PARCEL: "택배", PICKUP: "현장 수령" };
const MAX_GRADES = 10;
const MAX_PERFORMANCES = 20;
const MAX_EXTRA_IMAGES = 10;

let products = [];
let editingId = null; // 수정 중인 상품 id (null이면 새로 등록)
const images = { imageUrl: null, seatMapUrl: null }; // 업로드한 사진 주소 (한 장짜리)
let extraImages = []; // 굿즈 부가 사진 주소 (보여 줄 순서대로)

if (!auth.token()) {
  location.href = "/login.html";
}

// ---------- 입력칸 ----------

function selectedType() {
  return form.querySelector("input[name=type]:checked").value;
}

/** 종류에 따라 입력칸과 안내 문구를 바꾼다 */
function toggleTypeFields() {
  const isTicket = selectedType() === "TICKET";
  document.getElementById("ticket-fields").hidden = !isTicket;
  document.getElementById("goods-fields").hidden = isTicket;
  document.getElementById("image-label").textContent = isTicket ? "대표 사진 (포스터)" : "상품 사진";
  document.getElementById("description-label").textContent = isTicket ? "공연 소개 (선택)" : "상품 설명";
  document.getElementById("limit-hint").textContent = isTicket
    ? "한 사람이 한 회차에서 살 수 있는 매수 (등급 합산)"
    : "한 사람이 살 수 있는 개수";
}

form.querySelectorAll("input[name=type]").forEach((radio) =>
  radio.addEventListener("change", toggleTypeFields));

function addGradeRow(grade = {}) {
  if (gradeRows.children.length >= MAX_GRADES) return;
  const row = document.createElement("div");
  row.className = "row-item";
  row.innerHTML = `
    <input class="grade-name" maxlength="30" placeholder="등급 (예: VIP)">
    <input class="grade-price" type="number" min="0" placeholder="가격">
    <input class="grade-seats" type="number" min="1" placeholder="좌석 수">
    <button type="button" class="btn-small danger" data-action="remove-row">삭제</button>`;
  row.querySelector(".grade-name").value = grade.name ?? "";
  row.querySelector(".grade-price").value = grade.price ?? "";
  row.querySelector(".grade-seats").value = grade.seatCount ?? "";
  gradeRows.appendChild(row);
}

function addPerformanceRow(eventAt = "") {
  if (performanceRows.children.length >= MAX_PERFORMANCES) return;
  const row = document.createElement("div");
  row.className = "row-item";
  row.innerHTML = `
    <input class="performance-at" type="datetime-local">
    <button type="button" class="btn-small danger" data-action="remove-row">삭제</button>`;
  row.querySelector(".performance-at").value = eventAt ? eventAt.slice(0, 16) : "";
  performanceRows.appendChild(row);
}

document.getElementById("add-grade").addEventListener("click", () => addGradeRow());
document.getElementById("add-performance").addEventListener("click", () => addPerformanceRow());

form.addEventListener("click", (event) => {
  const button = event.target.closest("button[data-action=remove-row]");
  if (button) button.closest(".row-item").remove();
});

// ---------- 사진 ----------

function setImage(picker, url) {
  images[picker.dataset.target] = url;
  const preview = picker.querySelector(".image-preview");
  preview.hidden = !url;
  preview.src = url ?? "";
  picker.querySelector("[data-action=clear-image]").hidden = !url;
  if (!url) picker.querySelector("input[type=file]").value = "";
}

document.querySelectorAll(".image-picker[data-target]").forEach((picker) => {
  picker.querySelector("input[type=file]").addEventListener("change", async (event) => {
    const file = event.target.files[0];
    if (!file) return;
    showMessage(message, "사진을 올리는 중입니다...", "success");
    try {
      setImage(picker, await uploadImage(file));
      showMessage(message, "");
    } catch (e) {
      setImage(picker, null);
      showMessage(message, e.message);
    }
  });
  picker.querySelector("[data-action=clear-image]").addEventListener("click", () => setImage(picker, null));
});

// ---------- 굿즈 부가 사진 (최대 10장) ----------

const extraBox = document.getElementById("extra-images");
const extraInput = document.getElementById("extra-file");

function renderExtraImages() {
  document.getElementById("extra-count").textContent = extraImages.length;
  extraBox.innerHTML = extraImages.map((url, i) => `
    <div class="extra-item">
      <img src="${url}" alt="">
      <button type="button" class="extra-remove" data-index="${i}" aria-label="사진 삭제">×</button>
    </div>`).join("");
  extraInput.disabled = extraImages.length >= MAX_EXTRA_IMAGES;
}

extraBox.addEventListener("click", (event) => {
  const button = event.target.closest(".extra-remove");
  if (!button) return;
  extraImages.splice(Number(button.dataset.index), 1);
  renderExtraImages();
});

extraInput.addEventListener("change", async (event) => {
  const files = [...event.target.files];
  extraInput.value = "";
  if (files.length === 0) return;
  const room = MAX_EXTRA_IMAGES - extraImages.length;
  const toUpload = files.slice(0, room);
  try {
    // 고른 순서대로 한 장씩 올린다
    for (let i = 0; i < toUpload.length; i++) {
      showMessage(message, `사진을 올리는 중입니다... (${i + 1}/${toUpload.length})`, "success");
      extraImages.push(await uploadImage(toUpload[i]));
      renderExtraImages();
    }
    showMessage(message, files.length > room
      ? `부가 사진은 ${MAX_EXTRA_IMAGES}장까지라 ${files.length - room}장은 올리지 않았습니다.`
      : "");
  } catch (e) {
    showMessage(message, e.message);
  }
});

// ---------- 폼 읽기 / 채우기 ----------

function value(id) {
  return document.getElementById(id).value.trim();
}

function toNumber(text) {
  return text === "" ? null : Number(text);
}

/**
 * 세 칸이 모두 빈 등급 줄을 화면에서 지운다.
 * 보내는 목록과 화면의 줄 순서가 같아야 서버의 "N번째 좌석 등급" 안내가 화면 줄과 맞는다.
 * 모든 줄이 비었으면 입력할 수 있게 한 줄은 남긴다 (서버가 "좌석 등급을 1개 이상" 안내).
 */
function removeBlankGradeRows() {
  const blank = (row) => [...row.querySelectorAll("input")].every((input) => input.value.trim() === "");
  const rows = [...gradeRows.children];
  rows.filter(blank).forEach((row) => row.remove());
  if (gradeRows.children.length === 0) addGradeRow();
}

function readForm() {
  const isTicket = selectedType() === "TICKET";
  const body = {
    type: selectedType(),
    title: value("title"),
    imageUrl: images.imageUrl,
    maxPerPerson: toNumber(value("maxPerPerson")),
    saleStartAt: value("saleStartAt") || null,
    saleEndAt: value("saleEndAt") || null,
    description: value("description"),
  };
  if (isTicket) {
    body.venue = value("venue");
    body.seatMapUrl = images.seatMapUrl;
    body.closeBeforeHours = toNumber(value("closeBeforeHours"));
    removeBlankGradeRows();
    body.seatGrades = [...gradeRows.children]
      .map((row) => ({
        name: row.querySelector(".grade-name").value.trim(),
        price: toNumber(row.querySelector(".grade-price").value.trim()),
        seatCount: toNumber(row.querySelector(".grade-seats").value.trim()),
      }))
      // 남은 빈 줄은 "모두 비었을 때 남겨 둔 한 줄"뿐이라 빼도 번호가 어긋나지 않는다
      .filter((g) => g.name !== "" || g.price !== null || g.seatCount !== null);
    body.performances = [...performanceRows.children]
      .map((row) => row.querySelector(".performance-at").value)
      .filter((v) => v !== "");
  } else {
    body.price = toNumber(value("price"));
    body.quantity = toNumber(value("quantity"));
    body.deliveryType = value("deliveryType");
    body.images = [...extraImages];
  }
  return body;
}

function resetForm() {
  editingId = null;
  form.reset();
  form.querySelectorAll("input[name=type]").forEach((r) => r.disabled = false);
  document.querySelectorAll(".image-picker[data-target]").forEach((p) => setImage(p, null));
  extraImages = [];
  renderExtraImages();
  gradeRows.innerHTML = "";
  performanceRows.innerHTML = "";
  addGradeRow();
  addPerformanceRow();
  document.getElementById("type-hint").hidden = true;
  document.getElementById("form-title").textContent = "상품 등록";
  submitButton.textContent = "등록하기";
  cancelButton.hidden = true;
  toggleTypeFields();
}

function startEdit(product) {
  resetForm();
  editingId = product.id;
  form.querySelector(`input[name=type][value=${product.type}]`).checked = true;
  form.querySelectorAll("input[name=type]").forEach((r) => r.disabled = true);
  document.getElementById("type-hint").hidden = false;
  toggleTypeFields();

  document.getElementById("title").value = product.title;
  document.getElementById("maxPerPerson").value = product.maxPerPerson;
  document.getElementById("saleStartAt").value = product.saleStartAt.slice(0, 16);
  document.getElementById("saleEndAt").value = product.saleEndAt.slice(0, 16);
  setImage(document.querySelector(".image-picker[data-target=imageUrl]"), product.imageUrl);
  document.getElementById("description").value = product.description ?? "";

  if (product.type === "TICKET") {
    document.getElementById("venue").value = product.venue ?? "";
    document.getElementById("closeBeforeHours").value = product.closeBeforeHours ?? 1;
    setImage(document.querySelector(".image-picker[data-target=seatMapUrl]"), product.seatMapUrl);
    gradeRows.innerHTML = "";
    product.seatGrades.forEach((g) => addGradeRow(g));
    performanceRows.innerHTML = "";
    product.performances.forEach((p) => addPerformanceRow(p.eventAt));
  } else {
    const item = product.saleItems[0];
    document.getElementById("price").value = item?.price ?? "";
    document.getElementById("quantity").value = item?.totalQuantity ?? "";
    document.getElementById("deliveryType").value = product.deliveryType ?? "PARCEL";
    extraImages = [...(product.images ?? [])];
    renderExtraImages();
  }

  document.getElementById("form-title").textContent = "상품 수정";
  submitButton.textContent = "수정하기";
  cancelButton.hidden = false;
  showMessage(message, "");
  window.scrollTo({ top: 0, behavior: "smooth" });
}

cancelButton.addEventListener("click", () => {
  resetForm();
  showMessage(message, "");
});

// ---------- 저장 / 삭제 ----------

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  const body = readForm();
  submitButton.disabled = true;
  try {
    if (editingId) {
      delete body.type;
      await api(`/api/products/${editingId}`, { method: "PUT", body });
      showMessage(message, "수정되었습니다.", "success");
    } else {
      await api("/api/products", { method: "POST", body });
      showMessage(message, "등록되었습니다.", "success");
    }
    resetForm();
    await loadProducts();
  } catch (e) {
    if (e.status === 401) {
      // api() 가 로그인 화면으로 보낸다 (로그인 후 이 화면으로 돌아옴)
      return;
    }
    showMessage(message, e.message);
  } finally {
    submitButton.disabled = false;
  }
});

async function deleteProduct(product) {
  if (!confirm(`"${product.title}" 상품을 삭제할까요?`)) return;
  try {
    await api(`/api/products/${product.id}`, { method: "DELETE" });
    if (editingId === product.id) resetForm();
    showMessage(message, "삭제되었습니다.", "success");
    await loadProducts();
  } catch (e) {
    showMessage(message, e.message);
  }
}

// ---------- 목록 ----------

/** "2026-10-22T10:00:00" -> "2026-10-22 10:00" */
function formatDate(text) {
  return text ? text.slice(0, 16).replace("T", " ") : "";
}

function won(n) {
  return `${n.toLocaleString()}원`;
}

/** 티켓: 회차마다 등급별 남은 좌석 / 굿즈: 가격과 남은 수량 */
function renderStock(p) {
  if (p.type === "GOODS") {
    const item = p.saleItems[0];
    return item
      ? `<p class="product-meta">${won(item.price)} · 남은 수량 ${item.remainingQuantity}/${item.totalQuantity} · ${DELIVERY_NAME[p.deliveryType]}${p.images?.length ? ` · 부가 사진 ${p.images.length}장` : ""}</p>`
      : "";
  }
  const gradeName = Object.fromEntries(p.seatGrades.map((g) => [g.id, g.name]));
  const grades = p.seatGrades.map((g) => `${escapeHtml(g.name)} ${won(g.price)}`).join(" · ");
  const rows = p.performances.map((perf) => {
    const stock = p.saleItems
      .filter((s) => s.performanceId === perf.id)
      .map((s) => `${escapeHtml(gradeName[s.seatGradeId])} ${s.remainingQuantity}/${s.totalQuantity}`)
      .join(" · ");
    return `<li><strong>${formatDate(perf.eventAt)}</strong> ${stock}
      <span class="close-at">예매 마감 ${formatDate(perf.bookingCloseAt)}</span></li>`;
  }).join("");
  const seatMap = p.seatMapUrl
    ? ` · <a href="${p.seatMapUrl}" target="_blank" rel="noopener">좌석 배치도</a>`
    : "";
  return `
    <p class="product-meta">${escapeHtml(p.venue)}${seatMap}</p>
    <p class="product-meta">${grades}</p>
    <ul class="stock-list">${rows}</ul>`;
}

function renderProducts() {
  if (products.length === 0) {
    listBox.innerHTML = `<div class="empty">아직 등록한 상품이 없습니다.</div>`;
    return;
  }
  const now = new Date();
  listBox.innerHTML = products.map((p) => {
    // 판매 시작 전에만 수정/삭제 가능 (최종 판단은 서버가 한다)
    const editable = new Date(p.saleStartAt) > now;
    const thumb = p.imageUrl
      ? `<img class="product-thumb" src="${p.imageUrl}" alt="">`
      : `<div class="product-thumb empty-thumb">사진 없음</div>`;
    return `
      <article class="product-item" data-id="${p.id}">
        ${thumb}
        <div class="product-body">
          <div class="product-head">
            <span class="badge">${TYPE_NAME[p.type]}</span>
            <strong>${escapeHtml(p.title)}</strong>
          </div>
          <p class="product-meta">판매 ${formatDate(p.saleStartAt)} ~ ${formatDate(p.saleEndAt)} · 1인 ${p.maxPerPerson}${p.type === "TICKET" ? "매(회차당)" : "개"}</p>
          ${renderStock(p)}
          ${editable
            ? `<div class="product-actions">
                 <button class="btn-small" data-action="edit">수정</button>
                 <button class="btn-small danger" data-action="delete">삭제</button>
               </div>`
            : `<p class="hint">판매가 시작되어 수정할 수 없습니다.</p>`}
        </div>
      </article>`;
  }).join("");
}

listBox.addEventListener("click", (event) => {
  const button = event.target.closest("button[data-action]");
  if (!button) return;
  const id = Number(button.closest(".product-item").dataset.id);
  const product = products.find((p) => p.id === id);
  if (button.dataset.action === "edit") startEdit(product);
  else deleteProduct(product);
});

async function loadProducts() {
  try {
    products = await api("/api/sellers/me/products");
    renderProducts();
  } catch (e) {
    if (e.status === 401) {
      // api() 가 로그인 화면으로 보낸다 (로그인 후 이 화면으로 돌아옴)
      return;
    }
    listBox.innerHTML = `<div class="empty">${escapeHtml(e.message)}</div>`;
  }
}

resetForm();
loadProducts();

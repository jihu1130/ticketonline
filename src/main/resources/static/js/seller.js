// 판매 관리 화면: 상품 등록 / 내 상품 목록 / 수정 / 삭제

const form = document.getElementById("product-form");
const message = document.getElementById("message");
const listBox = document.getElementById("product-list");
const submitButton = document.getElementById("submit-button");
const cancelButton = document.getElementById("cancel-button");

const TYPE_NAME = { TICKET: "티켓", GOODS: "굿즈" };
const DELIVERY_NAME = { PARCEL: "택배", PICKUP: "현장 수령" };

let products = [];
let editingId = null; // 수정 중인 상품 id (null이면 새로 등록)

if (!auth.token()) {
  location.href = "/login.html";
}

function selectedType() {
  return form.querySelector("input[name=type]:checked").value;
}

/** 종류에 따라 입력칸을 바꿔 보여 준다 */
function toggleTypeFields() {
  const isTicket = selectedType() === "TICKET";
  document.getElementById("ticket-fields").hidden = !isTicket;
  document.getElementById("goods-fields").hidden = isTicket;
}

form.querySelectorAll("input[name=type]").forEach((radio) =>
  radio.addEventListener("change", toggleTypeFields));

/** "2026-10-22T10:00:00" -> "2026-10-22 10:00" */
function formatDate(value) {
  return value ? value.slice(0, 16).replace("T", " ") : "";
}

function value(id) {
  return document.getElementById(id).value.trim();
}

function numberOrNull(id) {
  const text = value(id);
  return text === "" ? null : Number(text);
}

function readForm() {
  const isTicket = selectedType() === "TICKET";
  return {
    type: selectedType(),
    title: value("title"),
    price: numberOrNull("price"),
    totalQuantity: numberOrNull("totalQuantity"),
    maxPerPerson: numberOrNull("maxPerPerson"),
    saleStartAt: value("saleStartAt") || null,
    saleEndAt: value("saleEndAt") || null,
    eventAt: isTicket ? (value("eventAt") || null) : null,
    venue: isTicket ? value("venue") : null,
    description: isTicket ? null : value("description"),
    deliveryType: isTicket ? null : value("deliveryType"),
  };
}

function resetForm() {
  editingId = null;
  form.reset();
  form.querySelectorAll("input[name=type]").forEach((r) => r.disabled = false);
  document.getElementById("type-hint").hidden = true;
  document.getElementById("form-title").textContent = "상품 등록";
  submitButton.textContent = "등록하기";
  cancelButton.hidden = true;
  toggleTypeFields();
}

function startEdit(product) {
  editingId = product.id;
  form.querySelector(`input[name=type][value=${product.type}]`).checked = true;
  form.querySelectorAll("input[name=type]").forEach((r) => r.disabled = true);
  document.getElementById("type-hint").hidden = false;
  toggleTypeFields();

  document.getElementById("title").value = product.title;
  document.getElementById("price").value = product.price;
  document.getElementById("totalQuantity").value = product.totalQuantity;
  document.getElementById("maxPerPerson").value = product.maxPerPerson;
  document.getElementById("saleStartAt").value = product.saleStartAt.slice(0, 16);
  document.getElementById("saleEndAt").value = product.saleEndAt.slice(0, 16);
  document.getElementById("eventAt").value = product.eventAt ? product.eventAt.slice(0, 16) : "";
  document.getElementById("venue").value = product.venue ?? "";
  document.getElementById("description").value = product.description ?? "";
  document.getElementById("deliveryType").value = product.deliveryType ?? "PARCEL";

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
      location.href = "/login.html";
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

function renderProducts() {
  if (products.length === 0) {
    listBox.innerHTML = `<div class="empty">아직 등록한 상품이 없습니다.</div>`;
    return;
  }
  const now = new Date();
  listBox.innerHTML = products.map((p) => {
    // 판매 시작 전에만 수정/삭제 가능 (최종 판단은 서버가 한다)
    const editable = new Date(p.saleStartAt) > now;
    const detail = p.type === "TICKET"
      ? `공연 ${formatDate(p.eventAt)} · ${escapeHtml(p.venue)}`
      : `${DELIVERY_NAME[p.deliveryType]}`;
    return `
      <article class="product-item" data-id="${p.id}">
        <div class="product-head">
          <span class="badge">${TYPE_NAME[p.type]}</span>
          <strong>${escapeHtml(p.title)}</strong>
        </div>
        <p class="product-meta">
          ${p.price.toLocaleString()}원 · 남은 수량 ${p.remainingQuantity}/${p.totalQuantity} · 1인 ${p.maxPerPerson}개
        </p>
        <p class="product-meta">판매 ${formatDate(p.saleStartAt)} ~ ${formatDate(p.saleEndAt)}</p>
        <p class="product-meta">${detail}</p>
        ${editable
          ? `<div class="product-actions">
               <button class="btn-small" data-action="edit">수정</button>
               <button class="btn-small danger" data-action="delete">삭제</button>
             </div>`
          : `<p class="hint">판매가 시작되어 수정할 수 없습니다.</p>`}
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
      location.href = "/login.html";
      return;
    }
    listBox.innerHTML = `<div class="empty">${escapeHtml(e.message)}</div>`;
  }
}

toggleTypeFields();
loadProducts();

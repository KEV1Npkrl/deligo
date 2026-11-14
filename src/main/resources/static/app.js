import { deliveryArt, welcomeArt } from "/assets/figma-ui.js";
const $ = (s) => document.querySelector(s);
const escape = (v) =>
  String(v ?? "").replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        c
      ],
  );
const money = (n) =>
  new Intl.NumberFormat("es-PE", { style: "currency", currency: "PEN" }).format(
    Number(n || 0),
  );
const date = (v) =>
  new Date(v).toLocaleString("es-PE", {
    timeZone: "America/Lima",
    dateStyle: "short",
    timeStyle: "short",
  });
const states = {
  PENDING: "Pendiente",
  PREPARING: "En preparación",
  READY: "Listo para recoger",
  ASSIGNED: "Repartidor asignado",
  TO_STORE: "En camino al comercio",
  AT_STORE: "En el comercio",
  PICKED_UP: "Pedido recogido",
  ON_THE_WAY: "En camino a tu dirección",
  ARRIVED: "El pedido llegó",
  DELIVERED: "Entregado",
  CANCELLED: "Cancelado",
};
const eventLabels = {
  ...states,
  PREPARE: "Preparación aceptada",
  CLAIM: "Entrega aceptada por el repartidor",
  CANCEL: "Pedido cancelado",
  YAPE_SUBMIT: "Referencia Yape registrada",
  YAPE_VERIFY: "Pago Yape verificado",
  YAPE_REJECT: "Referencia Yape rechazada",
  REFUND: "Devolución registrada",
  RENDER_CASH: "Efectivo rendido",
  SETTLE: "Liquidación registrada",
};
const payments = {
  UNPAID: "Pendiente de pago",
  VERIFYING: "Yape por verificar",
  PAID: "Pago confirmado",
  VOID: "Sin cobro",
  REFUND_PENDING: "Devolución pendiente",
  REFUNDED: "Devuelto",
};
const roles = {
  CUSTOMER: "Cliente",
  MERCHANT: "Comercio",
  COURIER: "Repartidor",
  ADMIN: "Administración",
};
const initials = {
  food: "MS",
  burger: "BB",
  market: "MV",
  bakery: "m&c",
  care: "BL",
  flowers: "fm",
};
const state = {
  user: null,
  demo: false,
  shops: [],
  zones: [],
  settings: { rain_fee: 0 },
  products: [],
  cart: [],
  cartShop: null,
  query: "",
  category: "Todos",
  sort: "name",
  csrf: null,
  order: null,
  workspace: "orders",
  submitKey: crypto.randomUUID(),
};
let viewSerial = 0;
async function token() {
  state.csrf = await (await fetch("/api/auth/csrf")).json();
}
async function api(path, options = {}) {
  const { method = "GET", body } = options;
  if (method !== "GET" && !state.csrf) await token();
  const headers = body ? { "Content-Type": "application/json" } : {};
  if (method !== "GET") headers[state.csrf.header] = state.csrf.token;
  const response = await fetch("/api" + path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) {
    let msg = "No se pudo completar la acción.";
    try {
      msg = (await response.json()).message || msg;
    } catch {}
    if (response.status === 401) msg = "Tu sesión terminó. Vuelve a ingresar.";
    if (response.status === 403)
      msg = "Acceso denegado o sesión desactualizada. Vuelve a ingresar.";
    throw new Error(msg);
  }
  return response.status === 204 ||
    response.headers.get("content-length") === "0"
    ? null
    : response.text().then((t) => (t ? JSON.parse(t) : null));
}
function toast(text) {
  $("#toast").textContent = text;
  $("#toast").classList.add("visible");
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => $("#toast").classList.remove("visible"), 4500);
}
function modal(title, body) {
  $("#modal-body").innerHTML =
    `<h2 id="modal-title">${escape(title)}</h2>${body}`;
  if (!$("#modal").open) $("#modal").showModal();
}
function closeModal() {
  $("#modal").close();
}
function field(label, name, value = "", type = "text", extra = "") {
  return `<label class="field">${label}<input name="${name}" type="${type}" value="${escape(value)}" ${extra}></label>`;
}
function empty(title, body) {
  return `<div class="empty"><h2>${title}</h2><p>${body}</p><a class="button" href="#explore">Explorar comercios ↗</a></div>`;
}
function art(shop) {
  return `<div class="shop-art ${escape(shop.image_key)}" aria-hidden="true"><span class="monogram">${initials[shop.image_key] || "dg"}</span><span class="art-sub">${escape(shop.category)} · Tarapoto</span><span class="shop-tag">${shop.is_open ? "Abierto ahora" : "Cerrado"}</span></div>`;
}
function nav() {
  const user = state.user;
  $("#cart-button").hidden = !!user && user.user_role !== "CUSTOMER";
  $("#account-button").textContent = user
    ? user.full_name.split(" ")[0] + " ↗"
    : "Ingresar ↗";
  $('[data-nav="workspace"]').hidden = !user || user.user_role === "CUSTOMER";
  $('[data-nav="orders"]').textContent =
    user?.user_role === "CUSTOMER" || !user ? "Mis pedidos" : "Pedidos";
  $("#cart-count").textContent = state.cart.reduce((n, p) => n + p.quantity, 0);
  document
    .querySelectorAll("[data-nav]")
    .forEach((a) =>
      a.classList.toggle("active", location.hash.includes(a.dataset.nav)),
    );
}
async function refreshIdentity() {
  const info = await api("/auth/me");
  state.user = info.user;
  state.demo = info.demo;
  nav();
}
async function route() {
  const serial = ++viewSerial;
  nav();
  const hash = location.hash.slice(1) || "explore";
  $("#view").innerHTML = '<p class="loading">Cargando…</p>';
  try {
    let html;
    if (hash === "cart") html = `<div class="page-title"><h1>Mi bolsa</h1><p>Revisa tus productos antes de continuar.</p></div><div class="cart-page" id="inline-cart">${cartContent()}</div>`;
    else if (hash.startsWith("merchant/")) html = await shopPage(hash.split("/")[1]);
    else if (hash === "orders") html = await ordersPage();
    else if (hash.startsWith("order/"))
      html = await orderPage(hash.split("/")[1]);
    else if (hash === "workspace") html = await workspacePage();
    else if (hash === "checkout") html = await checkoutPage();
    else html = explorePage();
    if (serial === viewSerial) {
      $("#view").innerHTML = html;
      nav();
    }
  } catch (e) {
    if (serial === viewSerial)
      $("#view").innerHTML = empty(
        "No pudimos cargar esta vista",
        escape(e.message),
      );
  }
}
function explorePage() {
  const categories=["Todos", ...new Set(state.shops.map(s=>s.category))];
  return `<div class="welcome-heading"><div><p class="eyebrow">DELIVERY LOCAL · TARAPOTO</p><h1>¿Qué se te antoja hoy?</h1><p>Encuentra tus favoritos, cerca de ti.</p></div><a class="text-link" href="#orders">Ver mis pedidos →</a></div>
  <div class="search-row"><label class="searchbox"><img src="/assets/figma/234-4849-imgSearchMagnifier.svg" alt=""><input id="search" type="search" placeholder="Busca un comercio o categoría" aria-label="Buscar comercios" value="${escape(state.query)}"></label><span>${state.shops.length} comercios locales</span></div>
  <section class="category-section"><h2>Explora por categoría</h2><div class="categories" role="group" aria-label="Categorías">${categories.map(c=>`<button class="category-tile ${state.category===c?'active':''}" data-action="category" data-value="${escape(c)}" aria-pressed="${state.category===c}"><span class="category-picture">${categoryArt(c)}</span><span>${escape(c)}</span></button>`).join('')}</div></section>
  <section class="delivery-banner"><div><h2>Tu barrio,<br>en tu puerta.</h2><p>Compra local. Nosotros lo acercamos.</p><a href="#merchants" class="button">Explorar comercios</a></div><div class="delivery-illustration">${deliveryArt}</div></section>
  <section id="merchants"><div class="section-head"><div><h2>Comercios cerca de ti</h2><p>Restaurantes y mucho más, en un solo lugar.</p></div><select id="sort" class="sort" aria-label="Ordenar comercios"><option value="name" ${state.sort==='name'?'selected':''}>Por nombre</option><option value="speed" ${state.sort==='speed'?'selected':''}>Más rápidos</option><option value="rating" ${state.sort==='rating'?'selected':''}>Mejor calificados</option></select></div><div class="merchant-grid" id="merchant-grid">${merchantCards()}</div></section>`;
}
function categoryArt(c) {
 const photos={Restaurantes:'246-4844-imgDeliciousFriedChickenWithFrenchFries51.png'};
 const symbols={Todos:'◎', 'Cuidado personal':'✚', Flores:'✿', Minimarket:'▦', Panadería:'◒'};
 return photos[c]?`<img src="/assets/figma/${photos[c]}" alt="" loading="lazy">`:`<span class="category-symbol" aria-hidden="true">${symbols[c]||'◎'}</span>`;
}
function productArt(p) {
 const photo = ['classic','double'].includes(p.id) ? '203-4234-imgBurger.png' : null;
 return photo ? `<img class="product-photo" src="/assets/figma/${photo}" alt="Imagen referencial de hamburguesa" loading="lazy">` : `<span class="product-placeholder" aria-hidden="true">${escape(p.name.split(' ').slice(0,2).map(w=>w[0]).join(''))}</span>`;
}
function productDetail(id) {
 const p=state.products.find(p=>p.id===id); if(!p)return;
 const shop=state.shops.find(s=>s.id===p.merchant_id);
 modal(p.name,`<div class="product-detail-art">${productArt(p)}</div><p class="detail-description">${escape(p.description)}</p><div class="detail-meta"><span>${escape(shop.name)}</span><span>${shop.prep_minutes} min preparación</span></div><div class="detail-purchase"><strong>${money(p.price)}</strong><button class="button" data-action="add" data-id="${p.id}" ${!shop.is_open||!p.stock?'disabled':''}>Agregar a mi bolsa</button></div><p class="muted">${p.stock} unidades disponibles.</p>`);
}

function merchantCards() {
  let shops = state.shops.filter(
    (s) =>
      (state.category === "Todos" || s.category === state.category) &&
      `${s.name} ${s.category} ${s.description}`
        .toLocaleLowerCase("es")
        .includes(state.query.toLocaleLowerCase("es")),
  );
  shops.sort((a, b) =>
    state.sort === "speed"
      ? a.prep_minutes - b.prep_minutes || a.name.localeCompare(b.name)
      : state.sort === "rating"
        ? (b.rating || 0) - (a.rating || 0) || a.name.localeCompare(b.name)
        : a.name.localeCompare(b.name),
  );
  return shops.length
    ? shops
        .map(
          (s) =>
            `<a class="merchant-card" href="#merchant/${s.id}">${s.id === "burger" ? '<div class="shop-photo"><img src="/assets/figma/136-2305-imgFrame91.png" alt="Hamburguesa referencial del comercio" loading="lazy"></div>' : art(s)}<div class="card-body"><h3>${escape(s.name)}</h3><p>${escape(s.description)}</p><div class="card-meta"><span>◷ ${s.prep_minutes} min preparación</span><strong>${s.rating ? "★ " + Number(s.rating).toFixed(1) + "/4" : "Nuevo en DeliGo"}</strong></div></div></a>`,
        )
        .join("")
    : empty(
        "No encontramos coincidencias",
        "Prueba otra categoría o un nombre diferente.",
      );
}
async function shopPage(id) {
  const shop = state.shops.find((s) => s.id === id);
  if (!shop) throw new Error("Comercio no encontrado.");
  state.products = await api(
    "/catalog/products?merchantId=" + encodeURIComponent(id),
  );
  return `<a href="#explore" class="back">← Todos los comercios</a><section class="shop-header">${art(shop)}<div><div class="eyebrow">${escape(shop.category)}</div><h1>${escape(shop.name)}</h1><p>${escape(shop.description)}<br>Preparación estimada: ${shop.prep_minutes} minutos · Delivery desde ${money(5)}</p></div></section><div class="catalog-layout"><section><div class="section-head"><h2>Elige lo que te gusta</h2><span class="muted">${state.products.length} productos</span></div><div class="product-list">${state.products.map((p) => `<article class="product"><button class="product-visual" data-action="product-detail" data-id="${p.id}" aria-label="Ver ${escape(p.name)}">${productArt(p)}</button><div><h3><button class="product-title" data-action="product-detail" data-id="${p.id}">${escape(p.name)}</button></h3><p>${escape(p.description)}</p><span class="price">${money(p.price)}</span> <small>· ${p.stock} disponibles</small></div><button class="add" data-action="add" data-id="${p.id}" aria-label="Agregar ${escape(p.name)}" ${!shop.is_open || !p.stock ? "disabled" : ""}>+</button></article>`).join("") || empty("Catálogo en preparación", "Este comercio aún no tiene productos disponibles.")}</div></section><aside class="cart-panel" id="inline-cart">${cartContent()}</aside></div>`;
}
function cartContent() {
  let subtotal = state.cart.reduce((s, p) => s + p.price * p.quantity, 0);
  return `<h2>Tu bolsa <span class="muted">(${state.cart.reduce((s, p) => s + p.quantity, 0)})</span></h2>${state.cart.length ? `<p class="muted" style="font-size:12px">${escape(state.shops.find((s) => s.id === state.cartShop)?.name)}</p>${state.cart.map((p) => `<div class="cart-item"><div class="cart-product-art">${productArt(p)}</div><div><strong>${escape(p.name)}</strong><span>${money(p.price * p.quantity)}</span></div><span class="qty"><button data-action="minus" data-id="${p.id}" aria-label="Quitar una unidad de ${escape(p.name)}">−</button>${p.quantity}<button data-action="plus" data-id="${p.id}" aria-label="Agregar una unidad de ${escape(p.name)}">+</button></span></div>`).join("")}<div class="money-line total"><span>Productos</span><span>${money(subtotal)}</span></div><p class="muted" style="font-size:11px;line-height:1.7">El delivery y cualquier adicional se muestran antes de confirmar.</p><button class="button full" data-action="checkout">Continuar ↗</button><button class="link-button" data-action="clear-cart">Vaciar bolsa</button>` : '<p class="muted" style="font-size:13px;line-height:1.8;padding:20px 0">Tu próximo favorito te espera.<br>Agrega un producto para empezar.</p>'}`;
}
function updateCart() {
  state.submitKey = crypto.randomUUID();
  nav();
  if ($("#inline-cart")) $("#inline-cart").innerHTML = cartContent();
  if ($("#modal").open && $("#modal-body [data-cart-modal]"))
    $("#modal-body").innerHTML =
      `<h2 id="modal-title">Tu pedido</h2><div data-cart-modal>${cartContent()}</div>`;
}
function addProduct(id) {
  const p = state.products.find((x) => x.id === id);
  if (!p) return;
  if (state.cartShop && state.cartShop !== p.merchant_id && state.cart.length) {
    modal(
      "Un comercio por pedido",
      `<p>Tienes productos de otro comercio. Puedes completar ese pedido primero o vaciar la bolsa para cambiar.</p><button class="button full" data-action="clear-cart">Vaciar bolsa actual</button>`,
    );
    return;
  }
  state.cartShop = p.merchant_id;
  const old = state.cart.find((x) => x.id === id);
  if (old) {
    if (old.quantity >= Math.min(p.stock, 50))
      return toast("Llegaste al stock disponible.");
    old.quantity++;
  } else state.cart.push({ ...p, quantity: 1 });
  updateCart();
  toast("Agregado a tu bolsa.");
}
async function checkoutPage() {
  if (!state.cart.length)
    return empty("Tu bolsa está vacía", "Elige un comercio para comenzar.");
  if (!state.user || state.user.user_role !== "CUSTOMER")
    return `<div class="page-title"><h1>Tu pedido está casi listo.</h1><p>Ingresa con una cuenta de cliente para confirmar.</p><button class="button" data-action="login">Ingresar</button></div>`;
  const addresses = await api("/addresses");
  const z = state.zones[0];
  return `<a href="#merchant/${state.cartShop}" class="back">← Volver al comercio</a><div class="page-title"><h1>¿A dónde lo llevamos?</h1><p>Revisa tu dirección y el total antes de confirmar.</p></div><form id="checkout-form" class="catalog-layout"><div class="panel"><h2>Datos de entrega</h2>${addresses.length ? `<label class="field">Direcciones guardadas<select id="saved-address"><option value="">Ingresar otra dirección</option>${addresses.map((a) => `<option value="${a.id}" data-address="${escape(a.address_text)}" data-lat="${a.latitude}" data-lng="${a.longitude}">${escape(a.label)} — ${escape(a.address_text)}</option>`).join("")}</select></label>` : ""}<label class="field">Zona de entrega<select name="zoneId" id="zone">${state.zones.map((z) => `<option value="${z.id}">${escape(z.name)} · ${money(z.delivery_fee)}</option>`).join("")}</select></label>${field("Dirección y referencia", "address", "", "text", 'required minlength="8" maxlength="250" placeholder="Calle, número y referencia (datos ficticios)"')}<div class="field-grid">${field("Latitud del punto", "latitude", z.center_lat, "number", 'required step="any" min="-90" max="90"')}${field("Longitud del punto", "longitude", z.center_lng, "number", 'required step="any" min="-180" max="180"')}</div><p class="hint">Punto de demostración: centro de la zona seleccionada. Puedes ajustar las coordenadas. El servidor valida que el punto esté dentro de cobertura.</p><label class="check"><input name="saveAddress" type="checkbox">Guardar esta dirección (máximo tres)</label><h2 style="margin-top:30px">Método de pago</h2><label class="field">Elige cómo pagar<select name="paymentMethod"><option value="CASH">Efectivo contraentrega</option><option value="YAPE">Yape a DeliGo · verificación manual</option></select></label><p class="hint warning">Esta demo no recibe dinero. En Yape registrarás una referencia ficticia y administración simulará su conciliación. No se muestran cuentas de cobro reales.</p>${field("Propina para tu repartidor (opcional)", "tip", 0, "number", 'id="tip" min="0" max="100" step="0.10" required')}<label class="check"><input name="confirmed" type="checkbox" required>Confirmo que la dirección y el punto de entrega son correctos.</label><p class="error-text" id="checkout-error"></p></div><aside class="cart-panel"><h2>Resumen del pedido</h2>${state.cart.map((p) => `<div class="money-line"><span>${p.quantity} × ${escape(p.name)}</span><strong>${money(p.price * p.quantity)}</strong></div>`).join("")}<div id="checkout-totals">${checkoutTotals(z, 0)}</div><button class="button full" type="submit">Confirmar pedido ↗</button><p class="muted" style="font-size:11px;line-height:1.7;margin-top:15px">El servidor revalida stock, cobertura y precios al confirmar.</p></aside></form>`;
}
function checkoutTotals(z, tip) {
  const sub = state.cart.reduce((n, p) => n + p.price * p.quantity, 0),
    rain = Number(state.settings.rain_fee);
  return `<div class="money-line"><span>Delivery</span><span>${money(z.delivery_fee)}</span></div>${rain ? `<div class="money-line"><span>Adicional por lluvia</span><span>${money(rain)}</span></div>` : ""}<div class="money-line"><span>Propina</span><span>${money(tip)}</span></div><div class="money-line total"><span>Total</span><span>${money(sub + Number(z.delivery_fee) + rain + Number(tip))}</span></div>`;
}
function orderCard(o) {
  return `<article class="order-card"><div class="order-top"><span class="badge ${o.order_status === "CANCELLED" ? "cancel" : ""}">${states[o.order_status]}</span><small>${date(o.created_at)}</small></div><h3>${escape(o.merchant_name)}</h3><p>Pedido ${escape(o.id.slice(0, 8).toUpperCase())} · ${escape(o.payment_method)}</p><div class="row"><strong>${money(o.total)}</strong><a href="#order/${o.id}" class="button secondary small">Ver pedido ↗</a></div></article>`;
}
async function ordersPage() {
  if (!state.user)
    return `<div class="page-title"><h1>Tus pedidos, en un lugar.</h1><p>Ingresa para ver el estado de tus compras.</p><button class="button" data-action="login">Ingresar</button></div>`;
  const orders = await api("/orders");
  state.visibleOrders = orders;
  return `<div class="page-title"><div class="eyebrow">${roles[state.user.user_role]}</div><h1>${state.user.user_role === "CUSTOMER" ? "Tus pedidos. Sin perder el hilo." : "Pedidos de tu operación."}</h1><p>Estados registrados por los responsables. Actualiza para consultar cambios.</p><button class="button secondary small" data-action="refresh">Actualizar ↻</button></div><label class="searchbox order-search"><img src="/assets/figma/150-4866-imgSearchMagnifier.svg" alt=""><input id="order-search" type="search" aria-label="Buscar en mis pedidos" placeholder="Busca por comercio, número o estado"></label><div class="order-grid" id="order-results">${orders.map(orderCard).join("") || empty("Todavía no hay pedidos", "Aquí aparecerán tus pedidos cuando los confirmes.")}</div>`;
}
function actionButton(action, label, style = "") {
  return `<button class="button small ${style}" data-action="order-action" data-value="${action}">${label}</button>`;
}
function orderActions(o) {
  const role = state.user.user_role,
    s = o.order_status,
    p = o.payment_status;
  let a = "";
  if (role === "CUSTOMER") {
    if (s === "PENDING")
      a += actionButton("CANCEL", "Cancelar pedido", "danger");
    if (o.payment_method === "YAPE" && p === "UNPAID" && s !== "CANCELLED")
      a += actionButton("YAPE_SUBMIT", "Registrar Yape de demo");
    if (s === "DELIVERED" && !o.review.length)
      a +=
        '<button class="button small" data-action="review">Calificar pedido</button>';
  }
  if (role === "MERCHANT") {
    if (s === "PENDING" && (o.payment_method === "CASH" || p === "PAID"))
      a += actionButton("PREPARE", "Aceptar y preparar");
    else if (s === "PENDING")
      a += '<p class="hint">La preparación estará disponible cuando administración verifique el pago Yape.</p>';
    if (s === "PREPARING")
      a += actionButton("READY", "Marcar listo para recoger");
    if (["PENDING", "PREPARING"].includes(s))
      a += actionButton("CANCEL", "Cancelar con motivo", "danger");
  }
  if (role === "COURIER") {
    const flow = [
      "ASSIGNED",
      "TO_STORE",
      "AT_STORE",
      "PICKED_UP",
      "ON_THE_WAY",
      "ARRIVED",
      "DELIVERED",
    ];
    let i = flow.indexOf(s);
    if (i >= 0 && i < flow.length - 1)
      a += actionButton(flow[i + 1], states[flow[i + 1]]);
  }
  if (role === "ADMIN") {
    if (p === "VERIFYING")
      a +=
        actionButton("YAPE_VERIFY", "Confirmar conciliación") +
        actionButton("YAPE_REJECT", "Rechazar referencia", "secondary");
    if (p === "REFUND_PENDING")
      a += actionButton("REFUND", "Registrar devolución");
    if (s === "DELIVERED" && o.payment_method === "CASH" && !o.cash_rendered_at)
      a += actionButton("RENDER_CASH", "Registrar rendición");
    if (s === "DELIVERED" && p === "PAID" && !o.settled_at &&
        (o.payment_method !== "CASH" || o.cash_rendered_at))
      a += actionButton("SETTLE", "Registrar liquidación");
    if (["PENDING", "PREPARING"].includes(s))
      a += actionButton("CANCEL", "Cancelar con motivo", "danger");
  }
  return (
    a +
    '<button class="button secondary small" data-action="support">Abrir caso de soporte</button>'
  );
}
async function orderPage(id) {
  const o = await api("/orders/" + encodeURIComponent(id));
  state.order = o;
  return `<a href="#orders" class="back">← Volver a pedidos</a><div class="page-title"><div class="eyebrow">Pedido ${escape(o.id.slice(0, 8).toUpperCase())}</div><h1>${states[o.order_status]}</h1><p>${escape(o.merchant_name)} · ${date(o.created_at)}</p><button class="button secondary small" data-action="refresh">Actualizar estado ↻</button></div><div class="order-detail"><section class="stack"><div class="panel"><h2>El recorrido de tu pedido</h2><ol class="timeline">${o.events.map((e) => `<li><strong>${escape(eventLabels[e.event_type] || "Actividad del pedido")}</strong><p>${escape(e.detail)}</p><small>${date(e.created_at)}</small></li>`).join("")}</ol><div class="actions">${orderActions(o)}</div></div><div class="panel"><h2>Dirección de entrega</h2><p style="font-size:14px">${escape(o.address_text)}</p><p class="muted" style="font-size:12px">${o.latitude}, ${o.longitude}</p><a class="button secondary small" target="_blank" rel="noopener" href="https://www.openstreetmap.org/?mlat=${encodeURIComponent(o.latitude)}&mlon=${encodeURIComponent(o.longitude)}#map=17/${encodeURIComponent(o.latitude)}/${encodeURIComponent(o.longitude)}">Ver punto en OpenStreetMap ↗</a><p class="muted" style="font-size:11px;margin-top:15px">Ubicación de entrega, no seguimiento GPS del repartidor.</p></div></section><aside class="stack"><div class="panel"><h2>Tu pedido</h2>${o.items.map((p) => `<div class="money-line"><span>${p.quantity} × ${escape(p.product_name)}</span><strong>${money(p.unit_price * p.quantity)}</strong></div>`).join("")}<div class="money-line"><span>Delivery</span><span>${money(o.delivery_fee)}</span></div>${Number(o.rain_fee) ? `<div class="money-line"><span>Adicional por lluvia</span><span>${money(o.rain_fee)}</span></div>` : ""}<div class="money-line"><span>Propina</span><span>${money(o.tip)}</span></div><div class="money-line total"><span>Total</span><span>${money(o.total)}</span></div><span class="badge pay">${payments[o.payment_status]}</span><p class="muted" style="font-size:11px;margin-top:12px">${o.payment_method === "YAPE" ? "Yape centralizado en DeliGo" : "Efectivo contraentrega"}${o.payment_reference ? " · " + escape(o.payment_reference) : ""}</p>${state.user.user_role === "ADMIN" || state.user.user_role === "COURIER" ? `<div class="hint">Comercio: ${money(o.subtotal)}<br>Repartidor (incluye propina): ${money(o.courier_share)}<br>DeliGo: ${money(o.platform_share)}<br>Liquidación: ${o.settled_at ? date(o.settled_at) : "Pendiente"}</div>` : ""}</div>${o.review.length ? `<div class="panel"><h3>Tu calificación</h3><p>${["", "Malo", "Regular", "Bueno", "Excelente"][o.review[0].score]}</p><p class="muted">${escape(o.review[0].comment_text)}</p></div>` : ""}</aside></div>`;
}
async function workspacePage() {
  if (!state.user) return ordersPage();
  const role = state.user.user_role;
  if (role === "CUSTOMER") return accountPage();
  if (role === "MERCHANT") return merchantWorkspace();
  if (role === "COURIER") return courierWorkspace();
  return adminWorkspace();
}
async function accountPage() {
  const a = await api("/addresses"),
    cases = await api("/support");
  return `<div class="page-title"><h1>Tu espacio</h1><p>${escape(state.user.full_name)} · ${escape(state.user.email)}</p></div><div class="stack"><div class="panel"><h2>Direcciones guardadas</h2>${a.map((x) => `<div class="row"><p>${escape(x.label)} · ${escape(x.address_text)}</p><button class="link-button" data-action="delete-address" data-id="${x.id}">Eliminar</button></div>`).join("") || '<p class="muted">Puedes guardar una dirección durante el checkout.</p>'}</div><div class="panel"><h2>Tus casos de soporte</h2>${cases.map((c) => `<p><span class="badge">${c.case_status === "OPEN" ? "Abierto" : "Resuelto"}</span> ${escape(c.subject)}<br><small>${escape(c.resolution || "En revisión por administración.")}</small></p>`).join("") || '<p class="muted">No tienes casos abiertos.</p>'}</div></div>`;
}
async function merchantWorkspace() {
  const catalog = await api("/merchant/catalog");
  state.managed = catalog;
  return `<div class="page-title"><div class="eyebrow">Espacio del comercio</div><h1>Todo listo para atender.</h1><p>Administra tu catálogo y los pedidos que llegan a tus comercios.</p><div class="actions"><a class="button" href="#orders">Gestionar pedidos ↗</a><button class="button secondary" data-action="product-new">Agregar producto</button></div></div><div class="stack">${catalog.merchants
    .map(
      (m) =>
        `<section class="panel"><div class="section-head"><div><h2>${escape(m.name)}</h2><p>${m.is_open ? "Recibiendo pedidos" : "Cerrado para nuevos pedidos"}</p></div><button class="button secondary small" data-action="shop-open" data-id="${m.id}" data-value="${!m.is_open}">${m.is_open ? "Cerrar comercio" : "Abrir comercio"}</button></div><div class="table-wrap"><table class="data-table"><thead><tr><th>Producto</th><th>Precio</th><th>Stock</th><th>Visible</th><th>Acciones</th></tr></thead><tbody>${catalog.products
          .filter((p) => p.merchant_id === m.id)
          .map(
            (p) =>
              `<tr><td>${escape(p.name)}</td><td>${money(p.price)}</td><td>${p.stock}</td><td>${p.active ? "Sí" : "No"}</td><td><button class="link-button" data-action="product-edit" data-id="${p.id}">Editar</button><button class="link-button" data-action="product-hide" data-id="${p.id}" ${!p.active ? "disabled" : ""}>Ocultar</button></td></tr>`,
          )
          .join("")}</tbody></table></div></section>`,
    )
    .join("")}</div>`;
}
async function courierWorkspace() {
  const [available, earnings, orders] = await Promise.all([
    api("/courier/available"),
    api("/courier/earnings"),
    api("/orders"),
  ]);
  return `<div class="page-title"><div class="eyebrow">Espacio del repartidor</div><h1>Tu próxima entrega empieza aquí.</h1><p>Una entrega activa por vez. Aceptación protegida contra asignaciones simultáneas.</p><div class="actions"><button class="button ${state.user.available ? "lime" : ""}" data-action="availability">${state.user.available ? "● Disponible · Pausar" : "Activar disponibilidad"}</button><button class="button secondary" data-action="refresh">Actualizar ↻</button></div></div><div class="stats">${stat("Entregas completadas", earnings.deliveries)}${stat("Ganancias acumuladas", money(earnings.earned))}${stat("Por liquidar", money(earnings.pending))}${stat("Efectivo por rendir", money(earnings.cash_debt))}</div><div class="section-head"><h2>Tu entrega activa</h2></div><div class="order-grid">${
    orders
      .filter((o) => !["DELIVERED", "CANCELLED"].includes(o.order_status))
      .map(orderCard)
      .join("") || '<p class="hint">Aún no tienes una entrega activa.</p>'
  }</div><div class="section-head" style="margin-top:30px"><h2>Listos para recoger</h2><span class="muted">${available.length} pedidos</span></div><div class="order-grid">${available.map((o) => `<article class="order-card"><span class="badge">Listo para recoger</span><h3>${escape(o.merchant_name)}</h3><p>${escape(o.zone_name)} · Pedido ${o.id.slice(0, 8)}</p><div class="row"><strong>Ganas ${money(o.courier_share)}</strong><button class="button small" data-action="claim" data-id="${o.id}" data-version="${o.version}" ${!state.user.available ? "disabled" : ""}>Aceptar entrega</button></div></article>`).join("") || '<p class="hint">No hay pedidos listos por ahora. Actualiza en unos momentos.</p>'}</div>`;
}
function stat(label, value) {
  return `<div class="stat"><small>${label}</small><strong>${value}</strong></div>`;
}
async function adminWorkspace() {
  const d = await api("/admin/summary");
  return `<div class="page-title"><div class="eyebrow">Administración</div><h1>Una operación bajo control.</h1><p>Ventas de comercios, ingresos de plataforma y efectivo se muestran por separado.</p><div class="actions"><a class="button" href="#orders">Pedidos y conciliación ↗</a><button class="button secondary" data-action="rain">Adicional por lluvia: ${money(state.settings.rain_fee)}</button><button class="button secondary" data-action="refresh">Actualizar ↻</button></div></div><div class="stats">${stat("Pedidos registrados", d.totals.orders_count)}${stat("Ventas entregadas", money(d.totals.merchant_sales))}${stat("Comisiones de DeliGo", money(d.totals.platform_revenue))}${stat("Efectivo por rendir", money(d.totals.cash_pending))}</div><div class="stack"><section class="panel"><h2>Comercios · Acumulado</h2><div class="table-wrap"><table class="data-table"><thead><tr><th>Comercio</th><th>Pedidos</th><th>Ventas entregadas</th></tr></thead><tbody>${d.byMerchant.map((m) => `<tr><td>${escape(m.name)}</td><td>${m.orders_count}</td><td>${money(m.sales)}</td></tr>`).join("")}</tbody></table></div></section><section class="panel"><h2>Casos de soporte</h2>${d.cases.map((c) => `<div class="product"><div><span class="badge">${c.case_status === "OPEN" ? "Abierto" : "Resuelto"}</span><p style="margin:12px 0">${escape(c.subject)}</p><small>${escape(c.resolution || "Pendiente de resolución")}</small><br><a class="link-button" href="#order/${c.order_id}">Ver pedido</a></div>${c.case_status === "OPEN" ? `<button class="button secondary small" data-action="resolve-case" data-id="${c.id}">Resolver</button>` : ""}</div>`).join("") || '<p class="muted">No hay casos registrados.</p>'}</section><section class="panel"><h2>Usuarios</h2><div class="table-wrap"><table class="data-table"><thead><tr><th>Nombre</th><th>Rol</th><th>Estado</th><th>Acción</th></tr></thead><tbody>${d.users.map((u) => `<tr><td>${escape(u.full_name)}<br><small>${escape(u.email)}</small></td><td>${roles[u.user_role]}</td><td>${u.active ? "Activo" : "Suspendido"}</td><td>${u.user_role !== "ADMIN" ? `<button class="link-button" data-action="suspend" data-id="${u.id}" data-value="${!u.active}">${u.active ? "Suspender" : "Reactivar"}</button>` : "Propietario"}</td></tr>`).join("")}</tbody></table></div></section></div>`;
}
function loginModal() {
  modal(
    "Bienvenido a DeliGo",
    `<div class="welcome-art">${welcomeArt}</div><p>Lo mejor de tu barrio, en tu puerta.</p><form id="login-form">${field("Correo electrónico", "username", "", "email", 'required autocomplete="username"')}${field("Contraseña", "password", "", "password", 'required autocomplete="current-password"')}<p id="login-error" class="error-text"></p><button class="button full" type="submit">Ingresar ↗</button></form>${state.demo ? '<p class="hint" style="margin-top:20px">Cuentas públicas de demostración. Los botones completan sus credenciales de ejemplo; no uses información real.</p><div class="demo-roles"><button data-action="demo-login" data-value="customer">Cliente</button><button data-action="demo-login" data-value="merchant">Comercio</button><button data-action="demo-login" data-value="courier">Repartidor</button><button data-action="demo-login" data-value="admin">Administración</button></div>' : ""}<button class="link-button" data-action="register">Crear cuenta de cliente</button>`,
  );
}
function registerModal() {
  modal(
    "Crea tu cuenta",
    `<p>Crea una cuenta de cliente para esta demostración.</p><form id="register-form">${field("Tu nombre", "name", "", "text", 'required maxlength="100" autocomplete="name"')}${field("Correo", "email", "", "email", 'required maxlength="160" autocomplete="email"')}${field("Contraseña (12 caracteres como mínimo)", "password", "", "password", 'required minlength="12" maxlength="64" autocomplete="new-password"')}<p class="error-text" id="register-error"></p><button class="button full">Crear mi cuenta</button></form>`,
  );
}
function productModal(id) {
  const p = state.managed.products.find((p) => p.id === id);
  modal(
    p ? "Editar producto" : "Nuevo producto",
    `<form id="product-form" data-id="${id || ""}"><label class="field">Comercio<select name="merchantId" ${p ? "disabled" : ""}>${state.managed.merchants.map((m) => `<option value="${m.id}" ${p?.merchant_id === m.id ? "selected" : ""}>${escape(m.name)}</option>`).join("")}</select></label>${field("Nombre", "name", p?.name || "", "text", 'required maxlength="100"')}<label class="field">Descripción<textarea name="description" maxlength="300" required>${escape(p?.description || "")}</textarea></label><div class="field-grid">${field("Precio S/", "price", p?.price || "", "number", 'required min="0.01" max="10000" step="0.01"')}${field("Stock disponible", "stock", p?.stock ?? 0, "number", 'required min="0" max="100000" step="1"')}</div><label class="check"><input name="active" type="checkbox" ${!p || p.active ? "checked" : ""}>Visible en el catálogo</label><p class="error-text" id="product-error"></p><button class="button full">Guardar producto</button></form>`,
  );
}
const prompts = {
  CANCEL: ["Cancelar pedido", "Explica el motivo de cancelación."],
  YAPE_SUBMIT: [
    "Registrar referencia Yape",
    "Usa una referencia ficticia de 6 a 50 caracteres. Administración debe verificarla.",
  ],
  YAPE_VERIFY: [
    "Confirmar conciliación de demo",
    "Registra la constancia de revisión. Esto no consulta ni mueve dinero en Yape.",
  ],
  YAPE_REJECT: [
    "Rechazar referencia",
    "Indica por qué la referencia no corresponde.",
  ],
  REFUND: [
    "Registrar devolución de demo",
    "Registra la referencia de la devolución simulada.",
  ],
  RENDER_CASH: [
    "Registrar rendición de efectivo",
    "Confirma la recepción del total recaudado e indica la constancia.",
  ],
  SETTLE: [
    "Registrar liquidación semanal",
    "Registra la constancia del pago simulado al comercio y al repartidor.",
  ],
};
function requestAction(action) {
  const o = state.order;
  const spec = prompts[action];
  if (spec) {
    modal(
      spec[0],
      `<p>${spec[1]}</p><form id="order-action-form" data-action-name="${action}">${field("Referencia o motivo", "reason", "", "text", `required minlength="${action === "YAPE_SUBMIT" ? 6 : 5}" maxlength="${action === "YAPE_SUBMIT" ? 50 : 500}"`)}<p class="error-text" id="action-error"></p><button class="button full">Confirmar registro</button></form>`,
    );
  } else if (action === "DELIVERED" && o.payment_method === "CASH") {
    modal(
      "Confirmar entrega y cobro",
      `<p>Debes haber recibido el total: <strong>${money(o.total)}</strong>.</p><form id="order-action-form" data-action-name="DELIVERED"><label class="check"><input type="checkbox" required>Confirmo que cobré el efectivo completo y entregué el pedido.</label><input type="hidden" name="reason" value="EFECTIVO COBRADO"><p class="error-text" id="action-error"></p><button class="button full">Marcar entregado</button></form>`,
    );
  } else return doAction(action, "");
}
async function doAction(action, reason) {
  await api("/orders/" + state.order.id + "/actions", {
    method: "POST",
    body: { action, reason, version: state.order.version },
  });
  closeModal();
  toast("Pedido actualizado.");
  await route();
}
document.addEventListener("input", (e) => {
  if (e.target.id === "search") {
    state.query = e.target.value;
    $("#merchant-grid").innerHTML = merchantCards();
  }
  if (e.target.id === "order-search") {
    const query=e.target.value.toLocaleLowerCase("es").trim();
    const matches=(state.visibleOrders||[]).filter(o=>[o.id,o.merchant_name,states[o.order_status]].join(" ").toLocaleLowerCase("es").includes(query));
    $("#order-results").innerHTML=matches.map(orderCard).join("")||empty("No encontramos ese pedido","Prueba con otro comercio, número o estado.");
  }
  if (e.target.id === "tip") updateCheckout();
});
function updateCheckout() {
  const z = state.zones.find((z) => z.id === $("#zone").value);
  $("#checkout-totals").innerHTML = checkoutTotals(z, $("#tip").value || 0);
}
document.addEventListener("change", (e) => {
  if (e.target.id === "sort") {
    state.sort = e.target.value;
    $("#merchant-grid").innerHTML = merchantCards();
  }
  if (e.target.id === "zone") {
    const z = state.zones.find((z) => z.id === e.target.value);
    $('#checkout-form [name="latitude"]').value = z.center_lat;
    $('#checkout-form [name="longitude"]').value = z.center_lng;
    updateCheckout();
  }
  if (e.target.id === "saved-address") {
    const opt = e.target.selectedOptions[0];
    if (opt.dataset.address) {
      $('#checkout-form [name="address"]').value = opt.dataset.address;
      $('#checkout-form [name="latitude"]').value = opt.dataset.lat;
      $('#checkout-form [name="longitude"]').value = opt.dataset.lng;
    }
  }
});
document.addEventListener("click", async (e) => {
  const b = e.target.closest("[data-action]");
  if (!b) return;
  const a = b.dataset.action,
    id = b.dataset.id,
    v = b.dataset.value;
  try {
    if (a === "category") {
      state.category = v;
      $("#view").innerHTML = explorePage();
    } else if (a === "product-detail") productDetail(id);
    else if (a === "add") addProduct(id);
    else if (a === "plus" || a === "minus") {
      const p = state.cart.find((p) => p.id === id);
      p.quantity += a === "plus" ? 1 : -1;
      p.quantity = Math.min(p.quantity, p.stock, 50);
      state.cart = state.cart.filter((p) => p.quantity > 0);
      updateCart();
    } else if (a === "clear-cart") {
      state.cart = [];
      state.cartShop = null;
      updateCart();
      closeModal();
      if (location.hash === "#checkout") location.hash = "explore";
    } else if (a === "checkout") {
      closeModal();
      location.hash = "checkout";
    } else if (a === "login") loginModal();
    else if (a === "register") registerModal();
    else if (a === "demo-login") {
      $('#login-form [name="username"]').value = v + "@deligo.demo";
      $('#login-form [name="password"]').value = "DeligoDemo2026!";
    } else if (a === "logout") {
      await api("/auth/logout", { method: "POST" });
      state.csrf = null;
      state.cart = [];
      state.cartShop = null;
      await refreshIdentity();
      closeModal();
      location.hash = "explore";
      await route();
    } else if (a === "profile") {
      closeModal();
      location.hash = "workspace";
      await route();
    } else if (a === "refresh") {
      await refreshIdentity();
      state.settings = await api("/catalog/settings");
      state.shops = await api("/catalog/merchants");
      await route();
    } else if (a === "order-action") await requestAction(v);
    else if (a === "review")
      modal(
        "¿Cómo estuvo tu pedido?",
        `<form id="review-form"><label class="field">Calificación<select name="score"><option value="4">Excelente</option><option value="3">Bueno</option><option value="2">Regular</option><option value="1">Malo</option></select></label><label class="field">Comentario opcional<textarea name="comment" maxlength="500"></textarea></label><p class="error-text" id="review-error"></p><button class="button full">Guardar calificación</button></form>`,
      );
    else if (a === "support")
      modal(
        "Estamos para ayudarte.",
        `<p>Cuéntanos qué pasó con este pedido.</p><form id="support-form"><label class="field">Describe el problema<textarea name="subject" required minlength="10" maxlength="500"></textarea></label><p class="error-text" id="support-error"></p><button class="button full">Crear caso</button></form>`,
      );
    else if (a === "availability") {
      await api("/courier/availability", {
        method: "PUT",
        body: { available: !state.user.available },
      });
      await refreshIdentity();
      await route();
    } else if (a === "claim") {
      b.disabled = true;
      await api("/orders/" + id + "/actions", {
        method: "POST",
        body: {
          action: "CLAIM",
          version: Number(b.dataset.version),
          reason: "",
        },
      });
      location.hash = "order/" + id;
    } else if (a === "product-new") productModal();
    else if (a === "product-edit") productModal(id);
    else if (a === "product-hide") {
      await api("/merchant/products/" + id, { method: "DELETE" });
      await route();
    } else if (a === "shop-open") {
      await api("/merchant/" + id + "/open", {
        method: "PUT",
        body: { open: v === "true" },
      });
      state.shops = await api("/catalog/merchants");
      await route();
    } else if (a === "suspend") {
      await api("/admin/users/" + id, {
        method: "PUT",
        body: { active: v === "true" },
      });
      await route();
    } else if (a === "delete-address") {
      await api("/addresses/" + id, { method: "DELETE" });
      await route();
    } else if (a === "rain")
      modal(
        "Adicional por lluvia",
        `<p>Solo afecta a pedidos nuevos. El cliente lo ve como un concepto separado.</p><form id="rain-form">${field("Monto S/ (0 desactiva)", "amount", state.settings.rain_fee, "number", 'required min="0" max="20" step="0.10"')}<p class="error-text" id="rain-error"></p><button class="button full">Guardar tarifa</button></form>`,
      );
    else if (a === "resolve-case")
      modal(
        "Resolver caso",
        `<form id="case-form" data-id="${id}"><label class="field">Resolución<textarea name="resolution" required minlength="10" maxlength="500"></textarea></label><p class="error-text" id="case-error"></p><button class="button full">Guardar resolución</button></form>`,
      );
  } catch (error) {
    toast(error.message);
    b.disabled = false;
  }
});
document.addEventListener("submit", async (e) => {
  const f = e.target;
  if (!f.id) return;
  e.preventDefault();
  const data = Object.fromEntries(new FormData(f));
  const button = f.querySelector('button[type="submit"],button.button');
  if (button) button.disabled = true;
  const error = f.querySelector(".error-text");
  if (error) error.textContent = "";
  try {
    if (f.id === "login-form") {
      await token();
      const r = await fetch("/api/auth/login", {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded",
          [state.csrf.header]: state.csrf.token,
        },
        body: new URLSearchParams(data),
      });
      if (!r.ok) throw new Error("Correo o contraseña incorrectos.");
      state.csrf = null;
      await refreshIdentity();
      closeModal();
      if (state.user.user_role !== "CUSTOMER") location.hash = "workspace";
      await route();
    } else if (f.id === "register-form") {
      await api("/auth/register", { method: "POST", body: data });
      toast("Cuenta creada. Ahora puedes ingresar.");
      loginModal();
    } else if (f.id === "checkout-form") {
      const payload = {
        merchantId: state.cartShop,
        zoneId: data.zoneId,
        address: data.address,
        latitude: Number(data.latitude),
        longitude: Number(data.longitude),
        paymentMethod: data.paymentMethod,
        tip: Number(data.tip),
        expectedTotal: Number(
          (
            state.cart.reduce((n, p) => n + p.price * p.quantity, 0) +
            Number(state.zones.find((z) => z.id === data.zoneId).delivery_fee) +
            Number(state.settings.rain_fee) +
            Number(data.tip)
          ).toFixed(2),
        ),
        idempotencyKey: state.submitKey,
        items: state.cart.map((p) => ({
          productId: p.id,
          quantity: p.quantity,
        })),
      };
      const order = await api("/orders", { method: "POST", body: payload });
      if (data.saveAddress) {
        try {
          await api("/addresses", {
            method: "POST",
            body: {
              label: "Mi dirección",
              address: data.address,
              latitude: Number(data.latitude),
              longitude: Number(data.longitude),
            },
          });
        } catch (err) {
          toast("Pedido creado. " + err.message);
        }
      }
      state.cart = [];
      state.cartShop = null;
      updateCart();
      location.hash = "order/" + order.id;
    } else if (f.id === "order-action-form")
      await doAction(f.dataset.actionName, data.reason);
    else if (f.id === "review-form") {
      await api("/orders/" + state.order.id + "/review", {
        method: "POST",
        body: { score: Number(data.score), comment: data.comment },
      });
      closeModal();
      await route();
      toast("Gracias por tu calificación.");
    } else if (f.id === "support-form") {
      await api("/orders/" + state.order.id + "/support", {
        method: "POST",
        body: data,
      });
      closeModal();
      toast("Caso registrado. Administración podrá responderlo.");
    } else if (f.id === "product-form") {
      const p = state.managed.products.find((p) => p.id === f.dataset.id);
      await api("/merchant/products" + (p ? "/" + p.id : ""), {
        method: p ? "PUT" : "POST",
        body: {
          merchantId: p?.merchant_id || data.merchantId,
          name: data.name,
          description: data.description,
          price: Number(data.price),
          stock: Number(data.stock),
          active: !!data.active,
        },
      });
      closeModal();
      await route();
      toast("Producto guardado.");
    } else if (f.id === "rain-form") {
      await api("/admin/rain", {
        method: "PUT",
        body: { amount: Number(data.amount) },
      });
      state.settings = await api("/catalog/settings");
      closeModal();
      await route();
    } else if (f.id === "case-form") {
      await api("/admin/cases/" + f.dataset.id, { method: "PUT", body: data });
      closeModal();
      await route();
    }
  } catch (err) {
    if (error) error.textContent = err.message;
    else toast(err.message);
  } finally {
    if (button) button.disabled = false;
  }
});
$("#close-modal").addEventListener("click", closeModal);
$("#account-button").addEventListener("click", () => {
  if (!state.user) return loginModal();
  modal(
    "Hola, " + state.user.full_name.split(" ")[0] + ".",
    `<p>${escape(state.user.email)}<br>${roles[state.user.user_role]}</p><div class="actions"><button class="button" data-action="profile">Mi espacio</button><button class="button secondary" data-action="logout">Cerrar sesión</button></div>`,
  );
});
$("#cart-button").addEventListener("click", () =>
  (location.hash = "cart"),
);
window.addEventListener("hashchange", () => {
  if (location.hash === "#merchants") {
    document
      .getElementById("merchants")
      ?.scrollIntoView({ behavior: "smooth" });
    return;
  }
  closeModal();
  route();
  window.scrollTo(0, 0);
});
async function init() {
  try {
    const [shops, zones, settings] = await Promise.all([
      api("/catalog/merchants"),
      api("/catalog/zones"),
      api("/catalog/settings"),
    ]);
    Object.assign(state, { shops, zones, settings });
    await refreshIdentity();
    await route();
  } catch (e) {
    $("#view").innerHTML = empty(
      "No pudimos conectar con DeliGo",
      escape(e.message),
    );
  }
}
init();

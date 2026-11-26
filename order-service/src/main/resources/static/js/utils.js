/* Shared by every page: session, API access, and the small UI pieces
   (toasts, confirm dialog, busy buttons, product tiles) that replace alert/confirm/prompt. */
const App = (() => {
    const TOKEN_KEY = "token";
    const ROLE_KEY = "role";

    // ---------- Formatting ----------

    const moneyFormat = new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" });
    const money = value => moneyFormat.format(Number(value) || 0);

    const ESCAPES = { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" };
    /** Everything from the server passes through this before it reaches innerHTML. */
    const esc = value => String(value ?? "").replace(/[&<>"']/g, c => ESCAPES[c]);

    const icon = (name, extraClass = "") =>
        `<svg class="icon ${extraClass}" aria-hidden="true"><use href="#i-${name}"/></svg>`;

    // ---------- Session ----------

    function parseJwt(token) {
        try {
            const base64 = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
            const json = decodeURIComponent(atob(base64).split("")
                .map(c => "%" + ("00" + c.charCodeAt(0).toString(16)).slice(-2)).join(""));
            return JSON.parse(json);
        } catch (e) {
            return null;
        }
    }

    const auth = {
        token: () => localStorage.getItem(TOKEN_KEY),

        setToken(token) {
            localStorage.setItem(TOKEN_KEY, token);
            sessionStorage.removeItem(ROLE_KEY);
        },

        payload() {
            const token = auth.token();
            return token ? parseJwt(token) : null;
        },

        isSignedIn() {
            const payload = auth.payload();
            return Boolean(payload && payload.exp && payload.exp > Date.now() / 1000);
        },

        username: () => auth.payload()?.sub || "",

        signOut(next) {
            localStorage.removeItem(TOKEN_KEY);
            sessionStorage.removeItem(ROLE_KEY);
            window.location.href = next ? "/signin?next=" + encodeURIComponent(next) : "/signin";
        },

        /** For pages that are useless to a guest. Returns false while redirecting. */
        requireSignIn() {
            if (auth.isSignedIn()) return true;
            auth.signOut(window.location.pathname);
            return false;
        },

        /** Asked once per session; the server stays the judge of what an admin may do. */
        async isAdmin() {
            if (!auth.isSignedIn()) return false;
            let role = sessionStorage.getItem(ROLE_KEY);
            if (!role) {
                try {
                    role = (await api("/api/auth/me")).role || "";
                    sessionStorage.setItem(ROLE_KEY, role);
                } catch (e) {
                    return false;
                }
            }
            return role === "ROLE_ADMIN" || role === "ADMIN";
        }
    };

    // ---------- API ----------

    class ApiError extends Error {
        constructor(status, message, fields) {
            super(message);
            this.status = status;
            this.fields = fields || {};
        }
    }

    const DEFAULT_MESSAGES = {
        0: "Can't reach the server. Check your connection and try again.",
        403: "You don't have access to this.",
        404: "That could not be found.",
        409: "That is no longer possible. Refresh and try again."
    };

    /** The backend answers with plain text, {error, message} or {field: message}. */
    function readError(status, data) {
        if (typeof data === "string" && data.trim()) {
            return { message: data.replace(/^Error:\s*/i, "") };
        }
        if (data && typeof data === "object") {
            if (data.message) return { message: String(data.message) };
            const fields = Object.fromEntries(Object.entries(data).filter(([, v]) => typeof v === "string"));
            if (Object.keys(fields).length) {
                return { message: Object.values(fields).join(" "), fields };
            }
        }
        return { message: DEFAULT_MESSAGES[status] || "Something went wrong. Please try again." };
    }

    async function api(path, { method = "GET", body, anonymous = false } = {}) {
        const headers = {};
        if (body !== undefined) headers["Content-Type"] = "application/json";
        const token = auth.token();
        if (!anonymous && token) headers["Authorization"] = "Bearer " + token;

        let res;
        try {
            res = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
        } catch (e) {
            throw new ApiError(0, DEFAULT_MESSAGES[0]);
        }

        const text = await res.text();
        let data = null;
        if (text) {
            try { data = JSON.parse(text); } catch (e) { data = text; }
        }
        if (res.ok) return data;

        // An expired session answers 401 or 403; send the customer back here after signing in.
        if (!anonymous && (res.status === 401 || res.status === 403) && !auth.isSignedIn()) {
            auth.signOut(window.location.pathname);
        }
        const { message, fields } = readError(res.status, data);
        throw new ApiError(res.status, message, fields);
    }

    // ---------- Cart and catalogue, fetched at most once per page ----------

    let cartPromise = null;
    let catalogPromise = null;

    const cart = {
        get() {
            if (!cartPromise) cartPromise = api("/api/cart").then(cart.sync);
            return cartPromise;
        },
        /** Every cart endpoint returns the whole cart; pass it here to keep the header honest. */
        sync(dto) {
            cartPromise = Promise.resolve(dto);
            const badge = document.getElementById("cartCount");
            if (badge) {
                const count = dto?.totalQuantity || 0;
                badge.hidden = count === 0;
                badge.innerHTML = `${count}<span class="visually-hidden"> items in cart</span>`;
            }
            return dto;
        },
        async add(productId, quantity) {
            return cart.sync(await api("/api/cart/add", { method: "POST", body: { productId, quantity } }));
        }
    };

    const catalog = {
        get() {
            if (!catalogPromise) {
                catalogPromise = api("/api/products", { anonymous: true }).then(data => data.products || []);
            }
            return catalogPromise;
        }
    };

    // ---------- Product presentation ----------

    const CATEGORY_MARKS = {
        "Accessories": ["bag", 0],
        "Electronics": ["headphones", 1],
        "Office Supplies": ["briefcase", 2],
        "Stationery": ["pencil", 3],
        "Tech Gadgets": ["cpu", 4]
    };

    /** The catalogue has no photos, so a product is shown by its category mark and SKU. */
    function productMedia(product, { thumb = false } = {}) {
        const [mark, tint] = CATEGORY_MARKS[product?.category] || ["package", 4];
        const sku = product?.productID != null && !thumb
            ? `<span class="media__sku">SKU ${esc(String(product.productID).padStart(3, "0"))}</span>`
            : "";
        return `<div class="media media--c${tint}${thumb ? " media--thumb" : ""}">${sku}${icon(mark)}</div>`;
    }

    const LOW_STOCK = 10;

    function stockLabel(quantity) {
        if (quantity <= 0) return `<span class="stock stock--out">Out of stock</span>`;
        if (quantity <= LOW_STOCK) return `<span class="stock stock--low">Only ${quantity} left</span>`;
        return `<span class="stock stock--in">In stock</span>`;
    }

    // ---------- Feedback ----------

    const TOAST_ICONS = { success: "check-circle", error: "alert", info: "info" };

    function toast(message, { type = "success", action } = {}) {
        const region = document.getElementById("toasts");
        if (!region) return;

        const el = document.createElement("div");
        el.className = `toast toast--${type}`;
        el.innerHTML = `
            ${icon(TOAST_ICONS[type] || "info")}
            <div class="toast__body">${esc(message)}</div>
            ${action ? `<a class="toast__action" href="${esc(action.href)}">${esc(action.label)}</a>` : ""}
            <button type="button" class="toast__close" aria-label="Dismiss">${icon("x")}</button>`;

        const dismiss = () => {
            el.classList.add("is-leaving");
            setTimeout(() => el.remove(), 160);
        };
        el.querySelector(".toast__close").addEventListener("click", dismiss);
        // Errors stay longer: they usually need reading twice.
        setTimeout(dismiss, type === "error" ? 7000 : 4500);
        region.appendChild(el);
    }

    /** Resolves true when the customer confirms. Escape and the backdrop both mean no. */
    function confirm({ title, body, confirmLabel = "Confirm", cancelLabel = "Cancel" }) {
        const dialog = document.getElementById("confirmDialog");
        const ok = dialog.querySelector("[data-dialog-confirm]");
        const cancel = dialog.querySelector("[data-dialog-cancel]");
        dialog.querySelector("#confirmTitle").textContent = title;
        dialog.querySelector("#confirmBody").textContent = body;
        ok.textContent = confirmLabel;
        cancel.textContent = cancelLabel;

        return new Promise(resolve => {
            const finish = value => {
                ok.removeEventListener("click", onOk);
                cancel.removeEventListener("click", onCancel);
                dialog.removeEventListener("close", onClose);
                dialog.removeEventListener("click", onBackdrop);
                if (dialog.open) dialog.close();
                resolve(value);
            };
            const onOk = () => finish(true);
            const onCancel = () => finish(false);
            const onClose = () => finish(false);
            const onBackdrop = event => { if (event.target === dialog) finish(false); };

            ok.addEventListener("click", onOk);
            cancel.addEventListener("click", onCancel);
            dialog.addEventListener("close", onClose);
            dialog.addEventListener("click", onBackdrop);
            dialog.showModal();
            cancel.focus();
        });
    }

    /** Disables a button for the length of a request and says what is happening. */
    function setBusy(button, busy, label) {
        if (!button) return;
        if (busy) {
            if (button.dataset.idle === undefined) button.dataset.idle = button.innerHTML;
            button.disabled = true;
            button.setAttribute("aria-busy", "true");
            button.innerHTML = `<span class="spinner" aria-hidden="true"></span>${esc(label || "Working…")}`;
        } else {
            button.disabled = false;
            button.removeAttribute("aria-busy");
            if (button.dataset.idle !== undefined) {
                button.innerHTML = button.dataset.idle;
                delete button.dataset.idle;
            }
        }
    }

    function emptyState({ icon: name, title, body, action }) {
        return `
            <div class="empty">
              <div class="empty__icon">${icon(name)}</div>
              <h2>${esc(title)}</h2>
              <p>${esc(body)}</p>
              ${action ? `<a class="btn btn--primary" href="${esc(action.href)}">${esc(action.label)}</a>` : ""}
            </div>`;
    }

    function errorState(message, retryId) {
        return `
            <div class="empty">
              <div class="empty__icon">${icon("alert")}</div>
              <h2>That didn't load</h2>
              <p>${esc(message)}</p>
              <button type="button" class="btn btn--secondary" id="${esc(retryId)}">${icon("refresh")}Try again</button>
            </div>`;
    }

    // ---------- Form errors: next to the field, announced, and linked to it ----------

    function setFieldError(input, message) {
        const field = input.closest(".field");
        if (!field) return;
        let error = field.querySelector(".field__error");
        if (!message) {
            input.removeAttribute("aria-invalid");
            if (error) error.remove();
            restoreDescription(input);
            return;
        }
        if (!error) {
            error = document.createElement("p");
            error.className = "field__error";
            error.id = input.id + "Error";
            field.appendChild(error);
        }
        error.innerHTML = `${icon("alert")}<span>${esc(message)}</span>`;
        input.setAttribute("aria-invalid", "true");
        const hint = input.dataset.hint ? input.dataset.hint + " " : "";
        input.setAttribute("aria-describedby", hint + error.id);
    }

    function restoreDescription(input) {
        if (input.dataset.hint) input.setAttribute("aria-describedby", input.dataset.hint);
        else input.removeAttribute("aria-describedby");
    }

    function clearFieldErrors(form) {
        form.querySelectorAll("[aria-invalid='true']").forEach(input => setFieldError(input, ""));
    }

    function showFormAlert(container, html) {
        container.innerHTML = html
            ? `<div class="alert alert--error" role="alert">${icon("alert")}<div>${html}</div></div>`
            : "";
    }

    /** Show/hide buttons next to password fields. */
    function wirePasswordToggles(root = document) {
        root.querySelectorAll("[data-toggle-password]").forEach(button => {
            const input = document.getElementById(button.dataset.togglePassword);
            button.addEventListener("click", () => {
                const reveal = input.type === "password";
                input.type = reveal ? "text" : "password";
                button.setAttribute("aria-label", reveal ? "Hide password" : "Show password");
                button.setAttribute("aria-pressed", String(reveal));
                button.innerHTML = icon(reveal ? "eye-off" : "eye");
            });
        });
    }

    // ---------- Header ----------

    function initHeader() {
        const path = window.location.pathname;
        document.querySelectorAll("[data-nav]").forEach(link => {
            if (link.dataset.nav.split(" ").some(prefix => path === prefix || path.startsWith(prefix + "/"))) {
                link.setAttribute("aria-current", "page");
            }
        });

        document.getElementById("signOutButton")?.addEventListener("click", () => auth.signOut());

        if (!auth.isSignedIn()) return;

        document.getElementById("userName").textContent = auth.username();
        document.getElementById("userGreeting").hidden = false;
        document.getElementById("signOutButton").hidden = false;
        document.getElementById("signInLink").hidden = true;

        cart.get().catch(() => { /* the badge is a convenience; pages report their own errors */ });
        auth.isAdmin().then(admin => {
            if (admin) document.getElementById("adminLink").hidden = false;
        });
    }

    document.addEventListener("DOMContentLoaded", initHeader);

    return {
        auth, api, ApiError, cart, catalog,
        ui: {
            money, esc, icon, productMedia, stockLabel, toast, confirm, setBusy,
            emptyState, errorState, setFieldError, clearFieldErrors, showFormAlert, wirePasswordToggles
        }
    };
})();

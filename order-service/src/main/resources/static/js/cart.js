/* Cart: quantities change in place, and every answer from the server redraws the page. */
(() => {
    const { api, auth, cart, catalog, ui } = App;
    if (!auth.requireSignIn()) return;

    const view = document.getElementById("cartView");
    const lead = document.getElementById("cartLead");

    /** Cart lines only carry a name and id; the catalogue adds category and stock. */
    let productsById = {};

    function line(item) {
        const product = productsById[item.product.productID] || item.product;
        const id = ui.esc(item.product.productID);
        const unit = Number(item.price) / item.quantity;
        const atLimit = product.quantity !== undefined && item.quantity >= product.quantity;
        return `
            <li class="line">
              ${ui.productMedia(product, { thumb: true })}
              <div>
                <a class="line__name" href="/product/${encodeURIComponent(item.product.productID)}">${ui.esc(item.product.name)}</a>
                <p class="muted small num">${ui.money(unit)} each</p>
              </div>
              <div class="line__controls">
                <div class="stepper" role="group" aria-label="Quantity of ${ui.esc(item.product.name)}">
                  <button type="button" class="stepper__btn" data-qty="${item.quantity - 1}" data-id="${id}"
                          aria-label="Decrease quantity" ${item.quantity <= 1 ? "disabled" : ""}>${ui.icon("minus")}</button>
                  <span class="stepper__value">${item.quantity}</span>
                  <button type="button" class="stepper__btn" data-qty="${item.quantity + 1}" data-id="${id}"
                          aria-label="Increase quantity" ${atLimit ? "disabled" : ""}>${ui.icon("plus")}</button>
                </div>
                <span class="line__total">${ui.money(item.price)}</span>
                <button type="button" class="btn btn--ghost btn--icon" data-remove="${id}"
                        aria-label="Remove ${ui.esc(item.product.name)} from cart">${ui.icon("trash")}</button>
              </div>
            </li>`;
    }

    function render(data) {
        const items = data?.cartItems || [];
        view.removeAttribute("aria-busy");

        if (items.length === 0) {
            lead.textContent = "";
            view.innerHTML = `<div class="card">${ui.emptyState({
                icon: "cart",
                title: "Your cart is empty",
                body: "Add something from the catalog and it will wait for you here.",
                action: { label: "Browse the catalog", href: "/catalog" }
            })}</div>`;
            return;
        }

        const units = data.totalQuantity;
        lead.textContent = `${units} ${units === 1 ? "item" : "items"}, ready when you are.`;
        view.innerHTML = `
            <div class="split">
              <div class="card">
                <ul class="line-list">${items.map(line).join("")}</ul>
              </div>
              <aside class="card split__aside" aria-label="Order summary">
                <div class="card__body">
                  <h2>Summary</h2>
                  <div class="summary-row"><span class="muted">Items</span><span class="num">${units}</span></div>
                  <div class="summary-row summary-row--total"><span>Subtotal</span><span class="num">${ui.money(data.totalPrice)}</span></div>
                  <div class="stack" style="margin-top: var(--space-4)">
                    <a class="btn btn--buy btn--lg btn--block" href="/checkout">Go to checkout${ui.icon("arrow-right")}</a>
                    <button type="button" class="btn btn--danger-quiet btn--block" id="clearCart">Clear cart</button>
                  </div>
                </div>
              </aside>
            </div>`;
    }

    /** Runs a cart change; the controls stay locked until the server has answered. */
    async function change(request) {
        view.querySelectorAll("button").forEach(button => { button.disabled = true; });
        try {
            render(cart.sync(await request()));
        } catch (err) {
            ui.toast(err.message, { type: "error" });
            render(await cart.get());
        }
    }

    view.addEventListener("click", async event => {
        const step = event.target.closest("[data-qty]");
        if (step) {
            return change(() => api("/api/cart/update", {
                method: "PUT",
                body: { productId: step.dataset.id, quantity: Number(step.dataset.qty) }
            }));
        }

        const remove = event.target.closest("[data-remove]");
        if (remove) {
            return change(() => api("/api/cart/remove/" + encodeURIComponent(remove.dataset.remove), { method: "DELETE" }));
        }

        if (event.target.closest("#clearCart")) {
            const yes = await ui.confirm({
                title: "Clear your cart?",
                body: "Every item will be removed. You can add them again from the catalog.",
                confirmLabel: "Clear cart",
                cancelLabel: "Keep items"
            });
            if (yes) change(() => api("/api/cart/clear", { method: "DELETE" }));
        }

        if (event.target.closest("#retryLoad")) window.location.reload();
    });

    async function load() {
        try {
            const [data, products] = await Promise.all([cart.get(), catalog.get().catch(() => [])]);
            productsById = Object.fromEntries(products.map(p => [p.productID, p]));
            render(data);
        } catch (err) {
            view.removeAttribute("aria-busy");
            view.innerHTML = `<div class="card">${ui.errorState(err.message, "retryLoad")}</div>`;
        }
    }

    load();
})();

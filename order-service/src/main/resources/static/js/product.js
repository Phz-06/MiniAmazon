/* Product page. Open to guests; signing in is only asked for when adding to the cart. */
(() => {
    const { api, auth, cart, ui } = App;

    const view = document.getElementById("productView");
    const productId = decodeURIComponent(window.location.pathname.split("/")[2] || "");

    let product = null;
    let quantity = 1;

    function render() {
        const soldOut = product.quantity <= 0;
        const warehouse = product.warehouse;
        // Lombok names the getters getXCoordinate, which Jackson writes as "xcoordinate".
        const wx = warehouse?.xCoordinate ?? warehouse?.xcoordinate;
        const wy = warehouse?.yCoordinate ?? warehouse?.ycoordinate;

        document.title = `${product.name} · MiniAmazon`;
        view.removeAttribute("aria-busy");
        view.innerHTML = `
            ${ui.productMedia(product)}
            <div class="product__info">
              <div>
                <p class="muted small">${ui.esc(product.category)}</p>
                <h1>${ui.esc(product.name)}</h1>
              </div>
              <div>
                <p class="product__price num">${ui.money(product.price)}</p>
                ${ui.stockLabel(product.quantity)}
              </div>
              <p>${ui.esc(product.description)}</p>

              <div class="product__buy">
                <div class="stepper" role="group" aria-label="Quantity">
                  <button type="button" class="stepper__btn" id="qtyDown" aria-label="Decrease quantity">${ui.icon("minus")}</button>
                  <span class="stepper__value" id="qtyValue" aria-live="polite">1</span>
                  <button type="button" class="stepper__btn" id="qtyUp" aria-label="Increase quantity">${ui.icon("plus")}</button>
                </div>
                <button type="button" class="btn btn--buy btn--lg" id="addToCart" ${soldOut ? "disabled" : ""}>
                  ${ui.icon("cart")}${soldOut ? "Out of stock" : "Add to cart"}
                </button>
              </div>

              <dl class="facts">
                <div><dt>SKU</dt><dd class="mono">${ui.esc(String(product.productID).padStart(3, "0"))}</dd></div>
                <div><dt>Available</dt><dd class="num">${product.quantity} in the warehouse</dd></div>
                ${warehouse ? `<div><dt>Ships from</dt><dd>${ui.esc(warehouse.name || "Warehouse " + warehouse.id)}${
                    wx !== undefined ? ` <span class="muted num">(${wx}, ${wy})</span>` : ""}</dd></div>` : ""}
              </dl>
            </div>`;
        syncQuantity();
    }

    function syncQuantity() {
        const max = Math.max(product.quantity, 1);
        quantity = Math.min(Math.max(quantity, 1), max);
        document.getElementById("qtyValue").textContent = quantity;
        document.getElementById("qtyDown").disabled = quantity <= 1;
        document.getElementById("qtyUp").disabled = quantity >= max || product.quantity <= 0;
    }

    async function addToCart(button) {
        if (!auth.isSignedIn()) {
            auth.signOut(window.location.pathname);
            return;
        }
        ui.setBusy(button, true, "Adding to cart…");
        try {
            await cart.add(productId, quantity);
            ui.toast(`${quantity} × ${product.name} added to your cart.`, { action: { label: "View cart", href: "/cart" } });
        } catch (err) {
            ui.toast(err.message, { type: "error" });
        } finally {
            ui.setBusy(button, false);
        }
    }

    async function load() {
        try {
            product = await api(`/api/products/${encodeURIComponent(productId)}`, { anonymous: true });
            if (!product) throw new App.ApiError(404, "This product is not in the catalog.");
            render();
        } catch (err) {
            view.removeAttribute("aria-busy");
            view.style.display = "block";
            view.innerHTML = ui.emptyState({
                icon: "package",
                title: "Product not found",
                body: err.message,
                action: { label: "Browse the catalog", href: "/catalog" }
            });
        }
    }

    view.addEventListener("click", event => {
        if (event.target.closest("#qtyDown")) { quantity -= 1; syncQuantity(); }
        if (event.target.closest("#qtyUp")) { quantity += 1; syncQuantity(); }
        const add = event.target.closest("#addToCart");
        if (add) addToCart(add);
    });

    load();
})();

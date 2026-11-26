/* Checkout. Placing an order is three calls: create it from the cart, confirm it
   (which reserves stock), and pay. Each can fail on its own, so each is reported on its own. */
(() => {
    const { api, auth, cart, catalog, ui } = App;
    if (!auth.requireSignIn()) return;

    const form = document.getElementById("checkoutForm");
    const alertBox = document.getElementById("formAlert");
    const placeButton = document.getElementById("placeOrder");
    const placeStatus = document.getElementById("placeStatus");
    const xInput = document.getElementById("deliveryX");
    const yInput = document.getElementById("deliveryY");
    const upsInput = document.getElementById("upsAccountName");
    const cardInput = document.getElementById("creditCardNumber");

    let warehouse = null;

    // ---------- Validation: on blur, and again on submit ----------

    const wholeNumber = input => /^-?\d+$/.test(input.value.trim());

    const RULES = [
        [xInput, () => wholeNumber(xInput) ? "" : "Enter the X coordinate as a whole number, for example 12."],
        [yInput, () => wholeNumber(yInput) ? "" : "Enter the Y coordinate as a whole number, for example 8."],
        [cardInput, () => cardInput.value.trim() ? "" : "Enter a card number. Any number works here; nothing is charged."]
    ];

    function validate(input, rule) {
        const message = rule();
        ui.setFieldError(input, message);
        return !message;
    }

    RULES.forEach(([input, rule]) => {
        input.addEventListener("blur", () => { if (input.value.trim()) validate(input, rule); });
        input.addEventListener("input", () => { if (input.getAttribute("aria-invalid")) validate(input, rule); });
    });

    // ---------- Route sketch: warehouse to drop-off, scaled to fit ----------

    const H = 220, PAD = 60;

    function drawRoute() {
        if (!warehouse) return;
        // One unit of the sketch is one CSS pixel, so labels stay readable on a phone.
        const map = document.getElementById("routeMap");
        document.getElementById("route").hidden = false;
        const W = Math.max(map.clientWidth, 280);
        map.setAttribute("viewBox", `0 0 ${W} ${H}`);
        const dest = wholeNumber(xInput) && wholeNumber(yInput)
            ? { x: Number(xInput.value), y: Number(yInput.value) }
            : null;
        const points = dest ? [warehouse, dest] : [warehouse];
        const xs = points.map(p => p.x), ys = points.map(p => p.y);
        const midX = (Math.min(...xs) + Math.max(...xs)) / 2;
        const midY = (Math.min(...ys) + Math.max(...ys)) / 2;
        const scale = Math.min(
            (W - 2 * PAD) / Math.max(Math.max(...xs) - Math.min(...xs), 1),
            (H - 2 * PAD) / Math.max(Math.max(...ys) - Math.min(...ys), 1));
        // Grid y grows upward, screen y grows downward.
        const sx = p => W / 2 + (p.x - midX) * scale;
        const sy = p => H / 2 - (p.y - midY) * scale;

        const label = (p, title, above) => {
            const x = Math.min(Math.max(sx(p), 70), W - 70);
            const y = sy(p) + (above ? -30 : 28);
            return `
                <text class="route__label" x="${x}" y="${y}" text-anchor="middle">${title}</text>
                <text class="route__sub" x="${x}" y="${y + 14}" text-anchor="middle">(${p.x}, ${p.y})</text>`;
        };

        const apart = dest && (dest.x !== warehouse.x || dest.y !== warehouse.y);
        const warehouseAbove = apart ? sy(warehouse) < sy(dest) : true;

        map.innerHTML = `
            <defs>
              <pattern id="routeGrid" width="28" height="28" patternUnits="userSpaceOnUse">
                <path class="route__grid" d="M28 0H0V28" fill="none"/>
              </pattern>
            </defs>
            <rect width="${W}" height="${H}" fill="url(#routeGrid)"/>
            ${apart ? `<path class="route__path" d="M${sx(warehouse)} ${sy(warehouse)} L${sx(dest)} ${sy(dest)}"/>` : ""}
            <rect class="route__wh" x="${sx(warehouse) - 8}" y="${sy(warehouse) - 8}" width="16" height="16" rx="3"/>
            ${label(warehouse, "Warehouse", warehouseAbove)}
            ${dest ? `<circle class="route__dest" cx="${sx(dest)}" cy="${sy(dest)}" r="8"/>
                      ${label(dest, "Drop-off", !warehouseAbove)}` : ""}`;

        const caption = document.getElementById("routeCaption");
        if (!dest) {
            caption.textContent = `Orders ship from the warehouse at (${warehouse.x}, ${warehouse.y}). Enter X and Y to see the drop-off point.`;
        } else {
            const distance = Math.hypot(dest.x - warehouse.x, dest.y - warehouse.y);
            caption.textContent = `Drop-off at (${dest.x}, ${dest.y}), about ${distance.toFixed(1)} grid units from the warehouse in a straight line.`;
        }
    }

    xInput.addEventListener("input", drawRoute);
    yInput.addEventListener("input", drawRoute);
    window.addEventListener("resize", drawRoute);

    // ---------- Summary ----------

    function renderSummary(data) {
        const items = data?.cartItems || [];
        if (items.length === 0) {
            document.getElementById("checkoutView").hidden = true;
            const empty = document.getElementById("checkoutEmpty");
            empty.hidden = false;
            empty.innerHTML = ui.emptyState({
                icon: "cart",
                title: "Nothing to check out",
                body: "Your cart is empty. Add something from the catalog first.",
                action: { label: "Browse the catalog", href: "/catalog" }
            });
            return;
        }
        document.getElementById("cartSummary").innerHTML = items.map(item => `
            <li class="line line--compact">
              <span>${ui.esc(item.product.name)} <span class="muted num">× ${item.quantity}</span></span>
              <span class="num">${ui.money(item.price)}</span>
            </li>`).join("");
        document.getElementById("cartTotal").textContent = ui.money(data.totalPrice);
        placeButton.disabled = false;
    }

    // ---------- Place the order ----------

    /** After the first call the order exists and the cart is spent, so the form cannot simply be sent again. */
    function orderLeftBehind(orderId, what, err) {
        ui.showFormAlert(alertBox, `
            <strong>Order #${ui.esc(orderId)} was created, but ${what}.</strong>
            ${ui.esc(/[.!?]$/.test(err.message) ? err.message : err.message + ".")} You can find the order under <a href="/orders">Orders</a> and cancel it there.`);
        alertBox.focus();
        ui.setBusy(placeButton, false);
        placeButton.disabled = true;
        placeStatus.textContent = "";
    }

    form.addEventListener("submit", async event => {
        event.preventDefault();
        ui.showFormAlert(alertBox, "");

        const invalid = RULES.filter(([input, rule]) => !validate(input, rule));
        if (invalid.length) {
            invalid[0][0].focus();
            return;
        }

        const deliveryX = Number(xInput.value);
        const deliveryY = Number(yInput.value);
        const upsAccountName = upsInput.value.trim();

        let order;
        ui.setBusy(placeButton, true, "Creating your order…");
        try {
            order = await api("/api/cart/checkout", {
                method: "POST",
                body: { deliveryX, deliveryY, upsAccountName: upsAccountName || null }
            });
            cart.sync({ totalQuantity: 0, cartItems: [] });
        } catch (err) {
            ui.showFormAlert(alertBox, `<strong>The order was not placed.</strong> ${ui.esc(err.message)}`);
            alertBox.focus();
            ui.setBusy(placeButton, false);
            return;
        }

        ui.setBusy(placeButton, true, "Reserving stock…");
        try {
            await api(`/api/orders/${order.orderId}/confirm`, { method: "POST", body: { x: deliveryX, y: deliveryY } });
        } catch (err) {
            return orderLeftBehind(order.orderId, "the stock could not be reserved", err);
        }

        ui.setBusy(placeButton, true, "Taking payment…");
        try {
            await api(`/api/orders/${order.orderId}/payment`, {
                method: "POST",
                body: { orderId: Number(order.orderId), creditCardNumber: cardInput.value.trim(), amount: order.totalPrice }
            });
        } catch (err) {
            return orderLeftBehind(order.orderId, "the payment did not go through", err);
        }

        // The orders page greets the customer with this order.
        sessionStorage.setItem("orderPlaced", order.orderId);
        window.location.href = "/orders";
    });

    async function load() {
        try {
            renderSummary(await cart.get());
        } catch (err) {
            ui.showFormAlert(alertBox, `<strong>Your cart could not be loaded.</strong> ${ui.esc(err.message)}`);
            document.getElementById("cartSummary").innerHTML = "";
        }

        // The warehouse position comes with the catalogue; without it the sketch is simply left out.
        const products = await catalog.get().catch(() => []);
        const w = products.find(p => p.warehouse)?.warehouse;
        if (w) {
            warehouse = { x: w.xCoordinate ?? w.xcoordinate ?? 0, y: w.yCoordinate ?? w.ycoordinate ?? 0 };
            drawRoute();
        }
    }

    load();
})();

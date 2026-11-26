/* Orders: every order shows where it is between the warehouse and the door.
   While any order is still moving the list refreshes itself; once all are settled it stops. */
(() => {
    const { api, auth, ui } = App;
    if (!auth.requireSignIn()) return;

    const container = document.getElementById("ordersList");
    const live = document.getElementById("liveStatus");
    const liveText = document.getElementById("liveText");
    const refreshButton = document.getElementById("refreshOrders");

    const POLL_MS = 5000;

    /* The tracker marks five milestones. A status either sits on a milestone
       or is the work on the way to the next one. */
    const MILESTONES = ["Placed", "Paid", "Packed", "Loaded", "Delivered"];

    const STATUS = {
        CREATED:    { reached: 1, moving: false, pill: "",         icon: "clock",        label: "Created",          caption: "Created. Waiting for the delivery point to be confirmed." },
        CONFIRMED:  { reached: 1, moving: false, pill: "warning",  icon: "card",         label: "Awaiting payment", caption: "Stock is reserved for you. Waiting for payment." },
        PAID:       { reached: 2, moving: true,  pill: "progress", icon: "package",      label: "Paid",             caption: "Paid. The warehouse has been asked to pack it." },
        PACKING:    { reached: 2, moving: true,  pill: "progress", icon: "package",      label: "Packing",          caption: "Being packed at the warehouse." },
        PACKED:     { reached: 3, moving: true,  pill: "progress", icon: "truck",        label: "Packed",           caption: "Packed. A truck is on its way to the warehouse." },
        LOADING:    { reached: 3, moving: true,  pill: "progress", icon: "truck",        label: "Loading",          caption: "The truck is at the warehouse and is being loaded." },
        LOADED:     { reached: 4, moving: true,  pill: "progress", icon: "truck",        label: "Loaded",           caption: "On the truck. It is about to leave." },
        DELIVERING: { reached: 4, moving: true,  pill: "progress", icon: "truck",        label: "Out for delivery", caption: "Out for delivery." },
        DELIVERED:  { reached: 5, moving: false, pill: "success",  icon: "check-circle", label: "Delivered",        caption: "Delivered." },
        CANCELED:   { reached: 0, moving: false, pill: "danger",   icon: "ban",          label: "Canceled",         caption: "This order was canceled. Nothing was shipped." }
    };

    const CANCELABLE = ["CREATED", "CONFIRMED"];

    let lastSnapshot = "";
    let timer = null;

    function tracker(state) {
        return `
            <ol class="tracker" aria-label="Order progress">
              ${MILESTONES.map((name, i) => {
                  const done = i < state.reached;
                  const next = i === state.reached;
                  const classes = ["tracker__step", done && "is-done", next && "is-next", next && state.moving && "is-moving"]
                      .filter(Boolean).join(" ");
                  const spoken = done ? "done" : next && state.moving ? "in progress" : "not yet";
                  return `
                    <li class="${classes}" ${next ? 'aria-current="step"' : ""}>
                      <span class="tracker__node">${ui.icon("check")}</span>
                      <span>${name}<span class="visually-hidden">: ${spoken}</span></span>
                    </li>`;
              }).join("")}
            </ol>`;
    }

    function orderCard(order) {
        const state = STATUS[order.status] || { reached: 0, moving: false, pill: "", icon: "info", label: order.status, caption: "" };
        const items = order.orderItems || [];
        const canceled = order.status === "CANCELED";
        const caption = order.status === "DELIVERING" || order.status === "DELIVERED"
            ? `${state.caption.slice(0, -1)} to (${order.deliveryX}, ${order.deliveryY}).`
            : state.caption;

        return `
            <article class="card" aria-labelledby="order-${order.id}">
              <div class="card__head">
                <div class="order__meta">
                  <h2 id="order-${order.id}">Order #${order.id}</h2>
                  <span class="pill ${state.pill ? "pill--" + state.pill : ""}">${ui.icon(state.icon)}${ui.esc(state.label)}</span>
                </div>
                <strong class="num">${ui.money(order.totalPrice)}</strong>
              </div>
              <div class="card__body">
                ${canceled ? "" : tracker(state)}
                <p class="order__caption" ${canceled ? 'style="margin-top: 0"' : ""}>${ui.esc(caption)}</p>

                <ul class="order__items">
                  ${items.map(item => `
                    <li class="line line--compact">
                      <span>${ui.esc(item.product?.name || "Item")} <span class="muted num">× ${item.quantity}</span></span>
                      <span class="muted num">${ui.money(Number(item.price) * item.quantity)}</span>
                    </li>`).join("")}
                </ul>

                <div class="order__foot">
                  <div class="order__facts">
                    <span>${ui.icon("pin")}Drop-off at <span class="num">(${order.deliveryX}, ${order.deliveryY})</span></span>
                    ${order.upsAccountName ? `<span>${ui.icon("truck")}UPS account ${ui.esc(order.upsAccountName)}</span>` : ""}
                  </div>
                  ${CANCELABLE.includes(order.status)
                      ? `<button type="button" class="btn btn--danger-quiet btn--sm" data-cancel="${order.id}">Cancel order</button>`
                      : ""}
                </div>
              </div>
            </article>`;
    }

    function render(orders) {
        container.removeAttribute("aria-busy");
        if (orders.length === 0) {
            container.innerHTML = `<div class="card">${ui.emptyState({
                icon: "orders",
                title: "No orders yet",
                body: "When you place an order you can follow it here, from the warehouse to your door.",
                action: { label: "Browse the catalog", href: "/catalog" }
            })}</div>`;
            return;
        }
        // Newest first: ids only ever grow.
        container.innerHTML = [...orders].sort((a, b) => b.id - a.id).map(orderCard).join("");
    }

    function setLive(orders) {
        // Only orders that are with the warehouse or the carrier change on their own.
        const moving = orders.some(order => STATUS[order.status]?.moving);
        const time = new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" });
        live.classList.toggle("is-live", moving);
        liveText.textContent = moving ? `Updating automatically · last checked ${time}` : `Last checked ${time}`;

        clearTimeout(timer);
        if (moving) timer = setTimeout(() => load({ quiet: true }), POLL_MS);
    }

    async function load({ quiet = false } = {}) {
        // A hidden tab does not need fresh data; it catches up when it is looked at again.
        if (quiet && document.hidden) {
            timer = setTimeout(() => load({ quiet: true }), POLL_MS);
            return;
        }
        if (!quiet) ui.setBusy(refreshButton, true, "Refreshing");
        try {
            const orders = (await api("/api/users/me/my-orders", { method: "POST" })) || [];
            const snapshot = JSON.stringify(orders);
            // Redraw only when something changed, so focus and scroll position survive the polling.
            if (snapshot !== lastSnapshot) {
                lastSnapshot = snapshot;
                render(orders);
            }
            setLive(orders);
        } catch (err) {
            if (quiet) {
                live.classList.remove("is-live");
                liveText.textContent = "Updates paused. Refresh to try again.";
            } else if (!lastSnapshot) {
                container.removeAttribute("aria-busy");
                container.innerHTML = `<div class="card">${ui.errorState(err.message, "retryLoad")}</div>`;
            } else {
                ui.toast(err.message, { type: "error" });
            }
        } finally {
            if (!quiet) ui.setBusy(refreshButton, false);
        }
    }

    async function cancelOrder(button) {
        const orderId = button.dataset.cancel;
        const yes = await ui.confirm({
            title: `Cancel order #${orderId}?`,
            body: "Any stock reserved for it goes back on the shelf. This cannot be undone.",
            confirmLabel: "Cancel order",
            cancelLabel: "Keep order"
        });
        if (!yes) return;

        ui.setBusy(button, true, "Canceling");
        try {
            await api(`/api/orders/${orderId}/cancel`, { method: "POST" });
            ui.toast(`Order #${orderId} was canceled.`);
        } catch (err) {
            ui.toast(err.message, { type: "error" });
        }
        await load();
    }

    container.addEventListener("click", event => {
        const cancel = event.target.closest("[data-cancel]");
        if (cancel) cancelOrder(cancel);
        if (event.target.closest("#retryLoad")) load();
    });
    refreshButton.addEventListener("click", () => load());
    document.addEventListener("visibilitychange", () => {
        if (!document.hidden && live.classList.contains("is-live")) load({ quiet: true });
    });

    const placed = sessionStorage.getItem("orderPlaced");
    if (placed) {
        sessionStorage.removeItem("orderPlaced");
        ui.toast(`Order #${placed} is placed and paid. You can follow it here.`);
    }

    load();
})();

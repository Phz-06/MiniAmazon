/* Warehouse (admin): world status, stock levels, and ordering more stock.
   The server enforces the admin role; this page only avoids showing it to others. */
(() => {
    const { api, auth, catalog, ui } = App;
    if (!auth.requireSignIn()) return;

    const LOW_STOCK = 10;

    const buyForm = document.getElementById("buyForm");
    const buyButton = document.getElementById("buyButton");
    const buyAlert = document.getElementById("buyAlert");
    const rows = document.getElementById("inventoryRows");
    const search = document.getElementById("inventorySearch");
    const refreshButton = document.getElementById("refreshStatus");

    const fields = {
        warehouseId: document.getElementById("buyWarehouseId"),
        productId: document.getElementById("buyProductId"),
        description: document.getElementById("buyDescription"),
        count: document.getElementById("buyCount")
    };

    let products = [];

    async function loadStatus() {
        try {
            const status = await api("/api/world/status");
            document.getElementById("statWorld").textContent = status.worldId ?? "None yet";
            document.getElementById("statPending").textContent = status.pendingMessages;
        } catch (err) {
            document.getElementById("statWorld").textContent = "–";
            document.getElementById("statPending").textContent = "–";
            ui.toast("Status could not be loaded. " + err.message, { type: "error" });
        }
    }

    async function loadInventory() {
        try {
            products = await api("/api/products", { anonymous: true }).then(data => data.products || []);
            const units = products.reduce((sum, p) => sum + p.quantity, 0);
            document.getElementById("statUnits").textContent = units.toLocaleString("en-US");
            document.getElementById("statProducts").textContent = `Across ${products.length} products`;
            document.getElementById("statLow").textContent = products.filter(p => p.quantity <= LOW_STOCK).length;
            renderInventory();
        } catch (err) {
            rows.innerHTML = `<tr><td colspan="6" class="wrap">Inventory could not be loaded. ${ui.esc(err.message)}</td></tr>`;
        }
    }

    function stockPill(quantity) {
        if (quantity <= 0) return `<span class="pill pill--danger">${ui.icon("alert")}Out of stock</span>`;
        if (quantity <= LOW_STOCK) return `<span class="pill pill--warning">${ui.icon("alert")}Low</span>`;
        return "";
    }

    function renderInventory() {
        const query = search.value.trim().toLowerCase();
        const visible = products
            .filter(p => !query || p.name.toLowerCase().includes(query) || String(p.productID).includes(query))
            .sort((a, b) => a.quantity - b.quantity);

        if (visible.length === 0) {
            rows.innerHTML = `<tr><td colspan="6" class="wrap muted">No products match that search.</td></tr>`;
            return;
        }
        rows.innerHTML = visible.map(p => `
            <tr>
              <td class="mono">${ui.esc(String(p.productID).padStart(3, "0"))}</td>
              <td class="wrap"><strong>${ui.esc(p.name)}</strong></td>
              <td class="muted">${ui.esc(p.category)}</td>
              <td class="right num">${ui.money(p.price)}</td>
              <td class="right num">${stockPill(p.quantity)} <strong>${p.quantity}</strong></td>
              <td class="right">
                <button type="button" class="btn btn--secondary btn--sm" data-restock="${ui.esc(p.productID)}"
                        aria-label="Restock ${ui.esc(p.name)}">Restock</button>
              </td>
            </tr>`).join("");
    }

    /** Fills the order form from a row, so nothing has to be retyped. */
    function prefill(productId) {
        const product = products.find(p => String(p.productID) === productId);
        if (!product) return;
        fields.productId.value = product.productID;
        fields.description.value = product.name;
        ui.clearFieldErrors(buyForm);
        buyForm.scrollIntoView({ behavior: "smooth", block: "center" });
        fields.count.focus({ preventScroll: true });
    }

    const RULES = [
        [fields.warehouseId, v => /^\d+$/.test(v) && Number(v) >= 1 ? "" : "Enter a warehouse number, 1 or higher."],
        [fields.productId, v => /^\d+$/.test(v) ? "" : "Enter the product's numeric ID."],
        [fields.description, v => v ? "" : "Describe the product, for example its name."],
        [fields.count, v => /^\d+$/.test(v) && Number(v) >= 1 ? "" : "Enter how many units to order, 1 or more."]
    ];

    buyForm.addEventListener("submit", async event => {
        event.preventDefault();
        ui.showFormAlert(buyAlert, "");

        const invalid = RULES.filter(([input, rule]) => {
            const message = rule(input.value.trim());
            ui.setFieldError(input, message);
            return Boolean(message);
        });
        if (invalid.length) {
            invalid[0][0].focus();
            return;
        }

        const params = new URLSearchParams({
            warehouseId: fields.warehouseId.value.trim(),
            productId: fields.productId.value.trim(),
            description: fields.description.value.trim(),
            count: fields.count.value.trim()
        });

        ui.setBusy(buyButton, true, "Ordering…");
        try {
            const result = await api("/api/world/buy?" + params.toString(), { method: "POST" });
            ui.toast(`Ordered ${params.get("count")} × ${params.get("description")} (request #${result.seqNum}). It is sellable once it arrives.`);
            fields.count.value = "";
            loadStatus();
        } catch (err) {
            ui.showFormAlert(buyAlert, `<strong>The stock was not ordered.</strong> ${ui.esc(err.message)}`);
        } finally {
            ui.setBusy(buyButton, false);
        }
    });

    rows.addEventListener("click", event => {
        const restock = event.target.closest("[data-restock]");
        if (restock) prefill(restock.dataset.restock);
    });
    search.addEventListener("input", renderInventory);
    refreshButton.addEventListener("click", async () => {
        ui.setBusy(refreshButton, true, "Refreshing");
        await Promise.all([loadStatus(), loadInventory()]);
        ui.setBusy(refreshButton, false);
    });

    (async () => {
        if (!await auth.isAdmin()) {
            window.location.replace("/catalog");
            return;
        }
        loadStatus();
        loadInventory();
    })();
})();

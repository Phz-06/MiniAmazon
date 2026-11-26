/* Catalog: the list is fetched once, then searched, filtered and sorted in the browser. */
(() => {
    const { api, auth, cart, catalog, ui } = App;

    const list = document.getElementById("productList");
    const chips = document.getElementById("categoryChips");
    const search = document.getElementById("searchInput");
    const sort = document.getElementById("sortSelect");
    const resultCount = document.getElementById("resultCount");

    let products = [];
    let category = "";

    const SORTS = {
        "featured": null,
        "price-asc": (a, b) => a.price - b.price,
        "price-desc": (a, b) => b.price - a.price,
        "name": (a, b) => a.name.localeCompare(b.name)
    };

    function renderSkeleton() {
        list.setAttribute("aria-busy", "true");
        list.innerHTML = Array.from({ length: 8 }, () => `
            <div class="product-card product-card--skeleton" aria-hidden="true">
              <div class="skeleton skeleton--media"></div>
              <div class="skeleton skeleton--line" style="width: 70%"></div>
              <div class="skeleton skeleton--line" style="width: 40%"></div>
            </div>`).join("");
    }

    function renderChips(categories) {
        const counts = Object.fromEntries(categories.map(c => [c, products.filter(p => p.category === c).length]));
        const chip = (value, label, count) => `
            <button type="button" class="chip" data-category="${ui.esc(value)}" aria-pressed="${value === category}">
              ${ui.esc(label)} <span class="chip__count">${count}</span>
            </button>`;
        chips.innerHTML = chip("", "All", products.length) + categories.map(c => chip(c, c, counts[c])).join("");
    }

    function card(p) {
        const soldOut = p.quantity <= 0;
        return `
            <article class="product-card">
              ${ui.productMedia(p)}
              <div class="product-card__body">
                <span class="product-card__category">${ui.esc(p.category)}</span>
                <h2 class="product-card__name">
                  <a class="product-card__link" href="/product/${encodeURIComponent(p.productID)}">${ui.esc(p.name)}</a>
                </h2>
                ${ui.stockLabel(p.quantity)}
              </div>
              <div class="product-card__foot">
                <span class="product-card__price">${ui.money(p.price)}</span>
                <button type="button" class="btn btn--secondary btn--sm product-card__add"
                        data-add="${ui.esc(p.productID)}" ${soldOut ? "disabled" : ""}
                        aria-label="Add ${ui.esc(p.name)} to cart">
                  ${ui.icon("plus")}Add
                </button>
              </div>
            </article>`;
    }

    function render() {
        const query = search.value.trim().toLowerCase();
        const visible = products
            .filter(p => !category || p.category === category)
            .filter(p => !query
                || p.name.toLowerCase().includes(query)
                || (p.description || "").toLowerCase().includes(query));
        const comparator = SORTS[sort.value];
        if (comparator) visible.sort(comparator);

        list.removeAttribute("aria-busy");
        resultCount.textContent = visible.length === 1 ? "1 product" : `${visible.length} products`;

        if (visible.length === 0) {
            list.innerHTML = `
                <div class="empty">
                  <div class="empty__icon">${ui.icon("search")}</div>
                  <h2>No products match</h2>
                  <p>Try a different word, or clear the filters to see the whole catalog.</p>
                  <button type="button" class="btn btn--secondary" id="clearFilters">Clear filters</button>
                </div>`;
            return;
        }
        list.innerHTML = visible.map(card).join("");
    }

    function setCategory(value) {
        category = value;
        chips.querySelectorAll(".chip").forEach(chip =>
            chip.setAttribute("aria-pressed", String(chip.dataset.category === category)));
        render();
    }

    async function addToCart(button) {
        if (!auth.isSignedIn()) {
            auth.signOut(window.location.pathname);
            return;
        }
        const product = products.find(p => String(p.productID) === button.dataset.add);
        ui.setBusy(button, true, "Adding");
        try {
            await cart.add(button.dataset.add, 1);
            ui.toast(`${product.name} added to your cart.`, { action: { label: "View cart", href: "/cart" } });
        } catch (err) {
            ui.toast(err.message, { type: "error" });
        } finally {
            ui.setBusy(button, false);
        }
    }

    async function load() {
        renderSkeleton();
        try {
            const [items, categoryData] = await Promise.all([
                catalog.get(),
                api("/api/products/categories", { anonymous: true })
            ]);
            products = items;
            renderChips(categoryData.categories || []);
            render();
        } catch (err) {
            list.removeAttribute("aria-busy");
            resultCount.textContent = "";
            list.innerHTML = ui.errorState(err.message, "retryLoad");
        }
    }

    let debounce = null;
    search.addEventListener("input", () => {
        clearTimeout(debounce);
        debounce = setTimeout(render, 150);
    });
    sort.addEventListener("change", render);
    document.getElementById("filters").addEventListener("submit", event => {
        event.preventDefault();
        render();
    });
    chips.addEventListener("click", event => {
        const chip = event.target.closest(".chip");
        if (chip) setCategory(chip.dataset.category);
    });
    list.addEventListener("click", event => {
        const add = event.target.closest("[data-add]");
        if (add) return addToCart(add);
        if (event.target.closest("#clearFilters")) {
            search.value = "";
            setCategory("");
        }
        if (event.target.closest("#retryLoad")) window.location.reload();
    });

    load();
})();

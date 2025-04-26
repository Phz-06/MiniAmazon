async function loadProducts() {
    const category = document.getElementById("categoryFilter").value;
    const query = document.getElementById("searchInput").value.toLowerCase();

    const res = await fetch("/api/products");
    const data = await res.json();
    const products = data.products;

    const list = document.getElementById("productList");
    list.innerHTML = "";

    products
        .filter(p => (!category || p.category === category))
        .filter(p => p.name.toLowerCase().includes(query) || p.description.toLowerCase().includes(query))
        .forEach(p => {
            const color = stringToColor(p.name);

            const card = document.createElement("div");
            card.className = "col";
            card.innerHTML = `
            <div class="product-card" style="background-color:${color}">
              <div class="product-title">${p.name}</div>
              <div><strong>$${p.price.toFixed(2)}</strong></div>
              <div class="product-description">${p.description}</div>
            </div>
          `;
            list.appendChild(card);
        });
}

function stringToColor(str) {
    let hash = 0;
    for (let i = 0; i < str.length; i++) {
        hash = str.charCodeAt(i) + ((hash << 5) - hash);
    }
    const color = '#' + ((hash >> 24 & 0xFF) + 128).toString(16).padStart(2, '0') +
        ((hash >> 16 & 0xFF) + 128).toString(16).padStart(2, '0') +
        ((hash >> 8 & 0xFF) + 128).toString(16).padStart(2, '0');
    return color;
}

async function populateCategories() {
    const res = await fetch("/api/products/categories");
    const data = await res.json();
    const categories = data.categories;
    const select = document.getElementById("categoryFilter");

    categories.forEach(c => {
        const option = document.createElement("option");
        option.value = c;
        option.textContent = c;
        select.appendChild(option);
    });
}

async function init() {
    const user = localStorage.getItem("user");
    if (user) {
        const userObj = JSON.parse(user);
        document.getElementById("userName").textContent = userObj.firstName || "User";
    }

    await populateCategories();
    await loadProducts();
}

init();

function logout() {
    localStorage.removeItem("token");
    window.location.href = "/signin";
}

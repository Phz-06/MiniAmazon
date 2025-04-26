let searchTimeout = null;

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
            const color = "white";
            const imgUrl = generateDummyImage(p.name);

            const card = document.createElement("div");
            card.className = "col";

            card.innerHTML = `
            <div class="card h-100 shadow-sm" style="background-color: ${color}; cursor: pointer;" onclick="goToProduct(${p.id})">
              <img src="${imgUrl}" class="card-img-top" alt="${p.name}" style="object-fit: cover; height: 200px;">
              <div class="card-body d-flex flex-column">
                <h5 class="card-title">${p.name}</h5>
                <h6 class="text-success mb-2">$${p.price.toFixed(2)}</h6>
                <p class="card-text text-truncate" style="max-height: 60px;">${p.description}</p>
              </div>
            </div>
          `;
            list.appendChild(card);
        });
}

function generateDummyImage(name) {
    const hash = name.split('').reduce((sum, c) => sum + c.charCodeAt(0), 0);
    const color = (hash % 16777215).toString(16).padStart(6, '0');
    return `https://dummyimage.com/400x400/${color}/ffffff&text=${encodeURIComponent(name)}`;
}

function searchProducts() {
    if (searchTimeout) clearTimeout(searchTimeout);
    searchTimeout = setTimeout(loadProducts, 300);
}

function goToProduct(id) {
    if (!id) {
        alert("Invalid product ID.");
        return;
    }
    window.location.href = `/product/${id}`;
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

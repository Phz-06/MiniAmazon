(async function() {
    const token = localStorage.getItem("token");
    if (!token) {
        window.location.href = "/signin";
        return;
    }

    const productId = getProductIdFromUrl();
    if (!productId) {
        alert("Invalid product ID.");
        return;
    }

    try {
        const res = await fetch(`/api/products/${productId}`, {
            method: "GET",
            headers: { "Authorization": "Bearer " + token }
        });

        if (!res.ok) {
            alert("Failed to load product details.");
            return;
        }

        const product = await res.json();

        document.getElementById("productName").textContent = product.name;
        document.getElementById("productPrice").textContent = `$${product.price.toFixed(2)}`;
        document.getElementById("productDescription").textContent = product.description;
        document.getElementById("productCategory").textContent = `Category: ${product.category}`;

        // Generate a dummy image based on product name (hash)
        const imgUrl = generateDummyImage(product.name);
        document.getElementById("productImage").src = imgUrl;

    } catch (err) {
        console.error(err);
        alert("Error loading product.");
    }
})();

function getProductIdFromUrl() {
    const parts = window.location.pathname.split("/");
    const id = parts[2]; // /product/{id}
    if (!id) {
        return null;
    }
    return id;
}

function generateDummyImage(name) {
    const hash = name.split('').reduce((sum, c) => sum + c.charCodeAt(0), 0);
    const color = (hash % 16777215).toString(16).padStart(6, '0');
    return `https://dummyimage.com/400x400/${color}/ffffff&text=${encodeURIComponent(name)}`;
}

async function addToCart() {
    const productId = getProductIdFromUrl();
    const quantity = 1; // Default 1 for now

    try {
        const res = await fetch("/api/cart/add", {
            method: "POST",
            headers: {
                "Authorization": "Bearer " + localStorage.getItem("token"),
                "Content-Type": "application/json"
            },
            body: JSON.stringify({ productId, quantity })
        });

        if (!res.ok) {
            alert("Failed to add to cart.");
            return;
        }

        alert("Product added to cart!");
    } catch (err) {
        console.error(err);
        alert("Error adding to cart.");
    }
}

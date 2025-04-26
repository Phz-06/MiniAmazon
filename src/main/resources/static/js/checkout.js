(async function() {
    const token = localStorage.getItem("token");
    if (!token) {
        window.location.href = "/signin";
        return;
    }
    await loadCartSummary();
})();

function getAuthHeaders() {
    const token = localStorage.getItem("token");
    return { "Authorization": "Bearer " + token, "Content-Type": "application/json" };
}

async function loadCartSummary() {
    try {
        const res = await fetch("/api/cart", {
            method: "GET",
            headers: getAuthHeaders()
        });

        if (!res.ok) {
            alert("Failed to load cart.");
            return;
        }

        const data = await res.json();
        const cartSummary = document.getElementById("cartSummary");
        cartSummary.innerHTML = "";

        if (!data.cartItems || data.cartItems.length === 0) {
            cartSummary.innerHTML = "<div class='text-muted'>Your cart is empty.</div>";
            return;
        }

        for (const item of data.cartItems) {
            const div = document.createElement("div");
            div.className = "list-group-item d-flex justify-content-between align-items-center";
            div.innerHTML = `
                <div><strong>${item.product.name}</strong> x ${item.quantity}</div>
                <div>$${(item.price * item.quantity).toFixed(2)}</div>
            `;
            cartSummary.appendChild(div);
        }
    } catch (err) {
        console.error(err);
        alert("Error loading cart.");
    }
}

async function checkout() {
    const deliveryX = parseInt(document.getElementById("deliveryX").value);
    const deliveryY = parseInt(document.getElementById("deliveryY").value);
    const upsAccountName = document.getElementById("upsAccountName").value.trim();

    if (!deliveryX || !deliveryY || !upsAccountName) {
        alert("Please fill in all delivery details.");
        return;
    }

    try {
        const res = await fetch("/api/cart/checkout", {
            method: "POST",
            headers: getAuthHeaders(),
            body: JSON.stringify({
                deliveryX,
                deliveryY,
                upsAccountName
            })
        });

        if (!res.ok) {
            alert("Failed to checkout.");
            return;
        }

        alert("Order placed successfully!");
        window.location.href = "/"; // Redirect to catalog or home after order placed
    } catch (err) {
        console.error(err);
        alert("Error during checkout.");
    }
}

function logout() {
    localStorage.removeItem("token");
    window.location.href = "/signin";
}

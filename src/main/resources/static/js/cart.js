(async function() {
    const token = localStorage.getItem("token");
    if (!token) {
        window.location.href = "/signin";
        return;
    }
    loadCart();
})();

function getAuthHeaders() {
    const token = localStorage.getItem("token");
    return { "Authorization": "Bearer " + token, "Content-Type": "application/json" };
}

async function loadCart() {
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
        const cartItemsDiv = document.getElementById("cartItems");
        cartItemsDiv.innerHTML = "";

        if (!data.cartItems || data.cartItems.length === 0) {
            cartItemsDiv.innerHTML = "<div class='text-muted'>Your cart is empty.</div>";
            return;
        }

        for (const item of data.cartItems) {
            const div = document.createElement("div");
            div.className = "list-group-item d-flex justify-content-between align-items-center";
            div.innerHTML = `
                <div>
                  <strong>${item.product.name}</strong> (Quantity: ${item.quantity})
                </div>
                <div class="d-flex gap-2">
                  <button class="btn btn-sm btn-warning" onclick="updateQuantity('${item.product.productID}')">Update</button>
                  <button class="btn btn-sm btn-danger" onclick="removeItem('${item.product.productID}')">Remove</button>
                </div>
            `;
            cartItemsDiv.appendChild(div);
        }
    } catch (err) {
        console.error(err);
        alert("Error loading cart.");
    }
}

async function updateQuantity(productId) {
    const quantity = prompt("Enter new quantity:");
    if (!quantity) {
        return;
    }

    try {
        const res = await fetch("/api/cart/update", {
            method: "PUT",
            headers: getAuthHeaders(),
            body: JSON.stringify({ productId, quantity: parseInt(quantity) })
        });

        if (!res.ok) {
            alert("Failed to update quantity.");
            return;
        }

        loadCart();
    } catch (err) {
        console.error(err);
        alert("Error updating quantity.");
    }
}

async function removeItem(productId) {
    try {
        const res = await fetch("/api/cart/remove/" + productId, {
            method: "DELETE",
            headers: getAuthHeaders()
        });

        if (!res.ok) {
            alert("Failed to remove item.");
            return;
        }

        loadCart();
    } catch (err) {
        console.error(err);
        alert("Error removing item.");
    }
}

async function clearCart() {
    if (!confirm("Are you sure you want to clear your cart?")) {
        return;
    }

    try {
        const res = await fetch("/api/cart/clear", {
            method: "DELETE",
            headers: getAuthHeaders()
        });

        if (!res.ok) {
            alert("Failed to clear cart.");
            return;
        }

        loadCart();
    } catch (err) {
        console.error(err);
        alert("Error clearing cart.");
    }
}

function checkout() {
    window.location.href = "/checkout";
}

function logout() {
    localStorage.removeItem("token");
    window.location.href = "/signin";
}

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
        const cartItems = document.getElementById("cartItems");
        cartItems.innerHTML = "";

        if (data.items.length === 0) {
            cartItems.innerHTML = "<div class='text-muted'>Your cart is empty.</div>";
            return;
        }

        for (const item of data.items) {
            const div = document.createElement("div");
            div.className = "list-group-item d-flex justify-content-between align-items-center";
            div.innerHTML = `
                <div>
                  <strong>${item.productName}</strong> (Quantity: ${item.quantity})
                </div>
                <div class="d-flex gap-2">
                  <button class="btn btn-sm btn-warning" onclick="updateQuantity('${item.productId}')">Update</button>
                  <button class="btn btn-sm btn-danger" onclick="removeItem('${item.productId}')">Remove</button>
                </div>
            `;
            cartItems.appendChild(div);
        }
    } catch (err) {
        console.error(err);
        alert("Error loading cart.");
    }
}

async function updateQuantity(productId) {
    const quantity = prompt("Enter new quantity:");
    if (!quantity) return;

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
    if (!confirm("Are you sure you want to clear your cart?")) return;

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

async function checkout() {
    const deliveryX = prompt("Enter delivery X coordinate:");
    const deliveryY = prompt("Enter delivery Y coordinate:");
    const upsAccountName = prompt("Enter your UPS account name:");

    if (!deliveryX || !deliveryY || !upsAccountName) {
        alert("All fields are required.");
        return;
    }

    try {
        const res = await fetch("/api/cart/checkout", {
            method: "POST",
            headers: getAuthHeaders(),
            body: JSON.stringify({
                deliveryX: parseInt(deliveryX),
                deliveryY: parseInt(deliveryY),
                upsAccountName
            })
        });

        if (!res.ok) {
            alert("Failed to checkout.");
            return;
        }

        alert("Order placed successfully!");
        loadCart();
    } catch (err) {
        console.error(err);
        alert("Error during checkout.");
    }
}

function logout() {
    localStorage.removeItem("token");
    window.location.href = "/signin";
}

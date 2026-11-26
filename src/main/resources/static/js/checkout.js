let cartTotalAmount = 0;

(async function () {
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
        const totalDisplay = document.getElementById("cartTotal");
        cartSummary.innerHTML = "";

        cartTotalAmount = 0;

        if (!data.cartItems || data.cartItems.length === 0) {
            cartSummary.innerHTML = "<div class='text-muted'>Your cart is empty.</div>";
            totalDisplay.textContent = "0.00";
            return;
        }

        for (const item of data.cartItems) {
            const itemTotal = Number(item.price);
            cartTotalAmount += itemTotal;

            const div = document.createElement("div");
            div.className = "list-group-item d-flex justify-content-between align-items-center";
            div.innerHTML = `
                <div><strong>${item.product.name}</strong> x ${item.quantity}</div>
                <div>$${itemTotal.toFixed(2)}</div>
            `;
            cartSummary.appendChild(div);
        }

        totalDisplay.textContent = cartTotalAmount.toFixed(2);
    } catch (err) {
        console.error(err);
        alert("Error loading cart.");
    }
}

async function checkout() {
    const deliveryX = parseInt(document.getElementById("deliveryX").value);
    const deliveryY = parseInt(document.getElementById("deliveryY").value);
    const upsAccountName = document.getElementById("upsAccountName").value.trim();
    const creditCardNumber = document.getElementById("creditCardNumber").value.trim();

    if (!deliveryX || !deliveryY || !upsAccountName || !creditCardNumber) {
        alert("Please fill in all fields.");
        return;
    }

    try {
        // Cart checkout
        const checkoutRes = await fetch("/api/cart/checkout", {
            method: "POST",
            headers: getAuthHeaders(),
            body: JSON.stringify({
                deliveryX,
                deliveryY,
                upsAccountName
            })
        });

        if (!checkoutRes.ok) {
            alert("Failed to checkout cart.");
            return;
        }

        const checkoutData = await checkoutRes.json();
        const orderId = checkoutData.orderId;

        if (!orderId) {
            alert("Order ID missing after cart checkout.");
            return;
        }

        console.log("Cart checkout complete. Order ID:", orderId);

        // Confirm Order
        const confirmRes = await fetch(`/api/orders/${orderId}/confirm`, {
            method: "POST",
            headers: getAuthHeaders(),
            body: JSON.stringify({
                x: deliveryX,
                y: deliveryY
            })
        });

        if (!confirmRes.ok) {
            alert("Failed to confirm order.");
            return;
        }

        console.log("Order confirmation complete.");

        // Process Payment
        const paymentRes = await fetch(`/api/orders/${orderId}/payment`, {
            method: "POST",
            headers: getAuthHeaders(),
            body: JSON.stringify({
                orderId,
                creditCardNumber,
                amount: checkoutData.totalPrice
            })
        });

        if (!paymentRes.ok) {
            alert("Failed to process payment.");
            return;
        }

        console.log("Payment processed successfully.");

        alert("Order placed, confirmed, and paid successfully!");
        window.location.href = "/";
    } catch (err) {
        console.error(err);
        alert("Error during full checkout process.");
    }
}

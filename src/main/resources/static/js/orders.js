async function loadOrder() {
    const orderId = document.getElementById("orderIdInput").value.trim();
    if (!orderId) {
        alert("Please enter an Order ID.");
        return;
    }

    try {
        const res = await fetch(`/api/orders/${orderId}`, {
            method: "GET",
            headers: getAuthHeaders()
        });

        if (!res.ok) {
            document.getElementById("orderDetails").classList.add("d-none");
            const errorDiv = document.getElementById("orderError");
            errorDiv.textContent = "Order not found or access denied.";
            errorDiv.classList.remove("d-none");
            return;
        }

        const data = await res.json();

        // Fill in the order details
        document.getElementById("orderId").textContent = data.orderId;
        document.getElementById("orderStatus").textContent = data.status;
        document.getElementById("deliveryX").textContent = data.deliveryX;
        document.getElementById("deliveryY").textContent = data.deliveryY;

        const itemsList = document.getElementById("orderItems");
        itemsList.innerHTML = "";
        data.items.forEach(item => {
            const li = document.createElement("li");
            li.className = "list-group-item";
            li.textContent = `${item.productName} x${item.quantity}`;
            itemsList.appendChild(li);
        });

        document.getElementById("orderError").classList.add("d-none");
        document.getElementById("orderDetails").classList.remove("d-none");

    } catch (err) {
        console.error(err);
        alert("Failed to load order.");
    }
}

function getAuthHeaders() {
    const token = localStorage.getItem("token");
    return { "Authorization": "Bearer " + token, "Content-Type": "application/json" };
}

function logout() {
    localStorage.removeItem("token");
    window.location.href = "/signin";
}

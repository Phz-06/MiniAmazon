(async function() {
    const token = localStorage.getItem("token");
    if (!token) {
        window.location.href = "/signin";
        return;
    }
    await loadOrders();
})();

function getAuthHeaders() {
    const token = localStorage.getItem("token");
    return { "Authorization": "Bearer " + token, "Content-Type": "application/json" };
}

async function loadOrders() {
    try {
        const res = await fetch("/api/users/me/my-orders", {
            method: "POST",
            headers: getAuthHeaders()
        });

        if (!res.ok) {
            alert("Failed to load orders.");
            return;
        }

        const orders = await res.json();
        const container = document.getElementById("ordersList");
        container.innerHTML = "";

        if (!orders || orders.length === 0) {
            container.innerHTML = "<div class='text-muted'>No orders found.</div>";
            return;
        }

        for (const order of orders) {
            const div = document.createElement("div");
            div.className = "card p-3 mb-3 shadow-sm";

            // Try to get shipment ID
            let shipmentId = null;
            try {
                const shipmentRes = await fetch(`/api/orders/${order.id}/shipmentId`, {
                    method: "GET",
                    headers: getAuthHeaders()
                });
                if (shipmentRes.ok) {
                    const shipmentInfo = await shipmentRes.json();
                    shipmentId = shipmentInfo.shipmentId;
                }
            } catch (err) {
                console.warn(`Failed to fetch shipment ID for order ${order.id}`, err);
            }

            // Build item list
            let itemsHtml = "<ul>";
            if (order.orderItems && order.orderItems.length > 0) {
                for (const item of order.orderItems) {
                    let itemName = (item.product && item.product.name) || "Unknown Item";
                    itemsHtml += `<li>${itemName} x ${item.quantity}</li>`;
                }
            } else {
                itemsHtml += "<li class='text-muted'>No items listed</li>";
            }
            itemsHtml += "</ul>";

            div.innerHTML = `
                <div class="d-flex justify-content-between align-items-center">
                    <div>
                        <h5>Order #${order.id}</h5>
                        <p>Status: <strong>${order.status}</strong></p>
                        ${shipmentId ? `<p>Tracking: <code>${shipmentId}</code></p>` : `<p class="text-muted">No tracking yet</p>`}
                        <h6>Items:</h6>
                        ${itemsHtml}
                    </div>
                    <div>
                        ${order.status === "CREATED" || order.status === "CONFIRMED" ? `
                            <button class="btn btn-danger btn-sm" onclick="cancelOrder(${order.id})">Cancel</button>
                        ` : ''}
                    </div>
                </div>
            `;

            container.appendChild(div);
        }
    } catch (err) {
        console.error(err);
        alert("Error loading orders.");
    }
}

async function cancelOrder(orderId) {
    if (!confirm("Are you sure you want to cancel this order?")) return;

    try {
        const res = await fetch(`/api/orders/${orderId}/cancel`, {
            method: "POST",
            headers: getAuthHeaders()
        });

        if (!res.ok) {
            alert("Failed to cancel order.");
            return;
        }

        alert("Order cancellation requested.");
        await loadOrders();
    } catch (err) {
        console.error(err);
        alert("Error cancelling order.");
    }
}

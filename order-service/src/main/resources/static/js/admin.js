(async function() {
    const token = localStorage.getItem("token");
    if (!token) {
        console.warn("No token found. Redirecting to signin.");
        window.location.href = "/signin";
        return;
    }

    try {
        const res = await fetch("/api/auth/me", {
            method: "GET",
            headers: { "Authorization": "Bearer " + token }
        });

        if (!res.ok) {
            console.warn("Failed to authenticate. Redirecting.");
            window.location.href = "/signin";
            return;
        }

        const data = await res.json();
        if (data.role !== "ROLE_ADMIN" && data.role !== "ADMIN") {
            console.warn("Not an admin. Redirecting.");
            window.location.href = "/catalog";
            return;
        }

        console.log("Admin user confirmed. Access granted.");
    } catch (err) {
        console.error("Error during auth check:", err);
        window.location.href = "/signin";
    }
})();

function getAuthHeaders() {
    const token = localStorage.getItem("token");
    return { "Authorization": "Bearer " + token };
}

async function buyProduct() {
    document.getElementById("buyStatus").innerText = "";

    const warehouseId = document.getElementById("buyWarehouseId").value;
    const productId = document.getElementById("buyProductId").value;
    const description = document.getElementById("buyDescription").value;
    const count = document.getElementById("buyCount").value;
    const params = new URLSearchParams({ warehouseId, productId, description, count });

    try {
        const res = await fetch("/api/world/buy?" + params.toString(), {
            method: "POST",
            headers: getAuthHeaders()
        });
        const data = await res.json();
        document.getElementById("buyStatus").innerText = res.ok ? "Ordered. Request #" + data.seqNum : "Failed to buy.";
    } catch (err) {
        console.error(err);
        alert("Error buying product.");
    }
}

async function loadStatus() {
    try {
        const res = await fetch("/api/world/status", { headers: getAuthHeaders() });
        const data = await res.json();
        document.getElementById("worldStatus").innerText = res.ok
            ? "World: " + (data.worldId ?? "none yet") + " | messages waiting to be published: " + data.pendingMessages
            : "Failed to load status.";
    } catch (err) {
        console.error(err);
        document.getElementById("worldStatus").innerText = "Failed to load status.";
    }
}

loadStatus();

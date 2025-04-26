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
            headers: {
                "Authorization": "Bearer " + token
            }
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

async function connectWorld() {
    const host = document.getElementById("connectHost").value || "localhost";
    const port = document.getElementById("connectPort").value || 23456;
    const worldId = document.getElementById("connectWorldId").value;

    const params = new URLSearchParams({ host, port });
    if (worldId) params.append("worldId", worldId);

    try {
        const res = await fetch("/api/world/connect?" + params.toString(), { method: "POST" });
        const data = await res.json();
        document.getElementById("connectStatus").innerText = res.ok ? "Connected! World ID: " + data.worldId : "Failed to connect.";
    } catch (err) {
        console.error(err);
        document.getElementById("connectStatus").innerText = "Error connecting.";
    }
}

async function disconnectWorld() {
    try {
        const res = await fetch("/api/world/disconnect", { method: "POST" });
        const data = await res.json();
        document.getElementById("disconnectStatus").innerText = res.ok ? "Disconnected!" : "Not connected.";
    } catch (err) {
        console.error(err);
        document.getElementById("disconnectStatus").innerText = "Error disconnecting.";
    }
}

async function setSpeed() {
    const speed = document.getElementById("speedInput").value;
    try {
        const res = await fetch("/api/world/speed?speed=" + encodeURIComponent(speed), { method: "POST" });
        const data = await res.json();
        document.getElementById("speedStatus").innerText = res.ok ? "Speed set to " + data.speed : "Failed to set speed.";
    } catch (err) {
        console.error(err);
        document.getElementById("speedStatus").innerText = "Error setting speed.";
    }
}

async function buyProduct() {
    const warehouseId = document.getElementById("buyWarehouseId").value;
    const productId = document.getElementById("buyProductId").value;
    const description = document.getElementById("buyDescription").value;
    const count = document.getElementById("buyCount").value;

    const params = new URLSearchParams({ warehouseId, productId, description, count });

    try {
        const res = await fetch("/api/world/buy?" + params.toString(), { method: "POST" });
        const data = await res.json();
        document.getElementById("buyStatus").innerText = res.ok ? "Product bought! SeqNum: " + data.seqNum : "Failed to buy.";
    } catch (err) {
        console.error(err);
        document.getElementById("buyStatus").innerText = "Error buying product.";
    }
}

async function packOrder() {
    const warehouseId = document.getElementById("packWarehouseId").value;
    const shipmentId = document.getElementById("packShipmentId").value;
    const productsJson = document.getElementById("packProducts").value;

    let products;
    try {
        products = JSON.parse(productsJson);
    } catch (err) {
        document.getElementById("packStatus").innerText = "Invalid JSON for products.";
        return;
    }

    try {
        const res = await fetch("/api/world/pack?warehouseId=" + warehouseId + "&shipmentId=" + shipmentId, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(products)
        });
        const data = await res.json();
        document.getElementById("packStatus").innerText = res.ok ? "Order packed! SeqNum: " + data.seqNum : "Failed to pack.";
    } catch (err) {
        console.error(err);
        document.getElementById("packStatus").innerText = "Error packing order.";
    }
}

async function loadPackage() {
    const warehouseId = document.getElementById("loadWarehouseId").value;
    const truckId = document.getElementById("loadTruckId").value;
    const shipmentId = document.getElementById("loadShipmentId").value;

    const params = new URLSearchParams({ warehouseId, truckId, shipmentId });

    try {
        const res = await fetch("/api/world/load?" + params.toString(), { method: "POST" });
        const data = await res.json();
        document.getElementById("loadStatus").innerText = res.ok ? "Package loaded! SeqNum: " + data.seqNum : "Failed to load.";
    } catch (err) {
        console.error(err);
        document.getElementById("loadStatus").innerText = "Error loading package.";
    }
}

async function queryPackage() {
    const packageId = document.getElementById("queryPackageId").value;

    try {
        const res = await fetch("/api/world/package/" + packageId);
        const data = await res.json();
        document.getElementById("queryStatus").innerText = res.ok ? "Query sent! SeqNum: " + data.seqNum : "Failed to query.";
    } catch (err) {
        console.error(err);
        document.getElementById("queryStatus").innerText = "Error querying package.";
    }
}

async function getWorldId() {
    try {
        const res = await fetch("/api/world/id");
        const data = await res.json();
        document.getElementById("worldIdStatus").innerText = res.ok ? "World ID: " + data.worldId : "Failed to get world ID.";
    } catch (err) {
        console.error(err);
        document.getElementById("worldIdStatus").innerText = "Error getting world ID.";
    }
}

function logout() {
    localStorage.removeItem("token");
    window.location.href = "/signin";
}


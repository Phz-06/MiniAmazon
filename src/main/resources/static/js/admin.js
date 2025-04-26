function clearStatusMsgs() {
    document.getElementById("connectStatus").innerText = "";
    document.getElementById("disconnectStatus").innerText = "";
    document.getElementById("speedStatus").innerText = "";
    document.getElementById("buyStatus").innerText = "";
    document.getElementById("packStatus").innerText = "";
    document.getElementById("loadStatus").innerText = "";
    document.getElementById("queryStatus").innerText = "";
    document.getElementById("worldIdStatus").innerText = "";
}

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

async function connectWorld() {
    clearStatusMsgs();

    const host = document.getElementById("connectHost").value || "localhost";
    const port = document.getElementById("connectPort").value || 23456;
    const worldId = document.getElementById("connectWorldId").value;
    const params = new URLSearchParams({ host, port });
    if (worldId) params.append("worldId", worldId);

    try {
        const res = await fetch("/api/world/connect?" + params.toString(), {
            method: "POST",
            headers: getAuthHeaders()
        });
        const data = await res.json();
        document.getElementById("connectStatus").innerText = res.ok ? "Connected! World ID: " + data.worldId : "Failed to connect.";
    } catch (err) {
        console.error(err);
        alert("Error connecting.");
    }
}

async function disconnectWorld() {
    clearStatusMsgs();

    try {
        const res = await fetch("/api/world/disconnect", {
            method: "POST",
            headers: getAuthHeaders()
        });
        const data = await res.json();
        document.getElementById("disconnectStatus").innerText = res.ok ? "Disconnected!" : "Not connected.";
    } catch (err) {
        console.error(err);
        alert("Error disconnecting.");
    }
}

async function setSpeed() {
    clearStatusMsgs();

    const speed = document.getElementById("speedInput").value;
    try {
        const res = await fetch("/api/world/speed?speed=" + encodeURIComponent(speed), {
            method: "POST",
            headers: getAuthHeaders()
        });
        const data = await res.json();
        document.getElementById("speedStatus").innerText = res.ok ? "Speed set to " + data.speed : "Failed to set speed.";
    } catch (err) {
        console.error(err);
        alert("Error setting speed.");
    }
}

async function buyProduct() {
    clearStatusMsgs();

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
        document.getElementById("buyStatus").innerText = res.ok ? "Product bought! SeqNum: " + data.seqNum : "Failed to buy.";
    } catch (err) {
        console.error(err);
        alert("Error buying product.");
    }
}

async function packOrder() {
    clearStatusMsgs();

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
            headers: { ...getAuthHeaders(), "Content-Type": "application/json" },
            body: JSON.stringify(products)
        });
        const data = await res.json();
        document.getElementById("packStatus").innerText = res.ok ? "Order packed! SeqNum: " + data.seqNum : "Failed to pack.";
    } catch (err) {
        console.error(err);
        alert("Error packing order.");
    }
}

async function loadPackage() {
    clearStatusMsgs();

    const warehouseId = document.getElementById("loadWarehouseId").value;
    const truckId = document.getElementById("loadTruckId").value;
    const shipmentId = document.getElementById("loadShipmentId").value;

    const params = new URLSearchParams({ warehouseId, truckId, shipmentId });

    try {
        const res = await fetch("/api/world/load?" + params.toString(), {
            method: "POST",
            headers: getAuthHeaders()
        });
        const data = await res.json();
        document.getElementById("loadStatus").innerText = res.ok ? "Package loaded! SeqNum: " + data.seqNum : "Failed to load.";
    } catch (err) {
        console.error(err);
        alert("Error loading package.");
    }
}

async function queryPackage() {
    clearStatusMsgs();

    const packageId = document.getElementById("queryPackageId").value;

    try {
        const res = await fetch("/api/world/package/" + packageId, {
            method: "GET",
            headers: getAuthHeaders()
        });
        const data = await res.json();
        document.getElementById("queryStatus").innerText = res.ok ? "Query sent! SeqNum: " + data.seqNum : "Failed to query.";
    } catch (err) {
        console.error(err);
        alert("Error querying package.");
    }
}

async function getWorldId() {
    clearStatusMsgs();

    try {
        const res = await fetch("/api/world/id", {
            method: "GET",
            headers: getAuthHeaders()
        });
        const data = await res.json();
        document.getElementById("worldIdStatus").innerText = res.ok ? "World ID: " + data.worldId : "Failed to get world ID.";
    } catch (err) {
        console.error(err);
        alert("Error getting world ID.");
    }
}

function logout() {
    localStorage.removeItem("token");
    window.location.href = "/signin";
}

(async function() {
    const token = localStorage.getItem("token");
    if (!token) {
        console.log("No token found.");
        window.location.href = "/catalog";
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
            console.log("Unauthorized or invalid token.");
            window.location.href = "/catalog";
            return;
        }

        const data = await res.json();
        console.log("Fetched user info:", data);

        if (data.role === "ROLE_ADMIN" || data.role === "ADMIN" || data.role === "ROLE_ADMIN") {
            window.location.href = "/admin";
        } else {
            window.location.href = "/catalog";
        }
    } catch (err) {
        console.error("Error fetching user info:", err);
        window.location.href = "/catalog";
    }
})();

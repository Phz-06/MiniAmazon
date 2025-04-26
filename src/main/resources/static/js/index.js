(async function() {
    const token = localStorage.getItem("token");
    if (!token) {
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
            window.location.href = "/catalog";
            return;
        }

        const data = await res.json();
        if (data.role === "ROLE_ADMIN") {
            window.location.href = "/admin";
        } else {
            window.location.href = "/catalog";
        }
    } catch (err) {
        console.error(err);
        window.location.href = "/catalog";
    }
})();

(function() {
    const token = localStorage.getItem("token");
    if (!token) {
        window.location.href = "/catalog";
        return;
    }

    const payload = Utils.parseJwt(token);
    if (!payload || !payload.role) {
        window.location.href = "/catalog";
        return;
    }

    if (payload.role === "ADMIN") {
        window.location.href = "/admin";
    } else {
        window.location.href = "/catalog";
    }
})();

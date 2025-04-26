const Utils = {
    parseJwt: function (token) {
        try {
            const base64Url = token.split('.')[1];
            const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
            const jsonPayload = decodeURIComponent(atob(base64).split('').map(function (c) {
                return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
            }).join(''));

            return JSON.parse(jsonPayload);
        } catch (e) {
            return null;
        }
    }
}

function logout() {
    localStorage.removeItem("token");
    window.location.href = "/signin";
}

document.addEventListener("DOMContentLoaded", function() {
    const token = localStorage.getItem("token");
    if (!token) return;

    const payload = Utils.parseJwt(token);
    if (!payload) return;

    // Safe wait until userName and adminButton actually appear
    const userNameSpan = document.getElementById("userName");
    const adminButton = document.getElementById("adminButton");

    if (userNameSpan) {
        userNameSpan.textContent = payload.username || "User";
    }

    if (adminButton && payload.role === "ADMIN") {
        adminButton.classList.remove("d-none");
    }
});

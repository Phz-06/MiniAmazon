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
    if (token) {
        const payload = Utils.parseJwt(token);
        if (payload) {
            // Set username
            const userNameSpan = document.getElementById("userName");
            if (userNameSpan) {
                userNameSpan.textContent = payload.username || "User";
            }

            // Show admin button if user is admin
            if (payload.role === "ADMIN") {
                const adminButton = document.getElementById("adminButton");
                if (adminButton) {
                    adminButton.classList.remove("d-none");
                }
            }
        }
    }
});


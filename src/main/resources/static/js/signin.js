import { parseJwt } from './utils.js';

function isSignedIn() {
    const token = localStorage.getItem("token");
    if (!token) {
        return false;
    }
    const payload = parseJwt(token);
    if (!payload || !payload.exp) {
        return false;
    }
    const now = Math.floor(Date.now() / 1000);
    return payload.exp > now;
}

// Redirect to "/" if already signed in
if (isSignedIn()) {
    window.location.href = window.location.origin + "/";
}

document.getElementById("signinForm").addEventListener("submit", async function(event) {
    event.preventDefault();

    const username = document.getElementById("inputUsername").value.trim();
    const password = document.getElementById("inputPassword").value;

    try {
        const res = await fetch("/api/auth/login", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                username: username,
                password: password
            })
        });

        if (!res.ok) {
            const text = await res.text();
            alert("Sign-in failed: " + text + ".");
            return;
        }

        const data = await res.json();
        localStorage.setItem("token", data.token); // Store JWT in local storage
        window.location.href = window.location.origin + "/"; // Redirect to main page
    } catch (err) {
        console.error(err);
        alert("Error when signing in. Please try again later.")
    }
});

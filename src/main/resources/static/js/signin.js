document.getElementById("signinForm").addEventListener("submit", async function(event) {
    event.preventDefault();

    const email = document.getElementById("inputEmail").value;
    const password = document.getElementById("inputPassword").value;

    try {
        const res = await fetch("/api/auth/login", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                username: email,
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
        window.location.href = "/"; // Redirect to main page
    } catch (err) {
        console.error(err);
        alert("Error when signing in. Please try again later.")
    }
});

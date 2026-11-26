document.getElementById("registerForm").addEventListener("submit", async function(event) {
    event.preventDefault();

    const username = document.getElementById("inputUsername").value.trim();
    const email = document.getElementById("inputEmail").value.trim();
    const name = document.getElementById("inputName").value.trim();
    const password = document.getElementById("inputPassword").value;
    const repeatPassword = document.getElementById("inputRepeatPassword").value;

    if (password !== repeatPassword) {
        alert("Password does not match.");
        return;
    }

    const [firstName, ...lastParts] = name.split(" ");
    const lastName = lastParts.join(" ") || "-";

    try {
        const res = await fetch("/api/auth/signup", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                username: username,
                email: email,
                password: password,
                firstName: firstName,
                lastName: lastName
            })
        });

        if (!res.ok) {
            const contentType = res.headers.get("content-type");
            if (contentType && contentType.includes("application/json")) {
                const errorJson = await res.json();
                let messages = Object.values(errorJson).join("\n");
                alert("Registration failed:\n" + messages);
            } else {
                const text = await res.text();
                alert("Registration failed: " + text);
            }
            return;
        }

        const data = await res.json();
        localStorage.setItem("token", data.token); // Store JWT in local storage
        window.location.href = window.location.origin + "/signin"; // Redirect to sign-in page
    } catch (err) {
        console.error(err)
        alert("Error when registering. Please try again later.");
    }
});

(() => {
    const { api, auth, ui } = App;

    /** Only ever return to a page of this shop. */
    function destination() {
        const next = new URLSearchParams(window.location.search).get("next");
        return next && next.startsWith("/") && !next.startsWith("//") ? next : "/";
    }

    if (auth.isSignedIn()) {
        window.location.replace(destination());
        return;
    }

    const form = document.getElementById("signinForm");
    const alertBox = document.getElementById("formAlert");
    const username = document.getElementById("inputUsername");
    const password = document.getElementById("inputPassword");
    const button = document.getElementById("signinButton");

    ui.wirePasswordToggles(form);

    form.addEventListener("submit", async event => {
        event.preventDefault();
        ui.showFormAlert(alertBox, "");

        ui.setFieldError(username, username.value.trim() ? "" : "Enter your username.");
        ui.setFieldError(password, password.value ? "" : "Enter your password.");
        const firstInvalid = form.querySelector("[aria-invalid='true']");
        if (firstInvalid) {
            firstInvalid.focus();
            return;
        }

        ui.setBusy(button, true, "Signing in…");
        try {
            const data = await api("/api/auth/login", {
                method: "POST",
                anonymous: true,
                body: { username: username.value.trim(), password: password.value }
            });
            auth.setToken(data.token);
            window.location.href = destination();
        } catch (err) {
            const message = err.status === 401
                ? "That username and password don't match. Check both and try again."
                : err.message;
            ui.showFormAlert(alertBox, `<strong>You are not signed in.</strong> ${ui.esc(message)}`);
            ui.setBusy(button, false);
            password.focus();
            password.select();
        }
    });
})();

(() => {
    const { api, auth, ui } = App;

    const form = document.getElementById("registerForm");
    const alertBox = document.getElementById("formAlert");
    const button = document.getElementById("registerButton");

    const username = document.getElementById("inputUsername");
    const email = document.getElementById("inputEmail");
    const name = document.getElementById("inputName");
    const password = document.getElementById("inputPassword");
    const repeat = document.getElementById("inputRepeatPassword");

    ui.wirePasswordToggles(form);

    // The same limits the server applies, checked here first so the answer is immediate.
    const RULES = [
        [username, () => {
            const length = username.value.trim().length;
            return length >= 3 && length <= 20 ? "" : "Choose a username of 3 to 20 characters.";
        }],
        [email, () => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value.trim()) ? "" : "Enter an email address, like name@example.com."],
        [password, () => password.value.length >= 6 && password.value.length <= 40 ? "" : "Choose a password of 6 to 40 characters."],
        [repeat, () => repeat.value === password.value ? "" : "The two passwords don't match. Type the same password again."]
    ];

    const validate = ([input, rule]) => {
        const message = rule();
        ui.setFieldError(input, message);
        return !message;
    };

    RULES.forEach(entry => {
        const [input] = entry;
        input.addEventListener("blur", () => { if (input.value) validate(entry); });
        input.addEventListener("input", () => { if (input.getAttribute("aria-invalid")) validate(entry); });
    });

    /** The server names the field in validation errors and only describes it in the others. */
    function showServerError(err) {
        const byField = { username, email, password };
        let placed = false;
        Object.entries(err.fields).forEach(([field, message]) => {
            if (byField[field]) {
                ui.setFieldError(byField[field], message);
                placed = true;
            }
        });
        if (!placed && /username/i.test(err.message)) {
            ui.setFieldError(username, "That username is taken. Try another one.");
            placed = true;
        } else if (!placed && /email/i.test(err.message)) {
            ui.setFieldError(email, "An account with that email already exists. Sign in instead, or use another address.");
            placed = true;
        }
        if (placed) {
            form.querySelector("[aria-invalid='true']").focus();
        } else {
            ui.showFormAlert(alertBox, `<strong>The account was not created.</strong> ${ui.esc(err.message)}`);
            alertBox.focus();
        }
    }

    form.addEventListener("submit", async event => {
        event.preventDefault();
        ui.showFormAlert(alertBox, "");

        const invalid = RULES.filter(entry => !validate(entry));
        if (invalid.length) {
            invalid[0][0].focus();
            return;
        }

        const [firstName, ...rest] = name.value.trim().split(/\s+/);

        ui.setBusy(button, true, "Creating account…");
        try {
            const data = await api("/api/auth/signup", {
                method: "POST",
                anonymous: true,
                body: {
                    username: username.value.trim(),
                    email: email.value.trim(),
                    password: password.value,
                    firstName: firstName || "",
                    lastName: rest.join(" ") || "-"
                }
            });
            // Signing up signs the customer in.
            auth.setToken(data.token);
            window.location.href = "/";
        } catch (err) {
            ui.setBusy(button, false);
            showServerError(err);
        }
    });
})();

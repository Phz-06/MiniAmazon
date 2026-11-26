/* "/" is only a junction: admins go to the warehouse, everyone else to the catalog. */
(async () => {
    const admin = await App.auth.isAdmin();
    window.location.replace(admin ? "/admin" : "/catalog");
})();

- Regular users may be able to access admin dashboard page.
  - The frontend redirects non-admin users to the catalog page.
  - The backend admin APIs will check for JWT for roles.


- Spring Boot cannot validate `BigDecimal` fields with `@NotEmpty`.
  - Use `@NotNull` for numeric fields like `BigDecimal` to prevent server errors.


- `localStorage` may not be ready before DOM updates.
  - Wait for `"DOMContentLoaded"` before parsing JWT and updating the navbar.


- Thymeleaf template engine cannot process dynamic fragment references when variables are missing.
  - Use conditionally render `th:replace` only when template and fragment are both non-null.


- HTML elements might not exist on all pages.
  - Check for element existence with `document.getElementById != null` before accessing or modifying their properties.


- Even if frontend hides admin buttons, users can manipulate the HTML manually.
  - Backend APIs use role-based access control.


- Trying to parse an HTML error page as JSON in frontend causes JavaScript crashes.
  - Always check `res.ok` before calling `res.json()`.


- After login, if JWT token is missing or corrupted, frontend can be in an inconsistent state.
  - Validate token payload structure before updating UI.

    
- SpringBoot might not keep port number when redirecting.
  - Let frontend redirect or use tools like Nginx.


- Checkout and order payment are separate APIs, which can cause an order to be in an inconsistent state if one fails.
  - Make sure to execute the two operations back-to-back and clean up previous calls if the next one fails.


- Use can access restricted APIs or routes by manually visiting them.
  - Use SpringBoot's security configuration to add role requirement or restrict routes.


- `Authentication` object can be null even after passing `@PreAuthorize("isAuthenticated()")`.
  - Re-validate `authentication != null && authentication.isAuthenticated()` inside controller methods before using authentication.getName().


- Other users may be able to access other user's user-specific APIs.
  - Backend validates user token before doing anything.

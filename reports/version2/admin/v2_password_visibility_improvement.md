# V2 Password Visibility Improvement

## 1. Objective

Add password show/hide controls to every current user-facing password input while preserving its value and existing form behavior.

## 2. Screens Updated

- Login: one password field.
- ADMIN Create Account (`/admin/accounts/new`): one password field; no confirmation field added.
- ADMIN account detail/reset: new password and confirmation, toggled independently.
- Handover execution: VTYT co-sign password, found during the full frontend audit.

All five inputs are masked by default.

## 3. Implementation

Added lightweight `PasswordInput` with local visibility state, explicit input label, forwarded input props and optional help text. The control only switches `type` between `password` and `text`; existing page value/change handlers remain in place. Compact visible text is “Hiện” / “Ẩn”.

Login retains `current-password`; create/reset retain `new-password`. ADMIN username now uses `username`. Co-sign keeps its existing autocomplete setting. Minimal CSS places the control inside the input boundary.

Existing reset success/cancel/error clearing and confirmation validation are unchanged. Successful reset removes the fields; reopening starts empty and masked.

## 4. Accessibility

Real `type="button"` controls have Vietnamese accessible names “Hiện mật khẩu” / “Ẩn mật khẩu”, `aria-controls`, `aria-pressed`, keyboard support and focus indication. Labels use explicit `htmlFor`/IDs, and help text uses `aria-describedby`. Toggle clicks do not submit forms; Enter still submits login.

## 5. Security Impact

**No backend/database/security-model change.** BCrypt, JWT, auth behavior, API contracts, migrations and V1 workflow handlers were not modified. Protected source hashes were checked after implementation.

Passwords are not logged, returned in new API responses, added to URLs or written to browser storage. Visibility exists only in component memory. No password values were saved in screenshots or evidence. Main application data was untouched; temporary account checks used a separate test database and were cleaned up.

## 6. Tests

- `npm run build --prefix frontend`: PASS.
- `npm run lint --prefix frontend`: PASS.
- `npm run test --prefix frontend`: PASS — 10 files, **41/41 tests**.
- Added login tests for default masking, show/hide, value preservation, non-submitting toggles and unchanged failed-login feedback.
- Added ADMIN tests for create toggles, independent reset toggles and API-error clearing. Strengthened existing success/cancel checks; username/department and confirmation validation tests remain intact.
- Backend regression was not run because no backend source changed.

## 7. Manual Browser Validation

PASS using real headless Google Chrome against the unchanged packaged backend, with isolated PostgreSQL `55433`, backend `18080`, frontend `5174`.

- Login: exact typed value preserved through show/hide; keyboard Space activates the toggle without submitting; Enter logs in.
- Create: default masking, show/hide, required department validation and successful UI creation; created account logs in.
- Reset: both fields initially masked; independent controls preserve values; mismatched confirmation is rejected; cancel/success clear values; reopening is empty/masked; old password fails and new password succeeds.
- VTYT co-sign: default masking/show/hide/value preservation verified on the existing handover fixture; no handover command was sent.
- Login/create/reset fit **1366, 760, 390 and 320 px** without horizontal page overflow. Empty-field screenshots were visually inspected, including both reset fields at 320 px.
- Browser checks found no password in URL/localStorage/sessionStorage and no unhandled JavaScript exceptions.
- Temporary account removed; test fixture restored to 15 users. Test services stopped; main services remain running.

Evidence: `.local-run/password-visibility-browser-results.log`; screenshots `.local-run/password-visibility-login.png`, `password-visibility-create.png`, `password-visibility-reset.png` contain empty password fields.

## 8. Files Changed

- `frontend/src/components/PasswordInput.tsx`
- `frontend/src/pages/LoginPage.tsx`
- `frontend/src/pages/LoginPage.test.tsx`
- `frontend/src/pages/AdminAccountFormPage.tsx`
- `frontend/src/pages/AdminAccountDetailPage.tsx`
- `frontend/src/pages/AdminAccounts.test.tsx`
- `frontend/src/pages/ExecutionItemPage.tsx` — password UI replacement only.
- `frontend/src/styles.css`
- `reports/version2/admin/v2_password_visibility_improvement.md`

## 9. Final Status

**PASS** — all requested acceptance criteria passed. No additional V2 feature started.

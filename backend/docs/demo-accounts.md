# Development demo accounts

**DEMO ONLY.** These are synthetic Phase 1.3 users in `medical_maintenance_backend_dev`; they are not hospital identities. The seed hashes are intentionally unknown placeholders. `./backend/scripts/setup-demo-login.sh` assigns new BCrypt hashes to only these four accounts after seed loading. The script refuses another database name, host, port or owner role and checks that all four expected active accounts exist.

| Username | Frozen role | Seeded department | Local password variable |
| --- | --- | --- | --- |
| `demo_vtyt` | `PHONG_VTYT` | Phòng VTYT | `DEMO_VTYT_PASSWORD` |
| `demo_bgd` | `BAN_GIAM_DOC` | None | `DEMO_BGD_PASSWORD` |
| `demo_khoa_noi` | `KHOA_PHONG` | Khoa Nội | `DEMO_KHOA_PASSWORD` |
| `demo_admin` | `ADMIN` | Phòng VTYT | `DEMO_ADMIN_PASSWORD` |

The generated passwords and `JWT_SECRET` are in ignored `.local-postgres/backend-security.env` (mode 600). Source the file in a local shell to use the variables; never copy its values into Git, reports, screenshots or a shared terminal transcript. `--env-only` creates/reuses the secret file before the first Flyway startup. The normal script mode updates the four BCrypt hashes after migrations and the 603-row seed. Re-running it uses the same local passwords with fresh BCrypt salts. Deleting the local file regenerates credentials, so run the normal script again afterward.

Use the [README login flow](../README.md#login) after sourcing both dev env files. That example sends JSON to `curl` on standard input so the demo password does not appear in a command-line argument. Use `/api/auth/me` to verify the token.

The sourced environment and shell token variable contain credentials while the shell runs. Use a private terminal and clear them when done. Do not use these generated accounts in production.

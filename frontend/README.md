# MONIQUE React dashboard — implemented development preview

This frontend is now a real **React 19 / TypeScript / Vite** web application replacing the missing frontend directory referenced by the repository's existing compose.yaml.

## Start the full stack

From the repository root, configure .env with random MONIQUE_ADMIN_TOKEN (at least 32 characters), MONIQUE_ENCRYPTION_KEY_BASE64 (32-byte key), and POSTGRES_PASSWORD. Then:

~~~sh
docker compose up --build
~~~

Open http://127.0.0.1:8088. The Nginx frontend proxies /api to the Spring Boot backend on the private Compose network; PostgreSQL and Core are not published to public interfaces.

Enter the MONIQUE_ADMIN_TOKEN from .env into the login form. It is kept **in the browser tab's memory**, not persisted in localStorage/cookies. Current API authentication is a single-admin preview and is **not** paid account/OIDC membership authentication.

Features implemented:
- Control-plane status, execution-disabled mode and Pod inventory dashboard.
- Register a Pod with SSH credential, encrypted by MONIQUE's backend.
- List and remove local Pod inventory records. Removal never changes a remote server.
- Planning-only Claude/OpenAI chat, with optional selected Pod context.
- Generic API errors without showing provider secrets or SSH credentials.

All remote operations are disabled. Chat suggestions and Pod registration do NOT connect to SSH, verify host keys, deploy software or perform a migration.

## Local frontend development

~~~sh
cd frontend
npm install
npm run dev
npm run build
~~~

Vite proxies /api to http://127.0.0.1:8080 during development. Production is served through frontend/nginx.conf.

Current limitations: no persistent user accounts, paid membership checks, streaming chat, background jobs, real execution workers, or signed desktop installers. All are separate roadmap items.

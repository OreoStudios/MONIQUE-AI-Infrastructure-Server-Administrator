# Deploy MONIQUE from GitHub to a private test VPS

MONIQUE is **not a GitHub Pages application**. Its React frontend needs a Spring Boot backend and a persistent PostgreSQL database. GitHub Actions can validate and deploy MONIQUE, but cannot provide a permanent application host.

> **Early prototype only:** The current backend provides Pod inventory and AI planning chat. The README explicitly warns against production credentials and production hosts. There is no remote SSH execution engine yet. Deploy on a disposable/test VPS and keep the dashboard private.

## 1. Prepare the sandbox server

Use a Linux VPS with Docker Engine + Compose plugin, Git, curl and SSH. Make sure the SSH account has the permissions needed to run Docker. Docker access is effectively root-equivalent; use a dedicated, controlled test environment.

On the VPS, run (adjust ownership/permissions to your environment):

```bash
sudo mkdir -p /opt/monique
sudo chown "$USER":"$USER" /opt/monique
git clone https://github.com/OreoStudios/MONIQUE-AI-Infrastructure-Server-Administrator.git /opt/monique
cd /opt/monique
cp .env.example .env
chmod 600 .env
```

Edit `/opt/monique/.env` with your *own* generated secrets. Do not commit it to GitHub and do not paste secrets into a pull request, Actions log or chat.

| Variable | Required? | Value |
| --- | --- | --- |
| `MONIQUE_ADMIN_TOKEN` | Yes | Random secret, at least 32 characters (`openssl rand -hex 32`) |
| `MONIQUE_ENCRYPTION_KEY_BASE64` | Yes | Base64 encoding of exactly 32 random bytes (`openssl rand -base64 32`); keep stable when restarting to retain decryptability |
| `POSTGRES_PASSWORD` | Yes | Random database password (`openssl rand -hex 24`) |
| `MONIQUE_WEB_ORIGIN` | Yes for correct cross-origin configuration | `http://localhost:8088` when using the SSH tunnel below; if behind an approved HTTPS proxy, use the exact HTTPS origin |
| `ANTHROPIC_API_KEY` | Optional | Anthropic API key for Claude mode |
| `OPENAI_API_KEY` | Optional | OpenAI **API** key for CODEX mode (not a ChatGPT subscription) |
| `MONIQUE_CLAUDE_MODEL` | Optional | Currently defaults to `claude-sonnet-4-5`; requires provider access |
| `MONIQUE_CODEX_MODEL` | Optional | Currently defaults to `gpt-5.1-codex`; requires provider access |

Example placeholders (NOT actual secrets):

```dotenv
MONIQUE_ADMIN_TOKEN=REPLACE_WITH_A_RANDOM_64_CHARACTER_HEX_TOKEN
MONIQUE_ENCRYPTION_KEY_BASE64=REPLACE_WITH_BASE64_OF_32_RANDOM_BYTES
POSTGRES_PASSWORD=REPLACE_WITH_RANDOM_DB_PASSWORD
MONIQUE_WEB_ORIGIN=http://localhost:8088
ANTHROPIC_API_KEY=
OPENAI_API_KEY=
MONIQUE_CLAUDE_MODEL=claude-sonnet-4-5
MONIQUE_CODEX_MODEL=gpt-5.1-codex
```

Run the initial stack:

```bash
cd /opt/monique
docker compose up -d --build
docker compose ps
curl -I http://127.0.0.1:8088/
```

Docker Compose publishes the UI only on `127.0.0.1:8088` on the **server**, and the backend/database remain on the Docker private network. Do not expose ports 8080/5432 publicly.

## 2. Configure GitHub Actions deployment connection

Go to **Repository → Settings → Secrets and variables → Actions**.

Under **Variables (repository variables)**, add:

| Name | Value |
| --- | --- |
| `MONIQUE_DEPLOY_HOST` | DNS name or IP address of **your test VPS**, reachable from GitHub Actions |
| `MONIQUE_DEPLOY_USER` | Dedicated SSH deployment username on that VPS |

Under **Secrets (repository secrets)**, add:

| Name | Value |
| --- | --- |
| `MONIQUE_DEPLOY_SSH_KEY` | Entire private key of an SSH key pair dedicated to GitHub Actions deployment |
| `MONIQUE_DEPLOY_KNOWN_HOSTS` | Verified SSH host key line(s) for your VPS, formatted like `known_hosts` |

Generate a dedicated SSH key pair on your own trusted machine with `ssh-keygen -t ed25519 -f monique_deploy_key`. Install only its `.pub` file in the deployment user's `~/.ssh/authorized_keys` on the VPS. Store its **private** key in the GitHub secret above. Never commit either the private key or your `.env`.

Obtain the server's SSH host key from a **trusted** server console or an independently verified fingerprint; do not blindly trust unverified `ssh-keyscan` output. GitHub Actions uses strict host-key checking. This workflow currently assumes SSH port 22 and path `/opt/monique`.

**Runtime values stay in `/opt/monique/.env` on the server**, not in GitHub Actions secrets. Only the two deployment connection values and two SSH secrets belong in GitHub.

## 3. Deploy

After merging the deployment PR into `main`, open **Actions → Deploy MONIQUE to sandbox VPS → Run workflow** on the `main` branch.

The workflow connects over verified SSH, refuses tracked local changes, checks out the workflow's exact Git commit, runs `docker compose up -d --build`, and checks the loopback dashboard and protected API. It is **manual**, never triggered automatically by a push. It does not configure DNS, TLS, a public proxy or production authentication.

## 4. Open MONIQUE privately

From your own computer, run:

```bash
ssh -L 8088:127.0.0.1:8088 YOUR_DEPLOY_USER@YOUR_VPS
```

Then open `http://localhost:8088` in a local browser. Sign in using the `MONIQUE_ADMIN_TOKEN` from the server's `.env`. Use test Pods and disposable data only.

To publish the dashboard externally later, first implement a proper production access model (individual accounts/RBAC/MFA), HTTPS, rate limiting, auditing, security review and reverse-proxy hardening. Avoid publicly exposing the current single-admin-token prototype.

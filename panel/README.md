# Vitrine Panel

Web panel for the Vitrine catalog, similar to Modrinth's moderation flow:

- **Human check first**: every visitor solves a Cloudflare Turnstile check
  before any page or API is reachable.
- **Browse**: public catalog with search, categories, details and downloads.
- **Suggestions**: players post ideas, bug reports and content wishes and vote
  on them; moderators mark them planned/done/declined or hide them.

- **Everyone** signs in with Google and can upload `.vpkg` packages.
- Every upload is checked automatically (zip safety, manifest, file types,
  ownership of the package id, rate limit) and then waits for a moderator.
- **Moderators** (e-mail whitelist) see the queue, download and inspect files,
  approve or reject with a reason, remove published packages and block users.
- **Approve** signs the file with the registry key, publishes it as a GitHub
  release in `vitrine-uploads`, writes `packages/<id>.json` to
  `vitrine-registry`, and the registry rebuilds its index. A few minutes later
  the package is in the in-game store.

Runs on Cloudflare's free tier: Workers (code), D1 (database), R2 (pending
uploads).

## Security

| Area | Measure |
|---|---|
| Login | Google OpenID Connect with PKCE, `state` and `nonce`; ID token verified locally (signature, issuer, audience, expiry, verified e-mail) |
| Sessions | 256-bit random token, `__Host-` cookie, `HttpOnly`, `Secure`, `SameSite=Lax`, 7 days; database stores only its SHA-256 |
| CSRF | Same-origin `Origin` check plus per-session token header on every write |
| Roles | Moderators = `MODERATOR_EMAILS` secret, re-checked on every request |
| Uploads | Max 10 MB, 10 per day per user; zip slip, absolute paths, symlinks, ZIP64, encryption, duplicate names, header mismatches, zip bombs and unknown file types are rejected; Java mods are never accepted |
| Ownership | A package id belongs to its first uploader; others cannot publish versions of it |
| Storage | Pending files live in a private R2 bucket; only moderators can download them |
| Publishing | Signing key and GitHub token exist only as Worker secrets; failed publishes roll back to "pending" |
| Browser | Strict CSP (no inline scripts; the only third-party script is Cloudflare Turnstile), `frame-ancestors 'none'`, HSTS, `nosniff`, no referrer; all user text rendered with `textContent` |
| Bots | Turnstile gate in front of everything; pass cookie signed with HMAC-SHA256, 12 h; refuses to run with Cloudflare's test keys on a public URL |
| Abuse | Rate limits: 20 logins/h per IP, 5 suggestions/day, 10 uploads/day, 200 votes/day per user |
| Privacy | Suggestions show a self-chosen nickname only; Google name and e-mail are never public; reserved nicknames (admin, moderator, …) are refused |
| Audit | Every login, upload, download, approval, rejection, removal and block is logged |

## Setup (one time)

You need: a Cloudflare account (free), a Google Cloud project (free), and the
GitHub account `umgekocht`.

1. **Install and log in**
   ```
   npm install
   npx wrangler login
   ```
2. **Create database and storage**
   ```
   npx wrangler d1 create vitrine-panel
   npx wrangler r2 bucket create vitrine-panel-uploads
   ```
   Paste the printed `database_id` into `wrangler.toml`, then:
   ```
   npm run db:init
   ```
3. **Deploy once** to get your URL (`https://vitrine-panel.<you>.workers.dev`):
   ```
   npx wrangler deploy
   ```
   Put that URL into `PUBLIC_URL` in `wrangler.toml`.
4. **Google login**: Google Cloud Console → APIs & Services → Credentials →
   *Create OAuth client ID* → *Web application*.
   Authorized redirect URI: `<PUBLIC_URL>/auth/callback`.
   On the consent screen choose *External* and publish it.
5. **GitHub**: create a public repo `umgekocht/vitrine-uploads` (holds the
   released files). Then create a *fine-grained personal access token* with
   access to **only** `vitrine-registry` and `vitrine-uploads`, permission
   **Contents: Read and write**, nothing else.
6. **Secrets** (each command asks for the value; nothing is stored in files):
   ```
   npx wrangler secret put GOOGLE_CLIENT_ID
   npx wrangler secret put GOOGLE_CLIENT_SECRET
   npx wrangler secret put MODERATOR_EMAILS
   npx wrangler secret put GITHUB_TOKEN
   npx wrangler secret put REGISTRY_SIGNING_KEY < %USERPROFILE%\.vitrine\signing-key
   ```
   `MODERATOR_EMAILS` is a comma separated list, e.g. `umgekocht@users.noreply.github.com`.
7. **Turnstile** (the human check): Cloudflare dashboard → *Turnstile* →
   *Add widget*, domain = your panel URL host, mode *Managed*. Put the
   **site key** into `TURNSTILE_SITE_KEY` in `wrangler.toml`, then:
   ```
   npx wrangler secret put TURNSTILE_SECRET
   npx wrangler secret put GATE_SECRET
   ```
   For `GATE_SECRET` paste 32+ random characters (for example the output of
   `node -e "console.log(require('crypto').randomBytes(32).toString('base64url'))"`).
8. **Deploy again**: `npx wrangler deploy`.

## Local development

```
npm install
npm run db:init:local
npx wrangler dev --local
```

Create `.dev.vars` with `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`,
`MODERATOR_EMAILS`, `GATE_SECRET` and `TURNSTILE_SECRET=1x0000000000000000000000000000000AA`
(Cloudflare's always-pass test secret) for local runs. Tests: `npm test`.

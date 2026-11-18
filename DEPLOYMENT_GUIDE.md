# SilentEcho deployment guide

Target architecture:

- **Frontend:** Vercel, static site from the `frontend` folder.
- **Backend:** Railway, Spring Boot from `Backend/silentecho-backend` using the Dockerfile.
- **Database:** Railway MySQL 8.
- **Evidence files:** a Railway volume mounted at `/app/data`.

**Status:** this has not been deployed or tested on Railway or Vercel. The steps below are what to do, and the checks at the end are what to confirm. Work through them in order.

**Existing local database:** before running the updated backend against an existing SilentEcho database, take a backup and run `db/migrations/V2__organisation_identity_and_deduplication.sql` once. It consolidates duplicate organisation workspaces and preserves their cases and memberships. For a brand-new empty database, use `db/schema.sql` instead; do not run both. After upgrading, sign in with an existing organisation admin Sign-in ID and use the dashboard's Administrator recovery panel to generate and save the first recovery key for that workspace.

## 0. Before you start

- Run the local checks first (see `README.md`): `.\mvnw.cmd clean verify` in the backend, and `npm test` in `frontend\tests`.
- Accounts: GitHub, Railway and Vercel. Each has a free tier. Check current pricing and limits on their websites before you rely on them.
- Make sure `.env` does **not** exist in the folder you will upload. Only `.env.example` should be in the repository.

## 1. Put the project on GitHub

```powershell
cd SilentEcho_Final_Deployable
git init
git add .
git status                       # confirm: no .env, no target\, no node_modules\, no data\
git commit -m "SilentEcho: pseudonymous reporting platform"
git branch -M main
git remote add origin https://github.com/YOUR-USER/silentecho.git
git push -u origin main
```

If `git status` lists `.env`, stop, remove it from the staging area with `git rm --cached .env`, and add it to `.gitignore` before committing.

## 2. Railway: create the database

1. In Railway, choose **New Project → Deploy MySQL**. Keep the default service name, `MySQL`, or note the name you chose.
2. Open the MySQL service's **Variables** tab. Note that it provides `MYSQLHOST`, `MYSQLPORT`, `MYSQLUSER`, `MYSQLPASSWORD` and `MYSQLDATABASE`. Check the names in your dashboard, because they are what the next step references.
3. Apply the base schema once for a brand-new database. If you are upgrading an existing SilentEcho database, run the V2 migration instead (after a backup). Use Railway's **Connect** panel to get a connection, and run the appropriate SQL file from your PC with the MySQL client:

```powershell
# Replace the placeholders with the values from the MySQL service's Connect panel
mysql -h HOST -P PORT -u USER -p DATABASE < db\schema.sql
# Existing database upgrade only:
# mysql -h HOST -P PORT -u USER -p DATABASE < db\migrations\V2__organisation_identity_and_deduplication.sql
```

Use a password with no special characters on the command line, or enter it at the prompt with `-p` and no value. The schema is idempotent, so running it twice is safe.

## 3. Railway: create the backend service

1. In the same project, choose **New → GitHub Repo** and select the repository.
2. Open the service's **Settings**:
   - **Root Directory:** `Backend/silentecho-backend`. Railway then finds the Dockerfile in that folder.
   - **Health Check Path:** `/api/health`.
3. Open **Volumes** and add a volume with **Mount Path** `/app/data`. Evidence files are stored in `/app/data/evidence`. Without a volume, uploads are lost on each redeploy.
4. Open **Variables** and add the values below. Replace each placeholder, and mark the two secrets as secret if your dashboard offers that option.

| Variable | Value |
| --- | --- |
| `SILENTECHO_DB_URL` | `jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true` |
| `SILENTECHO_DB_USERNAME` | `${{MySQL.MYSQLUSER}}` |
| `SILENTECHO_DB_PASSWORD` | `${{MySQL.MYSQLPASSWORD}}` |
| `SILENTECHO_HMAC_SECRET` | output of `scripts\new-secrets.ps1`, line 1 (never commit) |
| `SILENTECHO_ENCRYPTION_KEY` | output of `scripts\new-secrets.ps1`, line 2 (never commit) |
| `SILENTECHO_EVIDENCE_DIR` | `/app/data/evidence` |
| `SILENTECHO_COOKIE_SECURE` | `true` (required by the `prod` profile) |
| `SILENTECHO_COOKIE_SAMESITE` | `lax` |
| `SILENTECHO_ALLOWED_ORIGINS` | your Vercel URL from step 4, for example `https://silentecho.vercel.app` |
| `SILENTECHO_DDL_AUTO` | `update` for the first deploy. After the first healthy start, change it to `validate`. |
| `GEMINI_API_KEY` | optional. Leave it out to use the rule-based fallback. |

If your MySQL service is not named `MySQL`, replace `MySQL.` in the three database rows with its actual name.

Do not set `PORT`. Railway provides it, and the application reads it.

5. **Deploy.** Then open **Deployments → View logs** and confirm:
   - a line containing `Started SilentechoBackendApplication`;
   - no `Production profile requires` error (that means a required variable is missing or wrong);
   - no `Evidence directory is missing or not writable` error (that means the volume is not mounted at `/app/data`).
6. In **Settings → Networking**, generate a public domain. Open `https://YOUR-SERVICE.up.railway.app/api/health` in a browser. It should show `{"status":"UP","service":"silentecho-backend"}`.

## 4. Vercel: deploy the frontend

1. **Edit `frontend/vercel.json` before the first deploy.** In the rewrite destination, replace `REPLACE-WITH-YOUR-RAILWAY-BACKEND.up.railway.app` with your Railway domain from step 3.6. Keep the `https://` and `/api/:path*` already in the line, so it reads `https://YOUR-SERVICE.up.railway.app/api/:path*`. Commit and push the change.
2. In Vercel, choose **Add New → Project**, import the GitHub repository, and set:
   - **Root Directory:** `frontend`
   - **Framework Preset:** Other
   - **Build Command:** leave empty
   - **Output Directory:** leave empty
3. Deploy. Note the URL, for example `https://silentecho.vercel.app`.
4. Go back to Railway, set `SILENTECHO_ALLOWED_ORIGINS` to that exact URL, and redeploy the backend. The origin must match exactly, with no trailing slash.

## 5. Check the live system

Run these checks in order. Stop at the first failure and fix it before continuing.

1. **The rewrite works:**
   ```powershell
   Invoke-RestMethod https://silentecho.vercel.app/api/health
   ```
   Expected: `status` is `UP`. If you get HTML, the rewrite is not active: check `frontend/vercel.json` and the Root Directory setting. If you get a 502 or 404, the destination domain is wrong.
2. **Security headers and the content security policy:** `curl.exe -I https://silentecho.vercel.app` (or open DevTools → Network → the first request → Headers). Expected: `Content-Security-Policy`, `X-Frame-Options: DENY` and `X-Content-Type-Options: nosniff` are present.
3. **Account creation:** open the site, choose **Create account**, generate the ID, copy it, tick the box and continue. Refresh the page after you have signed out and check that you can sign back in with the saved ID.
4. **Cookie:** in DevTools → Application → Cookies, the cookie `SE_SESSION` should be listed under the Vercel domain, with `HttpOnly`, `Secure` and `SameSite=Lax`. If it is missing, the Vercel rewrite is not passing the `Set-Cookie` header through. In that case, stop and tell me, because the fallback changes the architecture.
5. **A full journey:** submit a report with an evidence file, track it, send a message, sign out. Then sign in to an organisation account, open the case, change its status, and reply. Reporters should see the change.
6. **Negative checks:** try a wrong sign-in ID six times, then confirm you are locked out for 15 minutes. Try to open another reporter's case ID while signed in as someone else. Expected: "not found".
7. **Restart test:** redeploy the backend and confirm that a previously uploaded evidence file can still be downloaded. If it cannot, the volume is not mounted correctly.

## Troubleshooting

| Symptom | Likely cause |
| --- | --- |
| Backend exits at start with `Production profile requires SILENTECHO_COOKIE_SECURE=true` | Variable missing or set to `false` |
| `Evidence directory is missing or not writable` | Volume not mounted at `/app/data`, or not attached to this service |
| `Access denied for user` or a JDBC error | Database variables do not match the MySQL service names |
| Login works on Vercel but sessions are lost after a few minutes | Expected after 30 minutes of inactivity. Otherwise check the cookie flags in step 5.4. |
| Sign-in fails with `403` and "Your request was blocked" | `SILENTECHO_ALLOWED_ORIGINS` does not match the Vercel URL exactly |
| `/api/*` returns 502 on Vercel | `vercel.json` destination is wrong, or the Railway service is not running |

## Rolling back

Railway keeps previous deployments: open the service's **Deployments** list and redeploy an earlier one. Vercel keeps previous deployments too, and you can promote one from the **Deployments** page. Back up the MySQL database before schema changes, and never change `SILENTECHO_HMAC_SECRET` or `SILENTECHO_ENCRYPTION_KEY` on a live system without a planned migration, because existing sign-in IDs and encrypted content depend on them.

## What is not covered here

- Automatic backups of MySQL and the evidence volume. Set these up in Railway and test a restore.
- Monitoring and alerting.
- Custom domains and HTTPS certificates beyond the defaults Vercel and Railway provide.
- A content security review by a qualified person, or penetration testing. The automated checks in the repository do not replace either.

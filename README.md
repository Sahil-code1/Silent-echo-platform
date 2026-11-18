# SilentEcho

SilentEcho is a privacy-focused reporting and case-management platform. People can report bullying, harassment, discrimination or other sensitive concerns without giving a name, email address or phone number. They sign in with a private, randomly generated sign-in ID and keep a two-way conversation with an organisation's response team through a case.

**Pseudonymous, not anonymous.** What you write, the dates and places you mention, and any files you upload can still identify you. The operator holds the encryption key, so the service is **not end-to-end encrypted**. The About page in the app says the same.

## Verification status (read this first)

| Check | Result |
| --- | --- |
| Backend Maven build and integration tests | **The previous package passed 48 tests.** This update adds organisation duplicate/recovery code and four tests; the new full Maven build has not been run in this environment. Run `mvnw.cmd clean verify` locally before deployment. |
| Organisation duplicate prevention | Added: normalized name key, service-level HTTP 409 conflict response, and MySQL unique constraint. An integration test now covers case/whitespace variants. |
| Existing database migration | Added at `db/migrations/V2__organisation_identity_and_deduplication.sql`. It merges duplicate workspaces into the oldest row and reassigns memberships, complaints, invitations and audit events. **Back up the database and run this migration once before starting the updated backend.** |
| Organisation recovery | Added one-time `SER-...` recovery keys. Existing administrators can generate/rotate a key from the dashboard; recovery creates a replacement admin account and rotates the key. Integration test added; full Maven suite still needs local verification. |
| Schema/entity static check | **Passed** with `node db/check-schema-vs-entities.js` (10 entity tables match 10 schema tables). |
| Frontend syntax, API route mapping and DOM ID checks | **Passed**: JavaScript syntax check; 23 API calls matched, zero missing; 162 DOM lookups resolve. |
| Frontend jsdom and responsive suites | Not rerun in this packaging environment because `frontend/tests` dependencies are not installed here. Run `npm install` then `npm test` locally; run `npm run responsive` for the browser layout suite. |
| MySQL 8 migration | Not run in this environment. Your local MySQL 8.4 instance must run the V2 migration before testing this updated package. |
| Deployed on Vercel and Railway | **Not deployed.** See `DEPLOYMENT_GUIDE.md`. |


## How it fits together

```
Browser ──► Vercel (static frontend/, vercel.json)
              │  /api/* is rewritten to the Railway backend, so the browser sees one origin
              ▼
           Railway: Spring Boot 4 backend (Dockerfile in Backend/silentecho-backend)
              ├── Spring Security: server-side sessions, CSRF, session fixation protection
              ├── Controllers ──► Services ──► JPA repositories ──► MySQL 8 (Railway)
              └── Evidence files, AES-256-GCM encrypted, on a persistent volume (/app/data)
```

Key rules in the code:

- **Sign-in IDs are never stored.** The database holds `HMAC-SHA-256(secret, "signin:" + id)`.
- **Case IDs are references, not credentials.** Every case lookup is scoped to the caller's own account or organisation. A case you may not see returns 404, the same as a case that does not exist.
- **Reporter identity is hidden from organisations.** Organisation views never include the reporter's internal account ID.
- **An organisation name can be registered only once.** Duplicate checking is case-insensitive and collapses repeated whitespace; a unique MySQL key also protects concurrent requests. A duplicate returns HTTP 409 and directs the user to sign in or ask the existing organisation team for access.
- **Organisation codes are stable references, not credentials.** Each workspace receives a short `SE-ORG-...` code. Students choose the workspace by its name and code; complaints are linked to the internal organisation UUID, not to a person's Sign-in ID. Every student has a separate Personal Sign-in ID. Each organisation administrator/responder has a separate account Sign-in ID.
- **Administrator recovery is separate from the public organisation code.** An organisation receives a one-time `SER-...` recovery key when registered. It is stored only as a keyed HMAC digest. If the administrator loses their Sign-in ID, the key and `SE-ORG-...` code create a replacement administrator account; the recovery key is rotated after use. Existing administrators can generate/rotate a recovery key from the Organisation Dashboard. Save it offline and keep it private.
- **Existing duplicate workspaces are merged by migration.** `db/migrations/V2__organisation_identity_and_deduplication.sql` keeps the oldest workspace, moves complaints, invitations, audit events and memberships to it, and retains administrator accounts as members. Back up MySQL and run this migration once before starting the updated application.
- **Content is encrypted at rest** with AES-256-GCM. Each ciphertext is bound to its record, so it cannot be copied into another record.

## Folder structure

```
SilentEcho_Final_Deployable/
├── README.md, DEPLOYMENT_GUIDE.md
├── .env.example                    placeholders only; copy to .env (never commit .env)
├── .gitignore
├── db/
│   ├── schema.sql                  MySQL 8 schema for a new database
│   ├── migrations/V2__organisation_identity_and_deduplication.sql  one-time upgrade for existing databases
│   └── check-schema-vs-entities.js static check: schema matches the JPA entities
├── scripts/
│   ├── load-env.ps1                loads .env into the PowerShell session (never prints values)
│   └── new-secrets.ps1             prints fresh HMAC and encryption secrets
├── frontend/                       static site, deployed to Vercel with Root Directory = frontend
│   ├── index.html                  original SilentEcho layout, with the pseudonymous screens
│   ├── css/style.css               original stylesheet, unchanged, plus an appended integration section
│   ├── js/config.js                API base: localhost:8080 locally, same origin in production
│   ├── js/app.js                   routing, API client, forms, case views
│   ├── vercel.json                 /api/* rewrite to Railway, and security headers with a CSP
│   └── tests/                      route and DOM checks, jsdom tests, real-browser layout check
└── Backend/silentecho-backend/     Spring Boot 4 project (Root Directory on Railway)
    ├── Dockerfile, .dockerignore, pom.xml, mvnw, mvnw.cmd
    └── src/main/java/com/silentecho/silentecho_backend/
        ├── config/      AppProperties, CoreBeans, SecurityConfig, ProductionGuard
        ├── controller/  Auth, Organisation, PersonalCase, OrganisationCase, Health, AiAnalysis
        ├── dto/  entity/  repository/  service/  security/  evidence/  exception/
    └── src/main/resources/  application.properties, application-prod.properties
    └── src/test/            unit tests, PseudonymousFlowIntegrationTest, context test
```

## Quick start on Windows (PowerShell)

Prerequisites: JDK 17 or newer, MySQL 8, Node.js 18 or newer, Python 3 (for a simple web server; or any static server).

```powershell
# 1. Database
# For a BRAND-NEW database, apply the base schema once:
mysql -u root -p < db\schema.sql
# For an EXISTING database created from the older project schema:
# 1) Back up the database first.
# 2) Run the V2 migration exactly once from the MySQL client:
# SOURCE C:/absolute/path/to/db/migrations/V2__organisation_identity_and_deduplication.sql;
# 3) Start the updated backend, sign in with an existing organisation admin ID,
#    and use Organisation Dashboard -> Administrator recovery -> Generate recovery key.
#    Save the new SER-... key safely.

# 2. Secrets and environment (from the project root)
Copy-Item .env.example .env
.\scripts\new-secrets.ps1          # copy the two output lines into .env, then edit .env
notepad .env                        # set SILENTECHO_DB_PASSWORD and the two secrets above
. .\scripts\load-env.ps1            # the leading dot keeps the variables in this session

# 3. Backend (from Backend\silentecho-backend)
cd Backend\silentecho-backend
.\mvnw.cmd clean verify             # build and run all tests
.\mvnw.cmd spring-boot:run          # API on http://localhost:8080
# Check it in a second PowerShell window:  Invoke-RestMethod http://localhost:8080/api/health

# 4. Frontend (new PowerShell window, from the project root)
cd frontend
python -m http.server 5500          # then open http://localhost:5500 in the browser
```

Use `localhost`, not `127.0.0.1`, in the browser address bar. Browsers treat `localhost:5500` and `localhost:8080` as the same site, so the session cookie is sent.

## Environment variables

| Variable | Required | Notes |
| --- | --- | --- |
| `SILENTECHO_DB_PASSWORD` | yes | Password of the MySQL application user |
| `SILENTECHO_HMAC_SECRET` | yes | 32+ characters. Losing it invalidates every sign-in ID. |
| `SILENTECHO_ENCRYPTION_KEY` | yes | Base64 of exactly 32 bytes. Losing it makes stored content unreadable. |
| `SILENTECHO_DB_URL`, `SILENTECHO_DB_USERNAME` | no | Defaults: `localhost`, `silentecho_user` |
| `SILENTECHO_DDL_AUTO` | no | `update` (default) or `validate` after the schema is applied |
| `SILENTECHO_EVIDENCE_DIR` | no | Default `./data/evidence`. The app refuses to start if it is not writable. |
| `SILENTECHO_ALLOWED_ORIGINS` | no | Exact frontend origins, comma separated |
| `SILENTECHO_COOKIE_SECURE` | no | `false` locally. **Must be `true` under the `prod` profile**, which refuses to start otherwise. |
| `SILENTECHO_COOKIE_SAMESITE` | no | `lax` (default). Correct for both local use and the Vercel same-origin rewrite. |
| `SPRING_PROFILES_ACTIVE` | no | `prod` on Railway (the Dockerfile sets it) |
| `GEMINI_API_KEY` | no | Optional AI triage. Without it, a rule-based fallback is used. |

## API

All endpoints are under `/api`. Errors are JSON of the form `{"message": "..."}`.

| Method | Path | Access |
| --- | --- | --- |
| GET | `/api/health`, `/api/test` | public |
| GET | `/api/auth/csrf`, `/api/auth/me` | public |
| POST | `/api/auth/personal/register` | public; returns the sign-in ID once |
| POST | `/api/auth/login`, `/api/auth/logout` | login is public and throttled; logout needs a session |
| POST | `/api/organisations/register`, `/api/organisations/join` | public; returns the sign-in ID once |
| POST | `/api/organisations/recover` | public; organisation code + unused one-time recovery key |
| GET | `/api/organisations` | signed in |
| POST | `/api/organisations/invitations`, `/api/organisations/recovery-codes` | organisation administrator |
| POST, GET | `/api/personal/cases` | personal account |
| GET | `/api/personal/cases/{caseId}` | owner only |
| POST | `/api/personal/cases/{caseId}/messages`, `/evidence` | owner only |
| GET | `/api/personal/cases/{caseId}/evidence/{fileId}` | owner only |
| GET | `/api/org/cases` | organisation member |
| GET, PATCH | `/api/org/cases/{caseId}` | member (ADMIN or RESPONDER) |
| POST | `/api/org/cases/{caseId}/messages` | member (ADMIN or RESPONDER) |
| GET | `/api/org/cases/{caseId}/evidence/{fileId}` | member of the owning organisation |
| POST | `/api/ai/analyze` | signed in; optional advisory triage |

`frontend/tests/check-api-routes.js` checks that every call the frontend makes matches one of these mappings.

## Cases, evidence and roles

- **Stages:** Submitted → Under review → In progress → Resolved → Closed. Responders may set any stage, and each change is written to the timeline.
- **Evidence:** PDF, JPG/JPEG, PNG or TXT, 1 byte to 2 MB. The server checks the extension, the file's signature bytes, and any declared content type. Filenames are sanitised, and files are stored under random names, encrypted, outside the web root. Closed cases do not accept new evidence.
- **Who can see a case:** the reporter who filed it, and members of the organisation that owns it. Anyone else gets a 404.

## Tests

```powershell
# Backend (from Backend\silentecho-backend). Runs unit and integration tests.
.\mvnw.cmd clean verify

# Frontend static checks, jsdom functional tests (from frontend\tests)
cd frontend\tests
npm install
npm test                       # routes + DOM IDs + 58 functional checks

# Real-browser layout check (needs Chrome or Edge installed)
$env:CHROME_PATH = "C:\Program Files\Google\Chrome\Application\chrome.exe"
npm run responsive
```

`npm run serve` starts the frontend with an in-memory mock API, for design work without the backend. Its sign-in IDs are printed on start. It is not a security test.

## Deployment

See **`DEPLOYMENT_GUIDE.md`**: GitHub, Railway (backend and MySQL, with a persistent volume), and Vercel (frontend), with the checks to run after each step.

## Security limitations

- Not end-to-end encrypted. The operator holds the encryption key.
- Pseudonymous, not anonymous. Writing style, dates, places, names and file metadata can identify someone. File metadata (EXIF, document authors) is not stripped.
- A lost sign-in ID cannot be recovered. A sign-in ID is a bearer secret: anyone who has it can sign in.
- Organisation registration is self-service and not verified.
- Sessions and the login throttle are held in memory. A restart signs everyone out and clears lockouts, and multiple instances need a shared session store.
- Account creation is not rate-limited, and invitation redemption is not transactionally locked (codes are 128-bit random, so guessing is impractical).
- Uploaded files are type-checked and encrypted but not malware-scanned.
- No retention or deletion workflow. Audit logs are not tamper-evident.
- The frontend CSP allows `'unsafe-inline'` styles, because the page uses 19 inline `style=` attributes. Scripts are restricted to the same origin.
- Not an emergency service. Cases are not watched in real time.
- Key rotation is not implemented. Changing the HMAC secret or encryption key breaks existing data and needs a planned re-encryption.

## Migrating from the Firebase version

Firebase Authentication, the Firebase Admin SDK, the client SDK and their configuration are removed from the active code. The old `users` and `reports` tables are not migrated. Create the schema from `db/schema.sql`. The old anonymous tracking-by-code endpoint is replaced by an authenticated case view. Rotate the database password that appeared in the old `application.properties`, and decommission the old Firebase project.

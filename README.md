# walls-gate

**Ethical-wall-aware gateway for local LLMs.** An OpenAI-compatible proxy for firms that enforces information barriers *before* a model sees any document.

> Status: MVP / proof of concept. Synthetic demo data only. Not production-hardened (see Roadmap).

## What it does

| Capability | Where |
|---|---|
| OpenAI-style `POST /v1/chat/completions` in front of any local model (vLLM, Ollama, llama.cpp) | `GatewayController`, `LlmClient` |
| Permission-aware retrieval: docs a user can't open never reach the model | `Retriever` |
| Wall rules: block (or flag) sessions that touch two conflicted matters | `WallEngine` |
| DLP / redaction (email, IBAN, phone, client names, matter numbers) on prompts, context and answers | `Redactor` |
| Shadow mode: log what *would* be blocked, without blocking | `wallsgate.shadow-mode` |
| Audit trail with CSV export: who, what, which docs, blocked/allowed, why | `AuditService`, `/admin/audit.csv` |
| Admin API for matters, walls, users, documents | `AdminController` |

## Run it

Requires Java 25 and Maven 3.9+.

```bash
mvn test
mvn spring-boot:run
```

Demo users: alice = M-1001 + M-3003, bob = M-2002, carol = M-1001 + M-2002 (these two are walled).

```bash
Q='{"messages":[{"role":"user","content":"Summarise Acme Beta settlement strategy"}]}'
curl -s localhost:8080/v1/chat/completions -H 'Content-Type: application/json' -H 'X-User-Id: alice' -d "$Q"   # allowed
curl -s localhost:8080/v1/chat/completions -H 'Content-Type: application/json' -H 'X-User-Id: bob'   -d "$Q"   # no M-1001 docs
curl -s localhost:8080/v1/chat/completions -H 'Content-Type: application/json' -H 'X-User-Id: carol' -d "$Q"   # 403 ethical_wall
curl -s localhost:8080/admin/audit.csv -H 'X-Admin-Token: change-me'
```

Connect a real model: set `wallsgate.llm.base-url` in `application.yml`, e.g. `http://localhost:8000/v1` for vLLM.
Trial mode for pilots: `wallsgate.shadow-mode: true`.

## Known MVP limits (be upfront with pilot firms)

- **Auth**: trusts the `X-User-Id` header. Run behind an SSO/OIDC reverse proxy that is the only thing allowed to set it. Native OIDC is task #1.
- **Storage**: policy and documents are in memory (lost on restart). Audit is also appended to `audit.jsonl`.
- **Retrieval**: naive keyword matching. Replace with a vector index, keeping filter-first.
- **DLP**: regex only. Add Presidio as a sidecar for NER and multilingual detection.
- **Wall logic**: session-scoped, matter-level. No time-based walls or cleared-user exceptions.
- Position it as "enforced and auditable", never "guaranteed compliant".

## Roadmap

1. Native OIDC and per-user mapping to matters/teams
2. One real document-source importer (file share or the DMS your first pilot uses) with ACL sync
3. Persistent storage and tamper-evident audit log
4. Presidio sidecar and EU-language detectors
5. Admin UI, PDF audit report
6. Red-team suite for cross-matter leakage (prompt injection inside documents, multi-turn probing)

## Layout

```
src/main/java/com/wallsgate/
  gateway/  GatewayController, LlmClient, AuditService
  policy/   PolicyStore, Retriever, WallEngine, Redactor, SeedData
  admin/    AdminController
  model/    Models
```

## CI and deploy (GitHub Actions + Render)

- `.github/workflows/ci.yml` runs `mvn verify` on JDK 25 for every push and PR, then builds the Docker image.
- `render.yaml` is a Render Blueprint; `Dockerfile` is what Render builds.

Deploy steps:
1. Push this repo to GitHub and confirm the Actions run is green.
2. In Render: **New > Blueprint**, connect the repo, select `render.yaml`, apply.
3. Open the service **Environment** tab and copy the generated `ADMIN_TOKEN`.
4. Test: `curl https://<your-service>.onrender.com/health`

Config via env vars: `ADMIN_TOKEN`, `WALLSGATE_SEED`, `WALLSGATE_SHADOW_MODE`, `AUDIT_FILE`, `LLM_BASE_URL`.

**Security warning:** the public Render URL trusts the `X-User-Id` header, so anyone who can reach it can
impersonate any user. Use synthetic demo data only until OIDC/SSO is in place.

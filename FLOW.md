# Complete System Flow, Future Modifications & Scalability Guide

> **Project:** `walls-gate` — Ethical-Wall-Aware Gateway & Agent SDK Runtime for Local LLMs  
> **Repository:** [walls-gate](file:///home/shankar/project/walls-gate)  
> **Runtime Environment:** Java 25 (GraalVM / OpenJDK), Spring Boot 3.5.11, Maven 3.9+  

---

## 1. Executive Summary & Flow Philosophy

When enterprise AI agents interact with Large Language Models (LLMs) in regulated sectors (healthcare, legal, tax, defense, consulting), standard model API endpoints introduce catastrophic compliance risks:
1. **Confidentiality & Information Barrier Collisions:** Agents touching two conflicted matters in the same session cause cross-matter data contamination.
2. **Access Control Bypass:** Agents query global vector indexes using static service keys, ignoring end-user access permissions.
3. **Sensitive PII/PHI Exfiltration:** Raw personal data leaks into model context windows.
4. **Context Window Exhaustion:** Unchecked agent loops exhaust token budgets.

`walls-gate` sits as a **hardened runtime security gateway and SDK layer** between agent feature code and model inference servers (vLLM, Ollama, llama.cpp, OpenAI). It enforces identity verification, token quotas, permission-aware retrieval, multi-turn ethical walls, and DLP sanitization **before** any request reaches model weights.

---

## 2. End-to-End Request Ingestion Flow (`/v1/chat/completions`)

```
+---------------------------------------------------------------------------------------------------+
|                                 CLIENT / AGENT SDK CALLER                                         |
|  POST /v1/chat/completions                                                                        |
|  Headers: X-User-Id: alice | X-Session-Id: sess-101 | X-Trace-Id: tr-xyz89                        |
|  Body: {"messages":[{"role":"user","content":"Summarise Acme litigation strategy"}]}              |
+-------------------------------------------------+-------------------------------------------------+
                                                  |
                                                  v
+---------------------------------------------------------------------------------------------------+
| 1. PRESENTATION ADAPTER: GatewayController                                                        |
|  - Ingests HTTP headers and binds to SLF4J MDC (traceId, userId, sessionId)                       |
|  - Validates payload syntax; constructs immutable ChatCommand DTO                                 |
+-------------------------------------------------+-------------------------------------------------+
                                                  |
                                                  v
+---------------------------------------------------------------------------------------------------+
| 2. ORCHESTRATION SERVICE: GatewayService.processChat(cmd)                                         |
|                                                                                                   |
|  [Step A: Identity Resolution]                                                                    |
|  - Looks up user in PolicyRepository -> User(id="alice", matters=["M-1001", "M-3003"])            |
|  - If missing/unauthorized -> Throws UserNotFoundException (HTTP 401)                             |
|                                                                                                   |
|  [Step B: Token Budget Guard]                                                                     |
|  - TokenBudgetGuard.checkPrompt(session, prompt)                                                  |
|  - Validates prompt length <= maxPromptChars and session cumulative <= sessionCharBudget          |
|  - If exceeded -> Throws TokenBudgetExceededException (HTTP 429)                                   |
|                                                                                                   |
|  [Step C: Permission-Aware Filter-First Retrieval]                                                |
|  - Retriever.search(prompt, allowedMatters=["M-1001", "M-3003"])                                  |
|  - Hard ACL filter applied BEFORE ranking; unauthorized documents never enter memory              |
|  - Output: Matched documents and set of touched matters (e.g. {"M-1001"})                         |
|                                                                                                   |
|  [Step D: Stateful Multi-Turn Ethical Wall Evaluation]                                            |
|  - WallEngine.evaluate(sessionId, touchedMatters={"M-1001"})                                      |
|  - Compares session cumulative matter history against active barriers (Wall(M-1001, M-2002))       |
|  - If Conflict detected:                                                                          |
|      * Strict Mode -> Logs BLOCKED in AuditService, throws EthicalWallViolationException (HTTP 403)|
|      * Shadow Mode -> Logs WOULD_BLOCK in AuditService, flags metadata, proceeds for trial        |
|  - If Clean -> walls.commit(session, matters) updates stateful session graph                      |
|                                                                                                   |
|  [Step E: Pre-Flight DLP & PII Redaction]                                                         |
|  - Redactor.redact() strips Emails, Phones, IBANs, and configured Client entities                 |
|  - Applied to BOTH user prompt and retrieved document context strings                             |
|                                                                                                   |
|  [Step F: Model Inference Connector]                                                              |
|  - LlmClient.complete() formats OpenAI-compatible system prompt and sanitized conversation        |
|  - Dispatches HTTP POST to local inference engine (vLLM / Ollama / llama.cpp / mock)               |
|                                                                                                   |
|  [Step G: Post-Flight Output Sanitization & Budget Accounting]                                    |
|  - Redactor.redact() sanitizes model output text                                                  |
|  - TokenBudgetGuard.guardOutput() truncates runaway output <= maxOutputChars                      |
|  - TokenBudgetGuard.consume(session, promptLen + outputLen) updates quota balance                 |
|                                                                                                   |
|  [Step H: Audit Persistence & Result Packaging]                                                   |
|  - AuditService.record("ALLOWED", matters, docIds, promptSnippet) appends tamper-evident audit log|
|  - Returns ChatResult DTO formatted into OpenAI response JSON + walls_gate metadata               |
+-------------------------------------------------+-------------------------------------------------+
                                                  |
                                                  v
+---------------------------------------------------------------------------------------------------+
| 3. CLIENT RESPONSE (HTTP 200 OK)                                                                  |
|  {                                                                                                |
|    "id": "chatcmpl-uuid",                                                                         |
|    "object": "chat.completion",                                                                   |
|    "model": "local",                                                                              |
|    "choices": [{"index":0,"message":{"role":"assistant","content":"Sanitized reply..."}}],        |
|    "walls_gate": {"documents":["doc-01"],"matters":["M-1001"],"shadow_mode":false}               |
|  }                                                                                                |
+---------------------------------------------------------------------------------------------------+
```

---

## 3. Component Details & Design Principles

### 3.1. Layered Architecture (DDD & SOLID)

| Layer | Classes | Responsibilities |
|---|---|---|
| **Presentation Adapter** | [`GatewayController`](file:///home/shankar/project/walls-gate/src/main/java/com/wallsgate/gateway/GatewayController.java)<br>[`AdminController`](file:///home/shankar/project/walls-gate/src/main/java/com/wallsgate/admin/AdminController.java)<br>[`GlobalExceptionHandler`](file:///home/shankar/project/walls-gate/src/main/java/com/wallsgate/gateway/GlobalExceptionHandler.java) | HTTP routing, header parsing, MDC context binding, OpenAPI metadata, response serialization. **Zero business logic.** |
| **Application Services** | [`GatewayService`](file:///home/shankar/project/walls-gate/src/main/java/com/wallsgate/service/GatewayService.java)<br>[`AdminService`](file:///home/shankar/project/walls-gate/src/main/java/com/wallsgate/service/AdminService.java) | Business workflow orchestration: auth verification, budget checks, retrieval orchestration, wall evaluation, redaction, audit commit. |
| **Domain Layer** | [`PolicyRepository`](file:///home/shankar/project/walls-gate/src/main/java/com/wallsgate/domain/repository/PolicyRepository.java)<br>[`Models`](file:///home/shankar/project/walls-gate/src/main/java/com/wallsgate/model/Models.java)<br>[`Domain Exceptions`](file:///home/shankar/project/walls-gate/src/main/java/com/wallsgate/domain/exception) | Invariant validation, core entities (`Matter`, `Wall`, `Doc`, `User`), domain exceptions (`EthicalWallViolationException`, etc.). |
| **Infrastructure Adapters** | [`PolicyStore`](file:///home/shankar/project/walls-gate/src/main/java/com/wallsgate/policy/PolicyStore.java)<br>[`Retriever`](file:///home/shankar/project/walls-gate/src/main/java/com/wallsgate/policy/Retriever.java)<br>[`WallEngine`](file:///home/shankar/project/walls-gate/src/main/java/com/wallsgate/policy/WallEngine.java)<br>[`TokenBudgetGuard`](file:///home/shankar/project/walls-gate/src/main/java/com/wallsgate/policy/TokenBudgetGuard.java)<br>[`Redactor`](file:///home/shankar/project/walls-gate/src/main/java/com/wallsgate/policy/Redactor.java)<br>[`LlmClient`](file:///home/shankar/project/walls-gate/src/main/java/com/wallsgate/gateway/LlmClient.java)<br>[`AuditService`](file:///home/shankar/project/walls-gate/src/main/java/com/wallsgate/gateway/AuditService.java) | Concrete storage, regex DLP engine, HTTP LLM connector, thread-safe memory maps, audit file persistence. |

### 3.2. Administrative Credential & Policy Flow (`/admin/**`)

```
Admin Request (X-Admin-Token)
       |
       v
AdminController (Presentation Adapter)
       |
       +--> AdminService.authenticate(token)
       |       |
       |       v
       |    Constant-time MessageDigest.isEqual (Timing attack protection)
       |    * Failed? -> Logs masked token warning + Throws UnauthorizedException (HTTP 401)
       |
       +--> AdminService Domain Validation
       |       * Self-walling rejected (e.g. Wall("M-1", "M-1")) -> Throws IllegalArgumentException (HTTP 400)
       |       * Blank IDs or names rejected -> Throws IllegalArgumentException (HTTP 400)
       |
       +--> PolicyRepository Update (Matters, Walls, Users, Documents)
       |
       +--> Audit Log & Formula-Safe CSV Export (/admin/audit.csv)
```

---

## 4. Future Modifications & Scalability Roadmap

To take `walls-gate` from an MVP to a high-throughput, enterprise-grade runtime handling 100,000+ daily frontline users and millions of tokens across distributed clusters, the following architectural upgrades are designed:

### 4.1. Distributed Session & Wall State (Multi-Node Scaling)

* **Current Architecture:** In-memory `ConcurrentHashMap` inside `WallEngine` and `TokenBudgetGuard`. Restricted to single-pod deployments.
* **Target Architecture:**
  ```
  [Gateway Pod 1]     [Gateway Pod 2]     [Gateway Pod 3]
         \                   |                   /
          v                  v                  v
  +-----------------------------------------------------+
  |          Redis Cluster (or Valkey 8.0)              |
  |  - Key: "session:matters:{sessionId}" (Redis Set)   |
  |  - Key: "session:budget:{sessionId}"  (Redis Int)   |
  |  - Lua Script: Atomic Wall Conflict Check & Commit  |
  +-----------------------------------------------------+
  ```
  * **Atomic Wall Evaluation via Lua Script:** Instead of fetching historical matter sets across the network, execute an in-database Lua script inside Redis:
    ```lua
    -- KEYS[1]: session matters set, ARGV[1]: candidate matter, ARGV[2..k]: conflicted matters
    for i = 2, #ARGV do
      if redis.call('SISMEMBER', KEYS[1], ARGV[i]) == 1 then
        return {1, ARGV[i]} -- Conflict detected!
      end
    end
    redis.call('SADD', KEYS[1], ARGV[1])
    redis.call('EXPIRE', KEYS[1], 86400)
    return {0, 'OK'}
    ```
  * **Benefits:** Zero race conditions, sub-millisecond evaluation latency, session persistence surviving pod restarts.

---

### 4.2. Storage Evolution: PostgreSQL + pgvector Hybrid Search

* **Current Architecture:** In-memory keyword match (`Retriever.java`) against `PolicyStore`.
* **Target Architecture:**
  * Implement [`PolicyRepository`](file:///home/shankar/project/walls-gate/src/main/java/com/wallsgate/domain/repository/PolicyRepository.java) using Spring Data JPA / JDBC backing **PostgreSQL 16+ with `pgvector`**.
  * **Database Schema with Matter Partitioning:**
    ```sql
    CREATE TABLE matters (
        id VARCHAR(64) PRIMARY KEY,
        name VARCHAR(255) NOT NULL,
        created_at TIMESTAMPTZ DEFAULT NOW()
    );

    CREATE TABLE ethical_walls (
        matter_a VARCHAR(64) NOT NULL REFERENCES matters(id),
        matter_b VARCHAR(64) NOT NULL REFERENCES matters(id),
        created_at TIMESTAMPTZ DEFAULT NOW(),
        PRIMARY KEY (matter_a, matter_b),
        CONSTRAINT check_no_self_wall CHECK (matter_a < matter_b)
    );

    CREATE TABLE documents (
        id VARCHAR(64) PRIMARY KEY,
        matter_id VARCHAR(64) NOT NULL REFERENCES matters(id),
        title VARCHAR(255) NOT NULL,
        content TEXT NOT NULL,
        embedding vector(1536), -- OpenAI text-embedding-3-small or GTE-large
        created_at TIMESTAMPTZ DEFAULT NOW()
    );

    CREATE INDEX idx_documents_matter_vector ON documents 
    USING hnsw (embedding vector_cosine_ops)
    WHERE matter_id IS NOT NULL;
    ```
  * **Filter-First Vector Query Execution:**
    ```sql
    -- Hard ACL boundary enforced at the SQL index level
    SELECT id, matter_id, title, content, 
           1 - (embedding <=> :queryEmbedding) AS cosine_similarity
    FROM documents
    WHERE matter_id IN (:allowedMatters)
    ORDER BY embedding <=> :queryEmbedding
    LIMIT 5;
    ```
  * **Benefits:** Strict ACL isolation guarantees no document outside the caller's authorized matters is ever examined by vector cosine similarity.

---

### 4.3. Enterprise DLP & Anonymization Engine

* **Current Architecture:** Fast regex-based redactor ([`Redactor.java`](file:///home/shankar/project/walls-gate/src/main/java/com/wallsgate/policy/Redactor.java)) for emails, phones, IBANs, and configured client strings.
* **Target Architecture:**
  * **Two-Tier DLP Strategy:**
    1. **Tier 1 (In-Process L1 Redaction):** Fast regex redactor for deterministic patterns (IBAN, SSN, credit cards, emails) running in < 0.2ms.
    2. **Tier 2 (Sidecar NER / Presidio L2 Redaction):** For complex unstructured clinical / legal text, call a local sidecar service (e.g. **Microsoft Presidio** or **spaCy Transformer NER**) over Unix domain sockets or gRPC.
  * **Reversible Pseudonymization (De-Anonymization Vault):**
    * Map sensitive entities to persistent surrogate keys during prompt ingress:
      `"Dr. John Watson"` -> `"[PERSON_1]"`
    * On LLM output egress, translate `"[PERSON_1]"` back to `"Dr. John Watson"` **only** for authorized callers.
    * External LLM providers or cloud inference endpoints only ever see surrogate tokens.

---

### 4.4. Streaming Inference (SSE) with Real-Time Ethical Chunk Buffering

* **Current Architecture:** Blocking HTTP completion response (`POST /v1/chat/completions`).
* **Target Architecture:**
  * Add **Server-Sent Events (SSE)** endpoint (`Accept: text/event-stream`).
  * **Challenge with Streaming & DLP:** Sensitive entities (e.g., telephone numbers or names) arrive across token chunk boundaries (Chunk 1: `"+49 170"`, Chunk 2: `" 1234567"`).
  * **Sliding Window Buffer Solution:**
    * Maintain an n-gram sliding window buffer (e.g., 32 tokens).
    * Emit tokens from the stream only after they pass through the trailing redaction window.
    * If an ethical wall violation occurs dynamically mid-generation, immediately send an SSE termination frame `event: error` with error code `ethical_wall_violation` and close the socket.

---

### 4.5. High Availability, Rate Limiting & Circuit Breaking

```
+-------------------------------------------------------------------------+
|                  External Ingress (AWS ALB / Cloudflare)                |
+------------------------------------+------------------------------------+
                                     |
                                     v
+-------------------------------------------------------------------------+
|                  Envoy Proxy / Spring Cloud Gateway                     |
|  - TLS Termination & WAF Inspection                                     |
|  - Token Bucket Rate Limiting per Tenant/User (Redis-backed)            |
+------------------------------------+------------------------------------+
                                     |
                                     v
+-------------------------------------------------------------------------+
|                    walls-gate Application Cluster                       |
|         [Pod 1]                [Pod 2]                [Pod 3]           |
|            |                      |                      |              |
|            +---------+------------+------------+---------+              |
|                      |                         |                        |
|                      v                         v                        |
|              PostgreSQL 16               Redis Cluster                  |
|             (pgvector + ACL)        (Wall State + Session Quota)        |
+------------------------------------+------------------------------------+
                                     |
                                     v
+-------------------------------------------------------------------------+
|                 Model Inference Tier (Auto-Scaling GPU Nodes)           |
|  - Primary: On-Premise vLLM / Ollama GPU Cluster                        |
|  - Resilience: Resilience4j CircuitBreaker with fallback to             |
|    isolated private cloud endpoint when GPU queue latency > 2.5s        |
+-------------------------------------------------------------------------+
```

1. **Circuit Breaker & Fallback:** Use `Resilience4j` around [`LlmClient`](file:///home/shankar/project/walls-gate/src/main/java/com/wallsgate/gateway/LlmClient.java):
   * When local vLLM queue latency exceeds 3000ms or error rate exceeds 20%, trip open the circuit.
   * Seamlessly route queries to a secondary dedicated warm GPU instance or encrypted cloud fallback.
2. **Virtual Threads (Java 25 Project Loom):**
   * Configure Tomcat with `spring.threads.virtual.enabled=true`.
   * Enables 10,000+ concurrent blocking downstream model calls with negligible JVM OS thread memory footprint.

---

### 4.6. Enterprise Observability & SLO Monitoring

* **SLO Targets:**
  * Gateway availability: **99.95%**
  * Gateway overhead latency (pipeline execution excluding model generation): **p95 < 25ms**, **p99 < 50ms**
  * Data leakage rate: **0.00%** (zero cross-matter access incidents)
* **Prometheus Metrics Exposed via Micrometer:**
  * `wallsgate.requests.total{status="allowed|blocked|shadow"}`
  * `wallsgate.wall_violations.total{matter_a="...", matter_b="..."}`
  * `wallsgate.token_budget.consumed_chars_total{session_id="..."}`
  * `wallsgate.pipeline.duration_seconds{stage="auth|retrieval|wall|redact|inference"}`
* **Distributed Tracing:**
  * OpenTelemetry trace propagation exporting spans to **Jaeger / AWS X-Ray / Grafana Tempo**.
  * Trace context links the consuming client SDK, gateway processing steps, and vector DB queries into a single timeline.

---

## 5. Summary Matrix: Current vs. Scaled Architecture

| Architectural Dimension | Current MVP Implementation | Enterprise Scale Target |
|---|---|---|
| **Language & Platform** | Pure Java 25 (GraalVM / OpenJDK) | Pure Java 25 with Virtual Threads enabled |
| **Persistence Layer** | Thread-Safe In-Memory `PolicyStore` | PostgreSQL 16+ with `pgvector` & HikariCP |
| **Ethical Wall State** | In-Memory `ConcurrentHashMap` | Distributed Redis 8.0 Cluster + Atomic Lua Scripts |
| **Retrieval Engine** | Filter-First Keyword Match | Hybrid HNSW Vector Search + BM25 with Pre-Filtering |
| **DLP & Sanitization** | Regex Pre/Post Redactor | Regex L1 + Local Presidio/NER L2 + Pseudonym Vault |
| **Streaming** | Standard HTTP Completion Body | Server-Sent Events (SSE) with Sliding Buffer |
| **API Contract** | OpenAI `/v1/chat/completions` + `/admin/**` | OpenAI `/v1` + Streaming SSE + gRPC internal SDK |
| **API Documentation** | Interactive Swagger UI (`/swagger-ui.html`) & OpenAPI 3.0 (`/v3/api-docs`) | Centralized API Portal, Redoc, & Client SDK Generation |
| **Test Verification** | 58 Automated JUnit & Integration Tests | 58 Unit/Integration Tests + Chaos Mesh + Locust Load Tests |

# System Architecture, Tradeoffs & Scalability Guide

**System:** `walls-gate`  
**Purpose:** High-compliance AI Gateway, Agent Runtime & Client SDK for regulated verticals  
**Scale Target:** 2,000+ Facilities, 200,000+ Frontline Users, 500+ Peak QPS

---

## 1. High-Level Architecture & Component Design

`walls-gate` is engineered as a defense-in-depth platform layer sitting between consuming AI agent applications (feature services) and model inference backends (vLLM, Ollama, OpenAI-compatible local servers).

```
+----------------------------------------------------------------------------------------+
|                              Consuming Feature Applications                            |
|                  (Care Shift Manager, Voice Assistant, Clinical Notes)                 |
+-------------------------------------------+--------------------------------------------+
                                            |
                                  WallsGateClient (SDK)
                          AgentContext(userId, sessionId, traceId)
                                            |
                                            v
+----------------------------------------------------------------------------------------+
|                                 walls-gate Core Gateway                                |
|                                                                                        |
|   +--------------------------------------------------------------------------------+   |
|   | 1. Ingress & Observability Pipeline                                            |   |
|   |    - GatewayController: Validates headers, binds user identity                 |   |
|   |    - SLF4J MDC: Sets userId, sessionId, traceId for distributed tracing       |   |
|   |    - TokenBudgetGuard: Validates prompt length and aggregate session budget    |   |
|   +---------------------------------------+----------------------------------------+   |
|                                           |                                            |
|   +---------------------------------------v----------------------------------------+   |
|   | 2. Compliance & Retrieval Guardrails                                           |   |
|   |    - Redactor: Pre-flight DLP redacts emails, phones, IBANs, patient/client PII |   |
|   |    - Retriever: Filter-first document search strictly within user's ACL        |   |
|   |    - WallEngine: Evaluates session conflict graph across multi-turn matters    |   |
|   |      -> If conflict detected: Abort with HTTP 403 (or flag in shadow-mode)     |   |
|   +---------------------------------------+----------------------------------------+   |
|                                           | Context Docs + Redacted Prompt             |
|   +---------------------------------------v----------------------------------------+   |
|   | 3. Inference Adapter                                                           |   |
|   |    - LlmClient: Dispatches request to local model inference (vLLM / Ollama)   |   |
|   |    - Post-processing: Output DLP redaction + TokenBudgetGuard output truncation|   |
|   +---------------------------------------+----------------------------------------+   |
|                                           |                                            |
|   +---------------------------------------v----------------------------------------+   |
|   | 4. Audit & Telemetry Engine                                                    |   |
|   |    - AuditService: Records action (ALLOWED/BLOCKED), decision reason, docs    |   |
|   |    - Formula Injection Defense: Escapes `=+-@` in CSV exports                  |   |
|   |    - Append-only JSONL stream for downstream SIEM ingestion                   |   |
|   +--------------------------------------------------------------------------------+   |
+-------------------------------------------+--------------------------------------------+
                                            |
                                            v
+----------------------------------------------------------------------------------------+
|                        Local Model Infrastructure (vLLM / GPU Pods)                    |
+----------------------------------------------------------------------------------------+
```

---

## 2. Request Lifecycle & Detailed Sequence Flow

```
Agent Client          GatewayController     TokenBudgetGuard      Retriever         WallEngine        Redactor          LlmClient         AuditService
     |                       |                     |                  |                  |               |                  |                 |
     |-- POST /completions ->|                     |                  |                  |               |                  |                 |
     |   (X-User-Id, Trace)  |                     |                  |                  |               |                  |                 |
     |                       |-- Check Budget ---->|                  |                  |               |                  |                 |
     |                       |<-- Budget OK -------|                  |                  |               |                  |                 |
     |                       |                                        |                  |               |                  |                 |
     |                       |-- Filter-First Document Search ------->|                  |               |                  |                 |
     |                       |   (Query, AllowedMatters)              |                  |               |                  |                 |
     |                       |<-- Filtered Documents -----------------|                  |               |                  |                 |
     |                       |                                                           |               |                  |                 |
     |                       |-- Evaluate Multi-Turn Conflict -------------------------->|               |                  |                 |
     |                       |   (SessionId, CandidateMatters)                           |               |                  |                 |
     |                       |<-- Decision: ALLOWED or BLOCKED --------------------------|               |                  |                 |
     |                       |                                                                           |                  |                 |
     |                       |-- Redact Prompt & Context Documents ------------------------------------->|                  |                 |
     |                       |<-- Sanitized Text --------------------------------------------------------|                  |                 |
     |                       |                                                                                              |                 |
     |                       |-- Forward Prompt & Redacted Context to Model ----------------------------------------------->|                 |
     |                       |<-- Model Response ---------------------------------------------------------------------------|                 |
     |                       |                                                                           |                                    |
     |                       |-- Redact Output Text ---------------------------------------------------->|                                    |
     |                       |<-- Sanitized Answer ------------------------------------------------------|                                    |
     |                       |                                                                                                                |
     |                       |-- Commit Session Matters & Record Audit Event ---------------------------------------------------------------->|
     |<-- 200 OK (Answer) ---|                                                                                                                |
```

---

## 3. Key Architectural Tradeoffs

Every engineering design involves intentional trade-offs. Here is why specific decisions were made and when they should evolve:

| Decision | Chosen Approach | Alternative Considered | Why Chosen & When to Switch |
|---|---|---|---|
| **Data Persistence** | In-Memory `ConcurrentHashMap` with thread-safe sets | External PostgreSQL / Redis | **MVP:** Zero-dependency startup, instant testing, sub-millisecond lookups. **Production:** Switch to PostgreSQL when multi-instance clustering and durable policy state are required. |
| **DLP / Anonymization** | Compiled regex patterns (`java.util.regex.Pattern`) | Microsoft Presidio / Local Spacy NER | **Chosen:** Executes in <0.5ms with zero external infrastructure. **Switch when:** Multilingual entity recognition or unstructured context (e.g., patient clinical notes) requires transformer-based NER. |
| **Architectural Topology** | Reverse-Proxy Gateway (`POST /v1/chat/completions`) + Client SDK | Embedded in-process SDK only | **Chosen:** Dual approach: HTTP proxy allows language-agnostic integration; SDK provides type safety. A centralized gateway enforces policies across heterogeneous feature services. |
| **Guardrail Engine** | Deterministic boolean rule engine | LLM-as-a-judge (second model pass) | **Chosen:** Deterministic, reproducible, auditable, and takes <1ms. LLM-as-a-judge adds 300-800ms latency, high inference costs, and is prone to hallucinated policy decisions. |
| **Concurrency Model** | Synchronous + Non-blocking `CompletableFuture` (Java 25) | Kotlin Coroutines / WebFlux | **Chosen:** Compatible with standard Java JVM backends while offering seamless async integration with virtual threads. Kotlin services consume it effortlessly via `await()`. |

---

## 4. Database Selection & Relational Schema Design

For production deployment at enterprise scale, the architecture transitions to **PostgreSQL with the `pgvector` extension**.

### Why PostgreSQL + `pgvector` Wins Over NoSQL & Dedicated Vector DBs

1. **ACID Transactions on Policy & Permissions:** Matter access, ethical barriers, and ACLs require strict relational consistency. If an ethical wall is created, no concurrent request must sneak past it due to eventual consistency.
2. **Unified Relational & Vector Queries (Filter-First):** In `walls-gate`, retrieval must be **filter-first** (filter by user ACL *before* computing vector similarity). In standalone vector DBs (e.g. Pinecone, Milvus), metadata filtering is secondary and suffers from index degradation. In `pgvector`, SQL enables composite filtering and HNSW vector search in a single query:
   ```sql
   SELECT id, matter_id, title, text, 1 - (embedding <=> :query_vector) AS similarity
   FROM documents
   WHERE matter_id = ANY(:user_allowed_matters)
   ORDER BY embedding <=> :query_vector
   LIMIT 3;
   ```
3. **Enterprise Compliance & Tooling:** Postgres provides mature point-in-time recovery (PITR), Read Replicas, connection poolers (PgBouncer), and GDPR compliance tooling (Row-Level Security, data encryption at rest via AWS RDS KMS).

### Production PostgreSQL Schema

```sql
-- 1. Legal / Enterprise Matters
CREATE TABLE matters (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. Information Barriers (Ethical Walls)
CREATE TABLE walls (
    matter_a VARCHAR(64) NOT NULL REFERENCES matters(id) ON DELETE CASCADE,
    matter_b VARCHAR(64) NOT NULL REFERENCES matters(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_walls PRIMARY KEY (matter_a, matter_b),
    CONSTRAINT chk_matter_order CHECK (matter_a < matter_b)
);

-- 3. Users and Matter Authorizations (ACL)
CREATE TABLE users (
    id VARCHAR(64) PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_matters (
    user_id VARCHAR(64) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    matter_id VARCHAR(64) NOT NULL REFERENCES matters(id) ON DELETE CASCADE,
    granted_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, matter_id)
);

-- 4. Knowledge Base Documents with pgvector
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE documents (
    id VARCHAR(64) PRIMARY KEY,
    matter_id VARCHAR(64) NOT NULL REFERENCES matters(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    embedding vector(1536), -- Dimensions matching embedding model (e.g. OpenAI text-embedding-3 or local BGE)
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- HNSW Index for ultra-fast approximate nearest neighbor search
CREATE INDEX idx_documents_embedding_hnsw ON documents 
USING hnsw (embedding vector_cosine_ops) 
WITH (m = 16, ef_construction = 64);

CREATE INDEX idx_documents_matter_id ON documents (matter_id);

-- 5. Multi-Turn Session State Ledger
CREATE TABLE session_history (
    session_id VARCHAR(128) NOT NULL,
    matter_id VARCHAR(64) NOT NULL REFERENCES matters(id),
    first_touched_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (session_id, matter_id)
);
CREATE INDEX idx_session_history_session ON session_history (session_id);

-- 6. Tamper-Evident Append-Only Audit Trail
CREATE TABLE audit_events (
    id BIGSERIAL PRIMARY KEY,
    event_timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(128) NOT NULL,
    action VARCHAR(32) NOT NULL, -- ALLOWED, BLOCKED, WOULD_BLOCK
    reason TEXT,
    matters_accessed TEXT[] NOT NULL,
    documents_used TEXT[] NOT NULL,
    prompt_redacted TEXT NOT NULL,
    previous_hash CHAR(64),     -- Cryptographic SHA-256 chain for tamper-evidence
    event_hash CHAR(64) NOT NULL
);
CREATE INDEX idx_audit_user_timestamp ON audit_events (user_id, event_timestamp DESC);
CREATE INDEX idx_audit_session ON audit_events (session_id);
```

### Audit Immutability & Tamper-Evidence
For compliance in care and legal environments:
- **Hash-Chaining:** Each audit row computes `SHA-256(previous_hash + timestamp + user + action + matters + prompt)`. Any alteration breaks the cryptographic verification chain.
- **S3 Object Lock (Write-Once-Read-Many):** A scheduled background worker batches audit rows hourly and streams them to an AWS S3 bucket with **Object Lock in Compliance Mode** (legal hold that even root account administrators cannot delete or overwrite).

---

## 5. Infrastructure, Containerization & Deployment Topology

### AWS Production Cloud Topology

```
                                  [ Internet / Clients ]
                                             |
                                             v
                                     [ AWS Route 53 ]
                                             |
                                             v
                                      [ AWS WAF ]
                         (Rate limiting, Geo-blocking, DDoS)
                                             |
                                             v
                       [ Application Load Balancer (ALB) ]
                               (TLS 1.3 Termination)
                                             |
                      +----------------------+----------------------+
                      v                                             v
            [ Private Subnet AZ-1 ]                       [ Private Subnet AZ-2 ]
       +------------------------------+              +------------------------------+
       |   AWS EKS / ECS Fargate      |              |   AWS EKS / ECS Fargate      |
       |  +------------------------+  |              |  +------------------------+  |
       |  | walls-gate Pod 1       |  |              |  | walls-gate Pod 2       |  |
       |  +-----------+------------+  |              |  +-----------+------------+  |
       +--------------+---------------+              +--------------+---------------+
                      |                                             |
                      +----------------------+----------------------+
                      v                      v                      v
             [ Redis Cluster ]      [ Aurora PostgreSQL ]     [ GPU Inference Node Pool ]
             - Distributed Cache    - Master (AZ-1)           - EC2 g5.xlarge / AWS Inferentia
             - Token bucket limits  - Read Replica (AZ-2)     - vLLM Engine (Qwen / Llama 3)
```

### Docker Multi-Stage Containerization

The production `Dockerfile` uses a multi-stage build running on a hardened distroless non-root image:

```dockerfile
# Stage 1: Build
FROM maven:3.9.7-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Hardened Runtime
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
COPY --from=builder /app/target/walls-gate-*.jar app.jar
USER appuser
EXPOSE 8080
ENV JAVA_OPTS="-XX:+UseZGC -XX:+ZGenerational -XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

---

## 6. Sizing, Scale & Load (2,000+ Facilities, 200,000+ Frontline Users)

### Mathematical Capacity Model

| Parameter | Calculation | Sizing Value |
|---|---|---|
| **Total Registered Users** | Enterprise user base | 200,000 frontline care workers |
| **Peak Concurrency (Shift Handover)** | 15% active during morning shift changes (07:00-08:30) | **30,000 concurrent active users** |
| **Request Cadence per Active User** | 1 query / care note completion every 2 minutes | 0.0083 requests/sec per user |
| **Average Traffic Load** | 30,000 * 0.0083 | **250 QPS** |
| **Peak Spike Traffic (Safety margin 2x)** | Burst traffic factor | **500 QPS** |
| **Gateway Processing Overhead** | Retrieval + DLP + Wall Engine + MDC logging | **< 15 ms (P99)** |
| **Model Inference Latency** | Local LLM inference (e.g. 7B/14B model) | 300 ms - 1,200 ms |

### Compute Sizing & Horizontal Autoscaling (KEDA)
- **Gateway CPU/Memory Footprint:** Each Java 25 instance on Generational ZGC handles ~150 QPS at ~40ms thread latency.
- **Instance Count:**
  - Base steady state: 4 pods (2 per Availability Zone) = 600 QPS capacity.
  - Peak burst capacity: Auto-scales up to 10 pods via Kubernetes HPA (triggered when pod CPU > 65% or request queue > 200).
- **GPU Inference Pool:**
  - 500 QPS with average 250ms completion requires ~125 concurrent model slots.
  - Using vLLM with PagedAttention and continuous batching on 8x NVIDIA A10G (g5.12xlarge instances) provides throughput of ~150 concurrent streams.

### Resilience & Graceful Degradation Patterns
1. **Per-Facility Token Bucket Rate Limiting:** Enforced via Redis to ensure one busy hospital or nursing home facility cannot starve other facilities during unexpected surges.
2. **Circuit Breaking (Resilience4j):** If local LLM backend error rate exceeds 5% over 10 seconds, the circuit breaker opens, directing traffic to a fallback smaller model or returning a friendly structured degradation message.
3. **Database Connection Pooling (HikariCP):**
   ```properties
   spring.datasource.hikari.maximum-pool-size=30
   spring.datasource.hikari.minimum-idle=10
   spring.datasource.hikari.idle-timeout=30000
   spring.datasource.hikari.connection-timeout=5000
   spring.datasource.hikari.max-lifetime=1800000
   ```

---

## 7. Future Optimizations Roadmap

1. **Semantic Caching Layer:**
   - Store prompt embeddings in Redis. If a query has cosine similarity > 0.96 with a recent cached answer within the same matter scope, return the cached result. Reduces GPU inference costs by 20-35%.
2. **Streaming Token DLP Redaction (Windowed Token Buffer):**
   - When streaming LLM responses via Server-Sent Events (SSE), maintain a sliding 5-token lookahead buffer to redact PII (e.g. phone numbers split across multiple tokens) before emitting chunks to the client.
3. **Presidio Microservice Sidecar:**
   - Run Microsoft Presidio with multilingual spaCy models on a local Unix domain socket sidecar for deep NER detection (names, medical IDs, German medical jargon) with <3ms latency overhead.
4. **Automated DMS & EHR ACL Sync Workers:**
   - Background polling workers connecting to document management systems (SharePoint, iManage) and EHR platforms (Cerner, Epic) to synchronize user permission updates in real time.

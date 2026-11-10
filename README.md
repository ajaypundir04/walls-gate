# walls-gate

[![Java](https://img.shields.io/badge/Java-25%20LTS-orange.png)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.11-brightgreen.png)](https://spring.io/projects/spring-boot)
[![Build Status](https://img.shields.io/badge/Build-Passing%20(56%20tests)-success.png)]()
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.png)](LICENSE)

> **Ethical-Wall-Aware Gateway & Agent SDK Runtime for Local LLMs.**  
> An enterprise AI gateway and client SDK designed for high-compliance environments (healthcare, legal, tax, consulting) that enforces information barriers, permission-aware retrieval, DLP/anonymization, and token governance *before* queries reach model inference.

---

##  The Core Problem

When deploying AI agents in enterprise and healthcare settings, consumer-facing LLM APIs lack the compliance controls required for confidential operations:

1. **Identity & Authorization Drift:** Agents frequently interact with LLMs using static service keys, stripping user identity and bypassing granular data access controls.
2. **Confidentiality & Ethical Wall Collisions:** In multi-tenant or multi-client operations, a user or agent touching two conflicted accounts or matters (e.g. litigation sides or competing care facilities) can inadvertently cause cross-contamination.
3. **PII/PHI Data Leakage:** Unredacted personal identifiable information (emails, phone numbers, IBANs, patient/client names) reaches external or shared model inference.
4. **Context Window Exhaustion:** Unchecked agent loops and bloated tool outputs exhaust token budgets and crash context windows.

`walls-gate` solves this by acting as a **hardened runtime layer and client SDK** sitting between agent feature code and model inference backends (vLLM, Ollama, llama.cpp).

---

##  Architecture & Component Flow

```
                      +--------------------------------------------------------+
                      |              Consuming Agent Applications              |
                      +---------------------------+----------------------------+
                                                  |
                                     WallsGateClient (Java SDK)
                                      AgentContext(user, trace)
                                                  |
                                                  v
+-----------------------------------------------------------------------------------------------+
|                                    walls-gate Gateway Runtime                                 |
+---------------------------+----------------------------+--------------------------------------+
|  1. Identity & Context    |  2. Token Budget Guard     |  3. Permission-Aware Retrieval       |
|  MDC distributed trace &  |  Per-call & session limits |  Filters documents by user ACL       |
|  user identity extraction |  truncates runaway outputs |  BEFORE scoring & model injection    |
+---------------------------+----------------------------+--------------------------------------+
|  4. Ethical Wall Engine   |  5. DLP & Anonymization    |  6. Tamper-Evident Audit Trail       |
|  Stateful conflict checks |  Regex/NER PII redactor    |  CSV / JSONL event stream with       |
|  detects cross-matter risk|  (email, phone, IBAN, PII) |  formula injection neutralization    |
+---------------------------+----------------------------+--------------------------------------+
                                                  |
                                                  v
                      +--------------------------------------------------------+
                      |      Model Inference Backend (vLLM / Ollama / OpenAI)  |
                      +--------------------------------------------------------+
```

---

##  Key Capabilities

| Capability | Module / Class | Description |
|---|---|---|
| **Client-Facing Agent SDK** | [`com.wallsgate.sdk.WallsGateClient`](file:///home/shankar/project/walls-gate/src/main/java/com/wallsgate/sdk/WallsGateClient.java) | Fluent SDK client offering both synchronous and async non-blocking (`CompletableFuture`) calls with typed domain exceptions. |
| **Agent Identity Layer** | [`com.wallsgate.sdk.AgentContext`](file:///home/shankar/project/walls-gate/src/main/java/com/wallsgate/sdk/AgentContext.java) | Binds every request to verified user identity, session ID, and authorized matter scopes. |
| **Shared Governance & Limits** | [`com.wallsgate.policy.TokenBudgetGuard`](file:///home/shankar/project/walls-gate/src/main/java/com/wallsgate/policy/TokenBudgetGuard.java) | Enforces max prompt characters, session aggregate budgets, and tool output limits to prevent context blowouts. |
| **Filter-First Retrieval** | [`com.wallsgate.policy.Retriever`](file:///home/shankar/project/walls-gate/src/main/java/com/wallsgate/policy/Retriever.java) | Strict ACL filtering applied *before* relevance ranking; unauthorized documents never enter model context. |
| **Multi-Turn Wall Engine** | [`com.wallsgate.policy.WallEngine`](file:///home/shankar/project/walls-gate/src/main/java/com/wallsgate/policy/WallEngine.java) | Evaluates cumulative matters touched within a session; blocks requests that breach an ethical wall with HTTP 403. |
| **DLP & Anonymization** | [`com.wallsgate.policy.Redactor`](file:///home/shankar/project/walls-gate/src/main/java/com/wallsgate/policy/Redactor.java) | Strips emails, phones, IBANs, and client entities from prompt inputs, context docs, and model outputs. |
| **Automated ML Eval Harness** | [`com.wallsgate.eval.AgentEvalHarness`](file:///home/shankar/project/walls-gate/src/main/java/com/wallsgate/eval/AgentEvalHarness.java) | Test runner evaluating safety policies, wall enforcement, and PII leakage before deploying model updates. |
| **Telemetry & Observability** | [`GatewayController`](file:///home/shankar/project/walls-gate/src/main/java/com/wallsgate/gateway/GatewayController.java) | SLF4J MDC distributed tracing (`userId`, `sessionId`, `traceId`) across the full request lifecycle. |
| **Audit Stream & CSV Export** | [`com.wallsgate.gateway.AuditService`](file:///home/shankar/project/walls-gate/src/main/java/com/wallsgate/gateway/AuditService.java) | Immutable audit trail with CSV export hardened against spreadsheet formula injection (`=`, `+`, `-`, `@`). |

---

## 🏛️ Domain-Driven Design (DDD) & SOLID Clean Architecture

The codebase strictly decouples HTTP presentation adapters, application orchestration services, domain policy invariants, and data persistence:

```
[Presentation Layer]
  ├── GatewayController       (OpenAI /v1/chat/completions HTTP transport & MDC tracing)
  ├── AdminController         (Administrative REST API & timing-safe token authentication)
  └── GlobalExceptionHandler  (@RestControllerAdvice translating DomainExceptions to OpenAI errors)
         │
         ▼ (Application DTOs: ChatCommand, ChatResult)
[Application Service Layer]
  ├── GatewayService          (Pipeline: Auth -> Budget -> Filter -> Wall -> Redact -> LLM -> Audit)
  └── AdminService            (Administrative policies, invariants, and CSV export orchestration)
         │
         ▼ (Domain Invariants & Interfaces)
[Domain Layer]
  ├── PolicyRepository        (DIP: Dependency Inversion interface decoupling persistence)
  ├── Domain Exceptions       (EthicalWallViolationException, TokenBudgetExceededException, etc.)
  └── Models                  (Immutable Records: User, Matter, Wall, Doc)
         │
         ▼ (Implementations & Policy Engines)
[Infrastructure & Policy Adapters]
  ├── PolicyStore             (Thread-safe in-memory ConcurrentMap repository implementation)
  ├── Retriever               (ACL-filtered knowledge base search)
  ├── WallEngine              (Stateful graph conflict evaluation)
  ├── TokenBudgetGuard        (Token limits and context window protection)
  ├── Redactor                (DLP / PII anonymization)
  └── LlmClient               (OpenAI-compatible HTTP model connector with offline mock)
```

### Applied SOLID Principles:
- **Single Responsibility Principle (SRP):** Controllers deal exclusively with HTTP routing, serialization, and status codes. All business rules (wall conflict evaluation, redaction, budget decrementing) live in `GatewayService` and `AdminService`.
- **Open/Closed Principle (OCP):** New LLM backends or persistence stores can be added by implementing interfaces without changing core domain services.
- **Liskov Substitution Principle (LSP):** `PolicyStore` satisfies `PolicyRepository` contract, allowing swap with PostgreSQL/pgvector implementations.
- **Interface Segregation Principle (ISP):** Domain repositories provide focused, decoupled query and mutation contracts.
- **Dependency Inversion Principle (DIP):** Application services depend on the `PolicyRepository` abstraction rather than concrete storage details.

---

## 🚀 Quickstart & Demo

Requires **Java 25** and **Maven 3.9+**.

### 1. Build and Run Tests
```bash
mvn clean verify
```
*Executes all 56 tests covering concurrent wall checks, budget guards, client SDK behavior, application service orchestration, and ML eval harness.*

### 2. Start the Gateway
```bash
mvn spring-boot:run
```

### 3. Interactive Show-and-Tell Walkthrough

Pre-seeded demo personas:
- **Alice**: Works on `M-1001` (Acme litigation) and `M-3003` (Gamma advisory).
- **Bob**: Works on `M-2002` (Beta acquisition, walled off from Acme).
- **Carol**: Assigned to both `M-1001` and `M-2002` (competing sides of a walled matter).

```bash
# Query: "Summarise Acme Beta settlement strategy"
Q='{"messages":[{"role":"user","content":"Summarise Acme Beta settlement strategy"}]}'

# 1. Alice (Allowed) -> Receives answer from her matter; PII (emails/IBANs) automatically redacted
curl -s localhost:8080/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -H 'X-User-Id: alice' \
  -d "$Q"

# 2. Bob (Filtered) -> Only sees documents for M-2002; Acme confidential documents never retrieved
curl -s localhost:8080/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -H 'X-User-Id: bob' \
  -d "$Q"

# 3. Carol (Blocked) -> Touching conflicted matters triggers Ethical Wall; blocked with HTTP 403
curl -s -w "\nHTTP Status: %{http_code}\n" localhost:8080/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -H 'X-User-Id: carol' \
  -d "$Q"

# 4. Audit Log Export -> Secure CSV download showing decisions and formula injection protection
curl -s localhost:8080/admin/audit.csv -H 'X-Admin-Token: change-me'
```

---

##  Consuming the Agent SDK

Consuming feature teams can embed the client contract directly into their services:

```java
// 1. Initialize client
WallsGateClient client = WallsGateClient.builder()
        .baseUrl("http://gateway.internal:8080")
        .timeout(Duration.ofSeconds(15))
        .build();

// 2. Build agent identity context
AgentContext context = AgentContext.builder()
        .userId("nurse-sarah")
        .sessionId("shift-morning-20261110")
        .traceId("trace-care-workflow-8831")
        .build();

// 3. Execute non-blocking prompt
client.completeAsync(context, "Summarize patient medication schedule")
        .thenAccept(summary -> log.info("Generated summary: {}", summary))
        .exceptionally(ex -> {
            if (ex.getCause() instanceof WallViolationException wve) {
                log.error("Compliance barrier triggered: {}", wve.getReason());
            } else if (ex.getCause() instanceof BudgetExceededException bee) {
                log.warn("Token budget exceeded: {}", bee.getMessage());
            }
            return null;
        });
```

---

##  Project Structure

```
src/main/java/com/wallsgate/
  +-- sdk/                    # Client-facing SDK contract
  |   +-- WallsGateClient.java
  |   +-- AgentContext.java
  |   +-- *Exception.java     # Typed domain exceptions (WallViolation, BudgetExceeded, etc.)
  +-- eval/                   # Automated ML Evaluation Harness
  |   +-- AgentEvalHarness.java
  +-- gateway/                # HTTP API & Orchestration
  |   +-- GatewayController.java
  |   +-- LlmClient.java
  |   +-- AuditService.java
  +-- policy/                 # Core Guardrail & Compliance Engines
  |   +-- PolicyStore.java
  |   +-- WallEngine.java     # Multi-turn ethical wall evaluator
  |   +-- Redactor.java       # DLP & PII regex/anonymizer
  |   +-- Retriever.java      # Filter-first document retrieval
  |   +-- TokenBudgetGuard.java # Token & tool output limiter
  +-- admin/                  # Administrative endpoints & CSV audit export
  |   +-- AdminController.java
  |   +-- HealthController.java
  +-- model/
      +-- Models.java         # Immutable domain models (Matter, Wall, Doc, User)
```

---

##  Deep-Dive Documentation

- [**Master Interview Encyclopedia & Curriculum (interview_prep/)**](file:///home/shankar/project/walls-gate/interview_prep/README.md): Exhaustive printable multi-volume curriculum covering System Design, SRE, Modern JVM/Java 25, Spring Boot 3, Hibernate internals, Advanced SQL queries, NoSQL, Resiliency, Kotlin Coroutines, and TypeScript.
- [**System Architecture, Tradeoffs & Scaling Guide (ARCHITECTURE.md)**](file:///home/shankar/project/walls-gate/ARCHITECTURE.md): Deep-dive into database selection, pgvector indexing, infrastructure topology on AWS/Kubernetes, and scaling to 200,000+ frontline users.
- [**voize Interview Playbook & Cheatsheet (VOIZE_INTERVIEW_PREP_GUIDE.md)**](file:///home/shankar/project/walls-gate/VOIZE_INTERVIEW_PREP_GUIDE.md): 45-minute show-and-tell talk track, coroutines vs Loom vs OS threads analysis, and strategic interview questions.
- [**100+ Master Interview Questions & Answers (INTERVIEW_QUESTIONS_100.md)**](file:///home/shankar/project/walls-gate/INTERVIEW_QUESTIONS_100.md): 110 comprehensive questions covering Kotlin Coroutines, Java 25, System Design, AI Agent Architecture, and voize scenarios.

---

##  License

Apache License 2.0. Built for modern high-assurance agent architectures.

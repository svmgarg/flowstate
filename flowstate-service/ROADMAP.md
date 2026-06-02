# FlowState — Deferred & Future Roadmap

> Principal Architect review — 2026-05-31  
> These are consciously deferred, not bugs. Prioritized by growth stage.

---

## P1 — Do Before Scaling (>10 consumers / public launch)

| # | Item | Effort | Trigger | Implementation Hint |
|---|------|--------|---------|---------------------|
| 1 | **Rate limiting** (per API key) | Medium | >10 API keys active | Redis sliding window (`ZRANGEBYSCORE`) or Bucket4j + Redis |
| 2 | **API key rotation support** | Medium | First key compromise or compliance audit | Add `POST /admin/keys/rotate` endpoint, grace period for old key |
| 3 | **Health check deep probe** | Low | Production SLA commitment | `/actuator/health` should ping Redis (custom `HealthIndicator`) |

## P2 — Do Before Multi-Tenant / Billing

| # | Item | Effort | Trigger | Implementation Hint |
|---|------|--------|---------|---------------------|
| 4 | **workspaceId ≠ apiKey** (decouple identity from key) | Medium | Multi-tenant billing or key rotation | SHA-256 prefix of key as workspaceId, or explicit mapping table |
| 5 | **API key format** (prefix + checksum, 32+ chars) | Low | External user onboarding | Format: `fs_live_<24 random chars>` with Luhn check digit |
| 6 | **Per-workspace usage metering** | Medium | Billing/quotas | Track store/recall counts per workspace in Redis sorted set |
| 7 | **Namespace isolation / RBAC** | High | Enterprise customers | Key → workspace → allowed namespaces mapping |

## P3 — Operational Excellence

| # | Item | Effort | Trigger | Implementation Hint |
|---|------|--------|---------|---------------------|
| 8 | **Structured logging (JSON)** | Low | Log aggregator integration | `logback-logstash-encoder` + logback-spring.xml |
| 9 | **Distributed tracing** | Low | Debugging cross-service calls | Micrometer + Azure Monitor / Zipkin |
| 10 | **Request idempotency on store** | Low | Aggressive consumer retries | `If-None-Match` / `X-Idempotency-Key` header |
| 11 | **Graceful degradation (circuit breaker)** | Medium | Redis outages causing cascading failures | Resilience4j CircuitBreaker on RedisMemoryService |
| 12 | **Redis connection pooling tuning** | Low | >100 req/s sustained | Lettuce pool config in application.yml |

## P4 — Future Features (Product Roadmap)

| # | Item | Effort | Value |
|---|------|--------|-------|
| 13 | **TTL extension API** (`PATCH /memory/extend`) | Low | Zapier workflows that need more time |
| 14 | **Bulk operations** (`POST /memory/batch`) | Medium | Workflows storing 5-10 keys per trigger |
| 15 | **Webhooks on expiry** (notify when key TTL expires) | High | Event-driven automation triggers |
| 16 | **Read-after-write consistency guarantee** | Medium | Critical path workflows |
| 17 | **Admin dashboard** (key usage, TTL heatmap, error rates) | High | Self-service visibility for consumers |
| 18 | **Multi-region Redis** (active-active) | High | Global latency <100ms |
| 19 | **Encryption at rest** (field-level value encryption) | Medium | PII/compliance requirements |
| 20 | **OpenAPI spec + SDK generation** | Low | Developer experience for non-Zapier consumers |

---

## Architecture Principles (for future contributors)

1. **Always return HTTP 200** — Zapier treats non-2xx as failure. Use `success: true/false` in body.
2. **Fail gracefully** — Redis down → `success:false`, never raw stack traces.
3. **Stateless service** — All state lives in Redis. Horizontal scaling = add instances.
4. **Keep TTLs finite** — Default 86400s. No unbounded storage growth.
5. **API key = workspace boundary** — One key = one isolated namespace tree.

---

_Last updated: 2026-05-31_

# FlowState — Deferred Items (Post-MVP)

> Reviewed by Principal Architect on 2026-05-31. These are consciously deferred — not bugs.

## P1 — Do Before Scaling

| # | Item | Effort | When to Address |
|---|------|--------|-----------------|
| 1 | **Rate limiting** (per API key, Redis sliding window) | Medium | Before public launch or >10 consumers |

## P2 — Nice to Have

| # | Item | Effort | When to Address |
|---|------|--------|-----------------|
| 2 | **workspaceId = apiKey** leaks key in Redis key pattern/logs | Medium | When adding multi-tenant billing or key rotation |
| 3 | **API key format too weak** (8 chars, no prefix/checksum) | Low | When onboarding external users |
| 4 | **No request idempotency on store** (duplicate writes possible) | Low | If consumers retry aggressively |

## P3 — Not Needed for MVP

| # | Item | Effort | When to Address |
|---|------|--------|-----------------|
| 5 | **Structured logging (JSON)** | Low (1 dependency + config) | When integrating log aggregator (Datadog/ELK/Azure Monitor) |

---

_Last updated: 2026-05-31_

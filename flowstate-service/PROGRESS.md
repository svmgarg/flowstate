# FlowState — Project Progress Tracker

> Workflow Memory API for automation tools (Zapier, n8n, Make)
> Last updated: 2026-05-31

---

## ✅ Completed

| # | Task | Date |
|---|------|------|
| 1 | Core API — store/recall/forget/health endpoints | Done |
| 2 | Redis Cloud integration (plough-pampas-industry-12379.db.redis.io:15199) | 2026-05-31 |
| 3 | Removed Lua script — plain redisTemplate calls | 2026-05-31 |
| 4 | Removed InMemory storage — Redis-only | 2026-05-31 |
| 5 | Removed legacy idempotency code (9 files deleted) | 2026-05-31 |
| 6 | Validation rules (key 1-32 chars, value max 1KB, TTL max 86400) | 2026-05-31 |
| 7 | API key auth filter ([A-Z0-9]{8} format) | Done |
| 8 | MemoryResponse cleanup (removed dead fields) | 2026-05-31 |
| 9 | New FlowState dashboard created (flowstate.html + flowstate.css) | 2026-05-31 |
| 10 | application.yml — Redis Cloud credentials, SSL, connection pool | 2026-05-31 |

---

## 🔲 Pending

### High Priority (Launch Blockers)
| # | Task | Notes |
|---|------|-------|
| 1 | Disable SSL for local dev / add profile | Keystore blocks `mvn spring-boot:run` locally |
| 2 | Test Redis connection end-to-end | Run app, hit /memory/health, store/recall a key |
| 3 | Deploy to Oracle Cloud | VM: 144.24.119.46, ARM, Always Free |
| 4 | API key provisioning | How users get keys — manual seed? admin endpoint? |

### Medium Priority (Post-Launch)
| # | Task | Notes |
|---|------|-------|
| 5 | Rate limiting | Per-key, per-minute throttle |
| 6 | HTTPS + domain | Nginx reverse proxy + Let's Encrypt on Oracle VM |
| 7 | Usage metrics / analytics | Track calls per workspace |
| 8 | Monetization tiers | Free (100 keys) → Pro ($5/mo) → Team ($15/mo) |

### Low Priority (Polish)
| # | Task | Notes |
|---|------|-------|
| 9 | Rename parent folder `idempotent` → `flowstate` | Access denied — close IDE first |
| 10 | Fix stale pom.xml comments | Still references "idempotency" |
| 11 | Review new dashboard output | Agent finished, not yet inspected |
| 12 | README.md rewrite | API docs, setup guide, examples |
| 13 | Integration tests | Redis testcontainers or embedded redis |

---

## 🏗 Architecture

```
Client (Zapier/n8n) → HTTPS → FlowState API (Spring Boot 3.2)
                                    ↓
                              Redis Cloud (Free Tier)
                              plough-pampas-industry-12379.db.redis.io:15199
```

**Key format:** `{apiKey}:{namespace}:{key}`
**Auth:** API key in `X-API-Key` header, format `[A-Z0-9]{8}`
**All responses:** HTTP 200 (success/failure in body — Zapier constraint)

---

## 🖥 Infrastructure

| Resource | Details |
|----------|---------|
| Redis | Redis Cloud Free — 30MB, SSL, single shard |
| Compute | Oracle Cloud Always Free — 4 ARM cores, 24GB RAM |
| IP | 144.24.119.46 |
| Domain | TBD |
| JDK | 17 (Microsoft build) |
| Framework | Spring Boot 3.2.2 + Lettuce |

---

## 📁 Key Files

| File | Purpose |
|------|---------|
| `MemoryController.java` | REST controller — store/recall/forget/health |
| `RedisMemoryService.java` | Redis impl — SET/GET/DEL with TTL |
| `MemoryService.java` | Interface |
| `ApiKeyAuthenticationFilter.java` | Auth — validates API key format |
| `application.yml` | Config — Redis, server, memory settings |
| `flowstate.html` | New dashboard SPA |
| `pom.xml` | Maven — Spring Boot 3.2.2, Java 17 |

# Backend Architecture — Phase 2 Freeze

```text
React frontend (future)
         │ HTTP + JWT Bearer
         ▼
Spring Security filter chain → current UserAccount / CurrentUser
         │
         ▼
REST controllers → response DTO / mapper
         │
         ▼
Business services (Phase 3; absent in Phase 2)
         │
         ▼
Spring Data repositories → JPA entities → Hibernate → PostgreSQL 16
                                                    ▲
                            Flyway V001–V006 ───────┘ schema owner
```

The original read controllers call repositories directly for focused data reads. Phase 3.1–3.2 command controllers call transactional planning, approval and assignment services. Persistence packages do not import API classes; DTO mappers do not query the database; controllers do not parse JWT or contain SQL. Security surrounds the HTTP boundary and supplies a minimal `CurrentUser` principal. The 14 business tables and 117 columns/31 FKs come from the frozen database contract, not Hibernate DDL.

Eight GET routes return selected DTOs. Pagination is page 0/size 20 by default, size at most 100, with allowlisted sort and an ID tie breaker. Errors use `ErrorResponse` and HTTP status semantics. Login and health are public; other `/api/**` routes require Bearer authentication, with the pending director queue restricted to `BAN_GIAM_DOC`. Database role/active state is reloaded on each authenticated request. CORS remains closed pending a specific frontend origin.

Repositories are query primitives, not workflow policy. The database enforces PK/FK/UNIQUE/CHECK structure; Phase 3 services must enforce cross-row BR01–BR05 and own transactions that update states and append audit evidence together. See [phase_3_handoff.md](phase_3_handoff.md) for allowed extension points and frozen boundaries.

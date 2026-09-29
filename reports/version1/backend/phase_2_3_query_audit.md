# Phase 2.3 Query Audit

Measured with built-in Hibernate prepared-statement statistics on the Phase 1.3 603-row PostgreSQL demo dataset. Each scenario starts with an empty persistence context and cleared statistics. The count includes page/count SQL where Spring Data issues it.

| Query Scenario | Rows | SQL Statements | Result |
| --- | ---: | ---: | --- |
| Equipment list + department | 20 | 2 | PASS |
| Plan item list + equipment | 12 | 2 | PASS |
| Pending approval + display relations | 2 | 2 | PASS |

Each query uses a to-one `@EntityGraph` fetch plan. A small constant SQL count for these list samples avoids the obvious one-query-per-row pattern; this is not a production latency or scale benchmark.

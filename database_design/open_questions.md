# FINAL OPEN QUESTIONS

## Implementation Blockers

**None for the documented university V1 scope.** The supplied System Analysis defines UC01–UC12, BR01–BR05 and exact plan/item lifecycles. The freeze policies resolve structural ambiguity without claiming hospital-wide policy.

## Non-blocking Domain Questions

| ID | Question | Frozen V1 treatment | Would answer change current schema? |
| --- | --- | --- | --- |
| NQ-01 | Can the full QT02 procedure and dedicated post-maintenance forms be supplied? | SA remains primary authority; use BM01/02/03/06/08 fields as supporting evidence. | Not for the stated V1; new requirements would require change control. |
| NQ-02 | Does BM09 equipment handover supersede BM08 tool handover for post-maintenance use? | Follow UC10 actor/sign-off behavior; keep technical/handover type and two required digital signers. | No for current UC10. |
| NQ-03 | What evidence proves FREE versus NOT_FREE maintenance in each contract? | Require VTYT verification, basis note and date before routing. | No. |
| NQ-04 | Are roles ever simultaneous for one user? | One role/account in V1; ADMIN can manage separate accounts if necessary. | Yes only if the requirement changes. |
| NQ-05 | What is the hospital's retention duration and exact report number format? | No routine deletion; optional report number without invented format. | No. |
| NQ-06 | Is scan upload desired for a later demo? | Deferred optional feature, not part of mandatory UC09. | Would require future approved design change. |
| NQ-07 | Are additional paper witnesses or legally binding digital signatures required? | Only UC10 department and VTYT in-app confirmations are structured. | New requirements could change schema; outside stated V1. |

The absence of a full QT02 copy is disclosed; it does not erase the available primary System Analysis. Phase 1.2 may implement the frozen university V1 schema after human review of this freeze.


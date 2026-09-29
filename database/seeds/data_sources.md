# Phase 1.3 Data Sources and Provenance

Access date for external pages: **2026-09-25**. Manufacturer/model wording below is public reference information only. A model label in a synthetic equipment row does **not** assert that any real hospital owns it or has a service relationship with its manufacturer.

| Data Element | Value / Category | Source | Usage | Classification |
| --- | --- | --- | --- | --- |
| Patient monitor model | Philips IntelliVue MX450 | [Philips, IntelliVue MX450 product page](https://www.usa.philips.com/healthcare/product/HC866062) | `equipment.model` for synthetic monitors | PUBLIC REFERENCE |
| Intensive-care ventilator model | Dräger Evita V600 | [Dräger, Evita V600 product page](https://www.draeger.com/en_uk/Products/Evita-V600) | `equipment.model` for synthetic ICU ventilators | PUBLIC REFERENCE |
| Point-of-care ultrasound model | GE HealthCare Venue Go | [GE HealthCare, Venue Go product page](https://www.gehealthcare.com/en-us/products/ultrasound/point-of-care-ultrasound/venue/venuego) | `equipment.model` for synthetic imaging equipment | PUBLIC REFERENCE |
| Clinical chemistry analyzer model | Roche cobas c 311 | [Roche Diagnostics, cobas c 311 analyzer](https://diagnostics.roche.com/us/en/products/instruments/cobas-c-311-ins-2043.html) | `equipment.model` for synthetic laboratory analyzers | PUBLIC REFERENCE |
| Hematology analyzer model | Sysmex XN-1000 | [Sysmex, XN-Series analyzers](https://www.sysmex.com/en-ca/lab-solutions/hematology/xn-series) | `equipment.model` for synthetic laboratory analyzers | PUBLIC REFERENCE |
| Syringe pump model | B. Braun Spaceplus Perfusor | [B. Braun, Spaceplus system catalog](https://catalogs.bbraun.com/en-01/c/PRODUCTS0000000574/spaceplus-system) | `equipment.model` for synthetic syringe pumps | PUBLIC REFERENCE |
| Infusion pump model | B. Braun Spaceplus Infusomat | [B. Braun, Spaceplus system catalog](https://catalogs.bbraun.com/en-01/c/PRODUCTS0000000574/spaceplus-system) | `equipment.model` for synthetic infusion pumps | PUBLIC REFERENCE |
| Defibrillator model | Nihon Kohden Cardiolife TEC-8300 | [Nihon Kohden, TEC-8300 series](https://in.nihonkohden.com/en/products/resuscitation/defibrillators/cardiolife-tec-8300-series) | `equipment.model` for synthetic emergency devices | PUBLIC REFERENCE |
| Department terminology | ICU, emergency, imaging, laboratory, surgery, internal/surgical wards, medical equipment office | Supplied `docs/system_analysis_v1.pdf` and generic Vietnamese hospital terminology; no external hospital org chart was imported | Fictional department catalog | SYNTHETIC DEMO |
| Generic equipment model labels | `DEMO-GENERIC-*` | Invented for this project | Devices without a verified public product label | SYNTHETIC DEMO |
| Asset and serial codes | `DEMO-EQ-*`, `SYN-SN-*` | Invented for this project | Unique demo equipment identities | SYNTHETIC DEMO |
| User accounts and display labels | `*_demo*`, role codes from frozen dictionary | Invented for this project | Actor, approval and signer examples | SYNTHETIC DEMO |
| Service provider names | Names ending in `(Demo)` or containing `demo` | Invented for this project | Provider and approval workflow | SYNTHETIC DEMO |
| Coverage and contract references | `SYN-CONTRACT-*`, FREE/NOT_FREE/UNKNOWN decisions | Invented for this project; state values follow frozen dictionary | Route decision examples | SYNTHETIC DEMO |
| Plans, items, approvals, attempts, logs, acceptances, reports and histories | All stored workflow records and dates | Invented for this project; paths follow frozen lifecycle and constraints | Demo/API/test scenarios | SYNTHETIC DEMO |
| Password digests | BCrypt hashes of discarded random inputs | Generated locally; source values were discarded | Non-login placeholders until Phase 2 | SYNTHETIC DEMO |

No source was used for actual patient, staff, procurement, contract, serial-number or maintenance-history data. No external dataset was copied.

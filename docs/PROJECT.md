# PROJECT.md — Context and objectives

## What is SmartBusiness?

SmartBusiness is a mini ERP for commercial management aimed at small and medium businesses,
multi-tenant from the ground up (every customer of the application is isolated from the others — a
SaaS-ready architecture). It covers the full commercial cycle: customers → quotes → orders →
deliveries → invoices → payments → credit notes → returns, with an exact mirror on the purchasing
side, movement-based stock management, fine-grained per-company RBAC, and a dashboard.

This is not a CRUD demo: the difficulty of an ERP is in the business rules (an amount is calculated
only once, a settlement status is derived and never forced, stock is a sum of movements and never a
counter, two simultaneous requests must never pass the same check) — that is exactly what this
project was built to demonstrate.

## What is built

**Sales**: customers, quotes, orders, delivery notes, invoices, credit notes, return notes; a full
status workflow with a confirmation at every step; line-by-line tracking of what is still left to
deliver/return.
**Purchasing**: the exact mirror on the supplier side.
**Stock**: signed movements (entry/exit/transfer/adjustment/reservation), levels per warehouse, alert
thresholds.
**Catalogue**: products, categories, brands, taxes (VAT, FODEC, stamp duty).
**Payments**: append/cancel registers for sales and purchase invoices, settlement derived from them.
**Security**: JWT authentication, account lockout after failed attempts, RBAC by role and by
permission, business modules that can be toggled per company (the server itself drops the
permissions of a switched-off module, not just the display), strict per-company isolation on every
request, an audit log.
**Multi-tenant**: a separate platform portal to manage customer accounts (two distinct JWT worlds,
never mixed).
**Dashboard**: per-domain figures (sales, purchases, stock), 6-month charts, overdue invoices,
products below their minimum — each shown only if the company and the role are allowed to see it.
**Printing**: a single sheet for every document type, PDF export.

**785 backend tests** (unit, Spring test slices, end-to-end integration against PostgreSQL) and
**451 frontend tests** (Karma/Jasmine) — see [ROADMAP.md](ROADMAP.md) for the phase-by-phase detail.

## Technical objectives

This project was used to practise:

### Backend
- A clean, feature-based Spring Boot architecture, with no unnecessary layers (no
  Facade/Manager/UseCase)
- Spring Data JPA, JPQL queries, pessimistic locking on the critical races
- Flyway — versioned migrations, never edited once applied
- MapStruct — entity ↔ DTO mapping with no repeated code
- Spring Security + JWT, RBAC enforced server-side
- Designing derived business rules instead of fields updated by hand

### Frontend
- Angular 19 — Standalone Components, Signals, the `@if`/`@for` control flow
- A feature-based architecture, one service per domain in `core/services/`
- PrimeNG — mastering a full design system for a dense ERP interface
- Reactive Forms, a server-side preview before saving (nothing recalculated on the client)

### General
- Strict separation of Controller / Service / Repository
- Tests as a real safety net: a concurrency bug was reproduced and then fixed with a test that fails
  without the fix
- Documentation kept current throughout the project (`docs/ROADMAP.md` retraces every phase)

## Out of scope (deliberate choices, not oversights)

- Advanced accounting (general ledger, balance sheet)
- Multi-currency
- Electronic invoicing (TTN)
- A mobile app
- Partial invoicing of an order (tracking the invoiced quantity line by line)
- A global treasury page / a customer-supplier account statement
- External integrations (email, SMS, webhooks)

## Assumed constraints

- A desktop-first interface (an ERP is used at a desk)
- A single UI library: PrimeNG — never a second library, never a CSS framework
- Simple, readable code — clarity over cleverness (KISS, YAGNI)
- No fake business data in the application code; demo data comes from a separate seeding script
  (`tools/seed_demo.py`), outside the source code

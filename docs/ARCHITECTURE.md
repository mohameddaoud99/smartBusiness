# ARCHITECTURE.md — Technical architecture

## Overview

```
Angular 19 (localhost:4200)
        │
        │  HTTP/REST  /api/**
        ▼
Spring Boot 4 (localhost:8080)
        │
        │  JPA / Hibernate
        ▼
PostgreSQL (localhost:5432/smartBusiness)
        │
        │  Flyway migrations
        ▼
  db/migration/V*.sql
```

A stateless REST API (JWT, `open-in-view=false`) in front of a PostgreSQL database, consumed by an
Angular SPA. There is nothing exotic in the infrastructure — the difficulty of this project is in the
RULES that protect the schema, not the plumbing. What follows is a summary; every section below has the
full detail.

### What every feature follows, always

Every business domain (`sales/`, `purchase/`, `stock/`, `user/`...) follows exactly the same pattern —
`{Feature}.java` (entity), `Repository`, `Service`, `Controller`, `Mapper`, `Request`/`Response` — with
no extra layer (no Facade, Manager, UseCase). A new developer who has read one feature has read them
all. Detail: [Backend structure](#backend), [Frontend structure](#frontend).

### The decisions that separate an ERP from a CRUD app

- **Company isolation, enforced by the server, never by the client.** Every business table carries
  `company_id NOT NULL`; every repository query takes it from `CurrentUser` (extracted from the JWT
  server-side), never from a parameter sent by the frontend. Verified by a dedicated test that tries
  every endpoint with another company's token. See [RBAC.md](RBAC.md).
- **A commercial document never recalculates anything twice.** `common/DocumentTotals` is THE
  calculation (line discount → surcharges → VAT → stamp duty), used by quotes, orders, invoices, credit
  notes, on both the sales AND purchase side. The frontend displays whatever
  `POST /api/sales-documents/preview` computed — it never recalculates a total itself. See
  [Commercial document: snapshots and a single calculation](DEVELOPMENT.md).
- **Stock is a ledger, not a counter.** `StockMovement` is append-only, with a signed quantity; a
  mistake is corrected by a reverse movement, never by a direct update. "Available" = physical minus
  reserved, recomputed on every read. See [Append-only ledger](DEVELOPMENT.md).
- **A state that depends on a total rewrites itself from its source, never by hand.** An invoice's
  settlement status (unpaid / partially paid / paid) is rewritten on every payment or credit note from
  the SUM of active payments — there is no "mark as paid" button. Same principle for what has been
  delivered/received/returned of a document line. See [A derived state rewrites itself](DEVELOPMENT.md).
- **A race between two requests was hunted down, found, and fixed with a test that proves it.** Selling
  the last 2 units of a product twice at once, or delivering the same remainder of an order twice: both
  used to go through before a row lock (`PESSIMISTIC_WRITE`) was placed at the right spot. The
  accompanying test fails if the lock is removed — that isn't a claim, it's demonstrated.
- **RBAC doesn't just pretend on the server.** `*appHasPermission` hides a button in Angular, but every
  endpoint re-checks with `@PreAuthorize`. A business module switched off for a company (e.g. Purchases)
  removes the matching permissions on the VERY NEXT REQUEST, with no redeploy and no sign-out — because
  permissions are re-read from the database on every call, never cached in the token. See
  [RBAC.md](RBAC.md).
- **Every document prints on the same sheet.** A new document type adds one entry to a lookup table
  (`WORDING`) and a mapper — never a dedicated layout. The sheet adds nothing up: it displays what the
  server already calculated.
- **785 backend tests, 451 frontend tests**, including integration tests that recreate a real PostgreSQL
  database on every run (the database is never mocked for the critical paths) and concurrency tests that
  genuinely fire two requests in parallel.

---

## Backend

### Root package: `com.sales.smartBusiness`

```
com.sales.smartBusiness/
│
├── SmartBusinessApplication.java
│
├── config/
│   ├── WebConfig.java              CORS — allows localhost:4200 on /api/**
│   └── JpaConfig.java              @EnableJpaAuditing (createdAt / updatedAt auto)
│
├── exception/
│   ├── GlobalExceptionHandler.java     @RestControllerAdvice — handles every error
│   ├── ResourceNotFoundException.java  404 — resource not found
│   ├── DuplicateResourceException.java 409 — unique field already taken
│   ├── BusinessRuleException.java      422 — blocking business rule
│   └── ErrorResponse.java              Error response DTO
│
├── common/
│   ├── BaseEntity.java             @MappedSuperclass — createdAt + updatedAt + @Version
│   ├── BaseMapperConfig.java       Shared @MapperConfig — ignores createdAt/updatedAt everywhere
│   ├── SearchPattern.java          like(search) → "%term%" or "%" (never NULL for PostgreSQL)
│   ├── DocumentTotals.java         THE totals calculation, written once for sales AND purchase
│   │                                 documents: line discount → surcharges → VAT on base + "in-base"
│   │                                 surcharges → fixed taxes, HALF_UP 3 decimals. Plain numbers in
│   │                                 and out (no entity): documents feed it their lines
│   ├── Address.java / AddressDto.java  Shared @Embeddable — billing/shipping on Customer and Supplier
│   └── ImageStorage.java           Generic disk storage {domain}/{ownerId}/{kind}.{ext} —
│                                     used by product/ ; company/CompanyImageStorage was not migrated
│                                     onto it so as not to invalidate paths already stored
│
├── security/                       ✅ Authentication and isolation
│   ├── SecurityConfig.java         Stateless chain + @EnableMethodSecurity
│   ├── JwtService.java             Issues/reads both token shapes (see §Platform)
│   ├── JwtAuthenticationFilter.java Bearer → user OR platform admin, reloaded fresh
│   ├── AuthenticatedUser.java      Record: id, companyId, email, permissions
│   ├── CurrentUser.java            Caller identity for a company user, for services
│   ├── PlatformPrincipal.java      Record: id, email — no companyId
│   ├── CurrentPlatformAdmin.java   Caller identity for a platform admin
│   └── PasswordEncoderConfig.java  BCrypt bean
│
├── auth/                           ✅ /api/auth
│   ├── AuthController.java  AuthService.java
│   ├── RegisterRequest.java  LoginRequest.java  ChangePasswordRequest.java  UpdateProfileRequest.java
│   └── AuthResponse.java  SessionResponse.java
│
├── platform/                       ✅ Platform operators — outside the tenant model
│   ├── PlatformAdmin.java  PlatformAdminStatus.java  PlatformAdminRepository.java
│   ├── PlatformAdminBootstrap.java Creates the first account at startup (env variables)
│   ├── PlatformAuthController.java  PlatformAuthService.java  — /api/platform/auth
│   ├── PlatformCompanyController.java  PlatformCompanyService.java  — /api/platform/companies
│   └── PlatformLoginRequest.java  PlatformAuthResponse.java  PlatformSessionResponse.java
│       PlatformCompanyResponse.java  UpdateCompanyModulesRequest.java
│
├── company/                        ✅ The tenant — /api/company (singular, no id)
│   ├── Company.java  CompanyStatus.java  CompanyRepository.java
│   ├── BusinessModule.java         Platform-level grain (4) ≠ RBAC PermissionModule (11)
│   └── CompanyService.java  CompanyController.java  CompanyMapper.java
│       CompanyRequest.java  CompanyResponse.java
│
├── branch/                         ✅ Company sites (outside RBAC)
│   ├── Branch.java  BranchStatus.java  BranchRepository.java
│   └── BranchService.java  BranchController.java  BranchMapper.java
│       BranchRequest.java  BranchResponse.java
│
├── bankaccount/                    ✅ Company bank accounts — /api/settings/bank-accounts
│   └── CompanyBankAccount.java  …Repository/Service/Controller/Mapper/Request/Response
│
├── tax/                            ✅ Tax profile — /api/settings/taxes
│   ├── Tax.java  TaxKind.java      Tunisian tax set seeded by TaxService (not a migration)
│   └── TaxRepository/Service/Controller/Mapper/Request/Response.java
│
├── numbering/                      ✅ Document numbering — /api/settings/numbering
│   ├── DocumentType.java           Enum: the numbered document types (extensible)
│   ├── NumberingSequence.java      One sequence per type and per company
│   └── NumberingService.java (allocate(): lock → NumberingSequence.allocate(year))  …Controller/Mapper/Request/Response
│       — a type never used is shown with its defaults without being written (GET has no side effect)
│
├── customer/                       ✅ Customers — /api/customers
│   ├── Customer.java  CustomerType.java   (COMPANY | INDIVIDUAL, a single table)
│   └── CustomerRepository/Service/Controller/Mapper/Request/Response.java
│       — auto reference via numberingService.allocate(CUSTOMER) ("C-0001"), generated when left blank;
│         Address/AddressDto come from common/
│
├── supplier/                       ✅ Suppliers — /api/suppliers
│   ├── Supplier.java  SupplierType.java   (COMPANY | INDIVIDUAL, a single table)
│   └── SupplierRepository/Service/Controller/Mapper/Request/Response.java
│       — an exact mirror of customer/, without a VAT-suspension permit (that only makes sense for a
│         customer buying tax-free); auto reference SUPPLIER ("F-0001")
│
├── category/                       ✅ Product families — /api/categories
│   ├── Category.java               Self-referencing nullable parent (Family/Sub-family)
│   └── CategoryRepository/Service/Controller/Mapper/Request/Response.java
│       — no auto reference (Finco doesn't show one for families); cycle protection (a category cannot
│         become its own descendant); CategoryRepository.countProductsUsing() queries Product in JPQL
│         without depending on ProductRepository (same pattern as RoleRepository.countHolders)
│
├── brand/                          ✅ Brands — /api/brands
│   └── Brand.java  BrandRepository/Service/Controller/Mapper/Request/Response.java
│       — minimal CRUD, same anti-dependency pattern as category/ to block deleting a brand still in use
│
├── product/                        ✅ Catalogue — /api/products
│   ├── Product.java  ProductKind.java (GOOD|SERVICE)  ProductPurpose.java (SALE|PURCHASE|BOTH)
│   ├── ProductUnit.java             Fixed catalogue (no settings screen — Finco only shows the unit as
│   │                                 a field, never configured)
│   └── ProductRepository/Service/Controller/Mapper/Request/Response.java
│       — auto reference PRODUCT ("P-0001"); category/brand resolved as 422 via
│         CategoryService.getAssignable()/BrandService.getAssignable() (never their repository);
│         defaultTaxes (ManyToMany to tax/Tax) resolved via TaxService.resolveAssignable() — snapshotted
│         onto the document line in Phase 4, this table only supplies the default
│       — photos: up to 4 per product (ProductService.MAX_IMAGES), child entity ProductImage (table
│         product_images, path + content type, file via common/ImageStorage domain "products", one
│         unique name per photo); the 1st is the cover. POST /api/products/{id}/images ·
│         DELETE /api/products/{id}/images/{imageId}. The product owns the lifecycle: deleting a product
│         deletes its files. LIST response = cover only (imageDataUri); DETAIL response (GET/{id},
│         create, update, add/remove photo) = the full images[]. Trade-off: one disk read per list row,
│         photos capped at 1 MB. If the list grows: a binary endpoint
│
├── warehouse/                      ✅ Warehouses — /api/warehouses
│   ├── Warehouse.java  WarehouseRepository/Service/Controller/Mapper/Request/Response.java
│   │   — a default warehouse is created at registration (WarehouseService.createDefaults, called by
│   │     AuthService.register) and by V21 for existing companies; it can be neither deleted nor
│   │     deactivated. An inactive warehouse keeps its history but no longer receives movements;
│   │     deleting a warehouse that has movements is refused (a JPQL COUNT in its own repository).
│   │     getAssignable() (active, 422) and getDefault() serve the other features.
│
├── stock/                          ✅ Stock ledger — /api/stock
│   ├── StockMovement.java  StockMovementType.java  StockSource.java  StockRequirement.java
│   └── StockMovementRepository/Service/Controller/Mapper + Request/Response
│       — stock is NEVER a counter: it is the SUM of movements (an append-only ledger, SIGNED quantity).
│         ENTRY/EXIT/ADJUSTMENT/TRANSFER_IN/TRANSFER_OUT move the physical quantity; RESERVE/RELEASE move
│         what is promised. available = physical − reserved.
│       — manual: entry, exit, adjustment (the COUNTED quantity is entered, the service writes the
│         difference), transfer (two rows). A product that forbids "negative stock" cannot go below 0
│         available in that warehouse (422 "Not enough stock of …").
│       — driven by a document: SalesDocumentService calls stockService.reserve(...) when an order is
│         CONFIRMED (default warehouse, refused if stock is insufficient → the order stays "issued") and
│         stockService.release(...) when it is cancelled (gives back whatever it still held). Stock
│         doesn't know about `sales/`: it only ever sees a StockSource + an id.
│       — levels: GET /levels (one row per GOOD — a service has no stock; warehouse filter, "low stock"
│         filter). Threshold `Product.minStock` is UNIQUE for the whole company (not per warehouse): the
│         "low" flag and its filter therefore don't exist once a warehouse is picked.
│       — ProductRepository.searchGoods computes the "low" filter with a JPQL sub-query on StockMovement
│         (types compared as text: product/ does not import stock/); deleting a product that has
│         movements is refused.
│       — Delivery: stockService.deliver / undoDelivery (see sales/). A movement's `origin_id` column
│         (V23) names the document that caused it when the source is another document (the delivery
│         note, for the RELEASE rows of an order) — that is what lets a cancellation undo everything.
│       — Deferred: exit at invoicing (Phase 7), per-warehouse threshold, locking (LOCK/UNLOCK),
│         lots/serials, valuation, dashboard indicator.
│
├── dashboard/                      ✅ The home page's figures — /api/dashboard/{sales,purchases,stock}
│   └── DashboardController.java    (no entity, no repository, NO service: nothing to add to what the
│                                     features already compute)
│       — one endpoint per domain, each asking the feature that owns the data and carrying ITS right:
│         `sales` → SALE_VIEW (`SalesDocumentService.figures(today)`), `purchases` → PURCHASE_VIEW
│         (`PurchaseDocumentService.figures(today)`), `stock` → STOCK_VIEW (`StockService.figures()`). A
│         switched-off module answers 403 like the rest of its endpoints. The queries live in the
│         features' own repositories (`sumTotal`, `unpaid`, `overdue`, `overdueInvoices`, `stockValue`);
│         `common/AmountSummary` and `common/MonthAmount` are the small shared read models.
│       — Sales: NET revenue (issued / partially paid / paid invoices MINUS issued credit notes) for
│         today and this month, unpaid and overdue invoices (balance = total − credited − paid), the 5
│         oldest overdue invoices, the last 6 months. Purchases: the same with purchase invoices and
│         their credit notes. Stock: value at PURCHASE price (sum of signed PHYSICAL movements × purchase
│         price; no purchase price = 0; a reservation is not a good) and the top 5 products at their
│         minimum. Everything is computed server-side; the frontend adds nothing up.
│
├── printprofile/                   ✅ What a printed sheet says about the company — GET /api/print-profile
│   └── PrintProfileService/Controller/Response.java   (no entity, no repository: nothing is stored)
│       — assembles, through the SERVICES of the features that own the data (CompanyService.find(),
│         CompanyBankAccountService.findAll()), the identity, tax ID, logo, stamp and ONLY the bank
│         accounts flagged "show on documents". Open to SALE_VIEW / PURCHASE_VIEW / COMPANY_VIEW: a
│         salesperson lacks COMPANY_VIEW but still needs to print. The customer / supplier are read
│         through their usual endpoints (CUSTOMER_VIEW / SUPPLIER_VIEW).
│
├── purchase/                       ✅ Purchase orders + goods receipts + purchase invoices + supplier credit notes — /api/purchase-documents
│   ├── PurchaseDocument.java  PurchaseDocumentLine.java  PurchaseDocumentTax.java   (3 entities, one feature)
│   ├── PurchaseDocumentType.java (PURCHASE_ORDER|GOODS_RECEIPT|PURCHASE_INVOICE|PURCHASE_CREDIT_NOTE)
│   │   PurchaseDocumentStatus.java (DRAFT|VALIDATED|PARTIALLY_PAID|PAID|CANCELLED)
│   └── PurchaseDocumentRepository/Service/Controller/Mapper/Request/Response.java (+ Line/Tax/Summary DTO)
│       — the MIRROR of sales/ (Finco §06: the two supplier documents only have Draft / Validated /
│         Cancelled). Same life: number-less draft → validation (number allocated at that moment) →
│         frozen; line and tax snapshots; POST /preview; the calculation is common/DocumentTotals.
│       — Differences: party = supplier (SupplierService.getAssignable), default price = PURCHASE price,
│         a "sale only" product is refused (ProductService.resolvePurchasable), no quote and no
│         accepted/rejected/confirmed statuses.
│       — A goods receipt carries a WAREHOUSE (required, active): VALIDATING it writes a stock ENTRY per
│         good (stockService.receive, source PURCHASE_DOCUMENT), in the same transaction as the number;
│         CANCELLING it writes the reverse exit (stockService.reverseReceipt) — refused if a product that
│         forbids negative stock no longer has the goods (already sold or reserved): the receipt then
│         stays "validated".
│       — POST /{id}/convert-to-receipt: a validated order → a DRAFT goods receipt (default warehouse,
│         lines and taxes copied over), which the user adjusts to what actually arrived. An order can
│         have SEVERAL receipts (partial deliveries) — unlike quote → order; no per-line
│         received-quantity tracking yet. An order with a live receipt cannot be cancelled.
│       — CustomerRepository-style: SupplierRepository / ProductRepository block their own deletion with
│         a JPQL COUNT on PurchaseDocument / PurchaseDocumentLine.
│       — PURCHASE INVOICE (7c): an exact mirror of the sales invoice. Same table, number `PINV`
│         allocated at VALIDATION, `warehouse_id` required, `paid_amount` (V26). Statuses: draft →
│         validated ("unpaid", VALIDATED) → PARTIALLY_PAID → PAID, or CANCELLED; settlement is never a
│         manual change: `PurchaseDocument.applyPaid(SUM of active payments)` rewrites `paid_amount` AND
│         the status (never "+x").
│       — POST /{id}/convert-to-invoice: a VALIDATED order or a VALIDATED goods receipt → a draft
│         invoice (lines copied; the receipt's warehouse, else the default). One live invoice per source;
│         an order received through receipts is invoiced THROUGH THE RECEIPTS; an invoiced order can no
│         longer be received; a source carrying a live invoice cannot be cancelled (`hasLiveChild` is
│         typed: a receipt and an invoice don't mean the same thing). `sourceType` says where an invoice
│         came from.
│       — Stock, at VALIDATION: an invoice made from a goods receipt = no movement (the receipt already
│         brought the goods in); otherwise `stockService.receive` (ENTRY). Cancelling an unpaid invoice
│         does `reverseReceipt` (unless it came from a receipt), refused if a strict product no longer
│         has the goods. A paid invoice, even partially, cannot be cancelled: its payments must be
│         cancelled first.
│       — SUPPLIER CREDIT NOTE (7d): an exact mirror of the sales credit note. Same table, number `PCN`
│         allocated at validation, `credited_amount` on the invoice (V27). It is born from ONE validated
│         invoice (`POST /{id}/convert-to-credit-note`, never created by hand: `create` refuses), keeping
│         its supplier; draft = a copy of the lines that the user lowers to what is credited (several
│         credit notes per invoice, up to its total). VALIDATED = applied at once:
│         `applyCredited(SUM of validated credit notes)` rewrites the invoice's `credited_amount`;
│         refused (the credit note stays a draft, its number given back) if the running total would
│         exceed the invoice total. Statuses: draft → validated ("Applied") → cancelled; cancelling a
│         credit note gives its amount back to the invoice.
│       — A purchase invoice's settlement = payments + credit notes: `refreshSettlement()` (settled =
│         paid + credited); balance = total − credited − paid, NEGATIVE when a credit note follows a
│         payment (the supplier now owes us the difference: "To recover" on screen — recovering it isn't
│         built yet). A supplier payment is capped at total − credited − paid. An invoice carrying a live
│         credit note (even a draft one) cannot be cancelled.
│       — A supplier credit note does NOT touch stock: it is a financial document; goods sent back are
│         taken out by hand (the future supplier return note will do it).
│       — SUPPLIER RETURN NOTE (8): type `PURCHASE_RETURN_NOTE` (number `PRN`), the SAME table, NO
│         migration. Goods going back to the supplier: warehouse required; VALIDATE = stock EXIT
│         (`stockService.deliver` with no order, refused — the note stays a draft, its number given back
│         — if a strict product doesn't have the goods); CANCELLING it brings them back
│         (`undoDelivery`). Created by hand OR from a validated goods receipt or invoice
│         (`POST /{id}/convert-to-return-note`, lines copied then lowered, the supplier then locked). A
│         goods receipt or an invoice carrying a live return note (even a draft one) cannot be cancelled:
│         its stock exit would happen twice. Several return notes per document; the quantities already
│         returned are tracked line by line: see below. Independent of credit notes: the financial side
│         stays separate.
│       — QUANTITIES TRACKED LINE BY LINE (V28, `source_line_id` on `purchase_document_lines`): a line
│         copied from a source document (an order line onto a goods receipt, a receipt line onto a
│         return note...) keeps the line it came from (`sourceLine`). What has been taken of a line is
│         the SUM of the lines that follow it (`quantitiesTaken`, a `SUM … GROUP BY` in the repository)
│         — never a stored counter. Purchase order ← validated goods receipts: "received" / "left to
│         receive"; goods receipt or invoice ← validated supplier return notes: "returned" / "left to
│         return". `Tracking` (private to the service) says which type takes from which type and which
│         statuses count. The service only keeps a link when the document has a source and the original
│         line belongs to it (otherwise 422); `checkAgainstSource` refuses, at VALIDATION, a document that
│         would take more than what is left — that is the moment the goods are committed, so two drafts
│         made for the same remainder cannot both go through. `convert-to-…` prefills with what is left
│         and refuses when nothing remains; the responses carry `fulfilledQuantity` / `remainingQuantity`
│         (on the tracked document) and `sourceRemaining` (on a draft that follows a line, itself
│         excluded). The frontend sends `sourceLineId` back unchanged with the draft — without it the
│         line's tracking is lost.
│       — Deferred: the supplier's own invoice number, treasury, tracking of returned quantities.
│
├── supplierpayment/                ✅ Payments made to suppliers — /api/supplier-payments
│   ├── SupplierPayment.java  SupplierPaymentRepository/Service/Controller/Mapper/Response.java
│   │   — the MIRROR of payment/: the same register (add and cancel, no PUT or DELETE), the same cap
│   │     (what remains due), the same `PaymentMethod` / `PaymentStatus` enums and the same incoming DTO
│   │     `PaymentRequest` (imported from payment/). Invoice resolved by
│   │     `purchaseDocumentService.getPayableInvoice`, status rewritten by `applyPaid(sum)`.
│   │     Rights: PURCHASE_VIEW (read) · PURCHASE_UPDATE (record) · PURCHASE_CANCEL (cancel).
│
├── sales/                          ✅ Quotes + orders + delivery notes + invoices + credit notes — /api/sales-documents
│   ├── SalesDocument.java  SalesDocumentLine.java  SalesDocumentTax.java   (3 entities, one feature)
│   ├── SalesDocumentType.java (QUOTE|SALES_ORDER|DELIVERY_NOTE|INVOICE|CREDIT_NOTE)  SalesDocumentStatus.java
│   └── SalesDocumentRepository/Service/Controller/Mapper/Request/Response.java
│       + SalesDocumentLineRequest/LineResponse · TaxResponse · SummaryResponse · StatusRequest
│       — ONE generic document whose `type` says what it is (Finco §15): the invoice and the credit note
│         were added without a new table. Lines and tax rows are SNAPSHOTS (designation, price, VAT rate
│         all copied in): renaming a product or changing a rate never rewrites a document.
│       — The calculation lives in common/DocumentTotals (shared with purchase/): line discount →
│         surcharges (FODEC, computed per VAT rate) → VAT on base + "in-base" surcharges → fixed taxes
│         (stamp duty). Rounded HALF_UP to 3 decimals. SalesDocument.recalculate() only ever feeds it its
│         own lines and tax rows. The frontend never recalculates: it calls POST /preview (same rules,
│         nothing saved).
│       — Workflow: draft (editable, NO number) → issued (number allocated by
│         numberingService.allocate(type.getNumbering()): a discarded draft leaves no gap, the document
│         is then frozen) → a quote: accepted / rejected (freely reversible); an order: confirmed /
│         cancelled. SalesDocument.canMoveTo() carries the transitions. Deletion = drafts only.
│       — POST /{id}/convert-to-order: an issued or accepted quote → a draft order (source_id), lines
│         and taxes copied as-is; only one live order per quote.
│       — Customer resolved via CustomerService.getAssignable(), products via
│         ProductService.resolveSellable() ("purchase only" refused), taxes via
│         TaxService.resolveAssignable() — never their repositories directly. CustomerRepository /
│         ProductRepository block their own deletion with a JPQL COUNT query on
│         SalesDocument / SalesDocumentLine.
│       — A quote's "invoiced/delivered" status is DERIVED (`derived` documents), never stored.
│       — Confirming an order RESERVES its goods (stockService.reserve); cancelling it releases them.
│       — DELIVERY NOTE (4b): `warehouse_id` (required, active, V23) = where the goods leave from.
│         POST /{id}/convert-to-delivery-note: a CONFIRMED order → a DRAFT note (default warehouse, lines
│         copied, several notes per order = partial deliveries, no per-line delivered-quantity tracking
│         at this stage). Workflow: draft → issued → DELIVERED; cancellable while issued or delivered.
│         Moving to DELIVERED calls stockService.deliver(...) in the same transaction: an EXIT (EXIT) per
│         good, owned by the note, which CONSUMES the order's reservation (RELEASE rows: source = the
│         order, `origin_id` = the note). Cancelling a delivered note: stockService.undoDelivery(...)
│         brings the goods back (ENTRY) and RE-RESERVES for the order if it still stands. An order with a
│         live note cannot be cancelled. A note with no order: deliverable, but with no reservation to
│         consume (refused if the available stock is insufficient).
│       — INVOICE (7a): the same table, number `INV` allocated at issue, `warehouse_id` required just
│         like the delivery note, `paid_amount` (V24). Statuses: draft → issued ("unpaid") →
│         PARTIALLY_PAID → PAID, or CANCELLED. Settlement is NEVER a manual status change: `changeStatus`
│         refuses on an invoice, `SalesDocument.applyPaid(sum)` sets `paid_amount` AND the status from the
│         SUM of active payments (never "+x": the invoice can't drift away from its own payments).
│         Balance = `getBalance()`, computed server-side.
│       — POST /{id}/convert-to-invoice: an issued/accepted quote, a CONFIRMED order or a DELIVERED note
│         → a draft invoice (lines copied; the note's warehouse, else the default). One live invoice per
│         source; an order delivered through notes is invoiced THROUGH THE NOTES; an invoiced order can
│         no longer be delivered; a note or order carrying a live invoice cannot be cancelled.
│         `sourceType` says where an invoice came from.
│       — Effect on stock, at ISSUE (number and exit in the same transaction): an invoice made from a
│         delivery note = no movement (the note already took the goods out); otherwise
│         `stockService.deliver` (consumes the source order's reservation), refused if a strict product
│         is short → the invoice stays a draft, its number given back. Cancelling an unpaid invoice does
│         `undoDelivery` (unless it came from a note). A paid invoice, even partially, cannot be
│         cancelled: its payments must be cancelled first.
│       — CREDIT NOTE (7b): the same table, number `CN`, `credited_amount` on the invoice (V25). It is
│         born from ONE issued invoice (`POST /{id}/convert-to-credit-note`, never created by hand:
│         `create` refuses), keeping its customer; draft = a copy of the lines that the user lowers to
│         what is credited (several credit notes per invoice, up to its total). ISSUED = applied at once:
│         `applyCredited(SUM of issued credit notes)` rewrites the invoice's `credited_amount`; refused
│         (the credit note stays a draft, its number given back) if the running total would exceed the
│         invoice total. Statuses: draft → issued ("Applied") → cancelled; cancelling a credit note gives
│         its amount back to the invoice.
│       — An invoice's settlement = payments + credit notes: `refreshSettlement()` (settled = paid +
│         credited) gives unpaid / partially paid / paid; balance = total − credited − paid, NEGATIVE
│         when a credit note follows a payment ("To refund" on screen — the refund itself isn't built
│         yet). A payment is capped at total − credited − paid. An invoice carrying a live credit note
│         (even a draft one) cannot be cancelled.
│       — A credit note does NOT touch stock: it is a financial document; goods coming back go through
│         the RETURN NOTE (8), a separate document.
│       — CUSTOMER RETURN NOTE (8): type `RETURN_NOTE` (number `RN`), the SAME table, NO migration. Goods
│         coming back from a customer: warehouse required; ISSUING it = stock ENTRY
│         (`stockService.receive`, never refused); CANCELLING it takes them back out (`reverseReceipt`,
│         refused if a strict product no longer has the goods — the note stays issued). Created by hand
│         OR from a DELIVERED delivery note or an issued invoice
│         (`POST /{id}/convert-to-return-note`, lines copied then lowered, the customer then locked). A
│         delivery note or invoice carrying a live return note (even a draft one) cannot be cancelled:
│         putting the goods back into stock would happen twice. Several notes per document; returned
│         quantities tracked line by line (see below). Independent of credit notes: goods can be returned
│         without a credit note, and the other way round.
│       — QUANTITIES TRACKED LINE BY LINE (V28, `source_line_id` on `sales_document_lines`): a line
│         copied from a source document (an order line onto a delivery note, a note line onto a return
│         note...) keeps the line it came from (`sourceLine`). What has been taken of a line is the SUM
│         of the lines that follow it (`quantitiesTaken`, a `SUM … GROUP BY` in the repository) — never a
│         stored counter. An order ← issued or delivered delivery notes: "delivered" / "left to deliver";
│         a delivery note or invoice ← issued return notes: "returned" / "left to return". `Tracking`
│         (private to the service) says which type takes from which type and which statuses count. The
│         service only keeps a link when the document has a source and the original line belongs to it
│         (otherwise 422); `checkAgainstSource` refuses, at ISSUE, a document that would take more than
│         what is left — that is the moment the goods are committed, so two drafts made for the same
│         remainder cannot both go through. `convert-to-…` prefills with what is left and refuses when
│         nothing remains; the responses carry `fulfilledQuantity` / `remainingQuantity` (on the tracked
│         document) and `sourceRemaining` (on a draft that follows a line, itself excluded). The frontend
│         sends `sourceLineId` back unchanged with the draft — without it the line's tracking is lost.
│
├── payment/                        ✅ Payments received on sales invoices — /api/payments
│   ├── Payment.java  PaymentMethod.java (CASH|BANK_TRANSFER|CHECK|CARD|OTHER)  PaymentStatus.java (ACTIVE|CANCELLED)
│   └── PaymentRepository/Service/Controller/Mapper/Request/Response.java
│       — a REGISTER just like stock: rows are added and cancelled, never modified or deleted (no PUT,
│         no DELETE).
│       — create: the payable invoice is resolved by `salesDocumentService.getPayableInvoice` (issued or
│         partially paid), refused past what remains due, then
│         `salesDocumentService.applyPaid(id, SUM of active payments)`; cancel: flips the payment to
│         CANCELLED, flushes, rewrites the sum. No permission of its own: SALE_VIEW (read),
│         SALE_UPDATE (record), SALE_CANCEL (cancel). GET ?invoiceId= (paginated).
│       — Deferred: treasury accounts, following a cheque through to being cashed, withholding tax, a
│         payment left unallocated / split across several invoices, a global "Payments" page, supplier
│         payments.
│
├── role/                           ✅ The vocabulary of rights
│   ├── Permission.java             38 permissions, the single source of truth (no table)
│   ├── PermissionModule.java  SystemRole.java
│   ├── Role.java  RoleRepository.java  RoleService.java  RoleController.java
│   ├── RoleMapper.java  RoleRequest.java  RoleResponse.java
│   └── RoleSummaryResponse.java  PermissionModuleResponse.java
│
├── audit/                          ✅ The company's security log
│   ├── AuditLog.java  AuditAction.java  AuditEntity.java
│   ├── AuditLogRepository.java  AuditService.java
│   └── AuditLogController.java  AuditLogMapper.java  AuditLogResponse.java
│
├── user/                           ✅ Business module — reference model
│   ├── User.java  UserStatus.java
│   ├── UserRepository.java
│   ├── UserService.java  UserController.java  UserMapper.java
│   └── UserRequest.java  UserResponse.java  ResetPasswordRequest.java
│
└── {feature}/                      A feature is a flat folder
    ├── {Feature}.java
    ├── {Feature}Repository.java
    ├── {Feature}Service.java
    ├── {Feature}Controller.java
    ├── {Feature}Mapper.java
    ├── {Feature}Request.java
    └── {Feature}Response.java
```

### Audit log

A single `audit_logs` table per company, fed **by the services themselves** on every sensitive
operation:

```java
auditService.record(AuditAction.ROLE_CREATED, AuditEntity.ROLE, role.getId(),
                    "Role \"" + role.getLabel() + "\" created");
```

No generic audit framework, no AOP: an explicit call right where the change happens. Reading
`UserService` shows exactly what gets logged.

A user's "History" tab is just a filtered read of this same table (`entityType = USER`) — there is no
separate `user_history` table.

### Security — two independent checks

```
HTTP Request  (Authorization: Bearer …)
    │
    ▼
JwtAuthenticationFilter  token → userId → user + roles reloaded from the database
    │                    status ≠ ACTIVE → anonymous
    ▼
@PreAuthorize            hasAuthority('CUSTOMER_CREATE')      → 403 otherwise
    │
    ▼
Service                  repository.findByIdAndCompanyId(id, currentUser.companyId())
    │                                                          → 404 otherwise
    ▼
Response
```

Permissions are **not** in the token: they are re-read on every request, so disabling a user or
changing a role applies immediately. `companyId` always comes from `CurrentUser`, never from a
client-supplied parameter.

Full design: [`RBAC.md`](RBAC.md).

### Platform and per-company modules

A second authentication world, completely sealed off from the first:

```
Company token    { sub: userId,  companyId: 7 }        → hasAuthority('USER_VIEW', ...)
Platform token   { sub: adminId, platform: true }       → hasAuthority('PLATFORM_ADMIN')
```

`JwtService.parse()` reads both shapes in one pass; `JwtAuthenticationFilter` authenticates either a
`User` (company) or a `PlatformAdmin` (platform), never both. A platform token carries **no** business
permission — `/api/users`, `/api/company` etc. all answer 403. A company token **never** carries
`PLATFORM_ADMIN` — `/api/platform/**` answers 403. No extra check is needed: it's the same
`@PreAuthorize` mechanism used everywhere else, applied to two disjoint authority vocabularies.

`PlatformCompanyService` is **the only place in the whole application** that reads a `Company` by its
id without ever going through `currentUser.companyId()` — precisely because the caller belongs to no
company at all. This exception is deliberate, commented in the code, and covered by
`PlatformAuthorizationTest`.

Every company carries a `Set<PermissionModule>` (`company_modules`, fine-grained storage — 6 possible
values): which business modules it is allowed to use. Administrative modules (Users, Roles, Branches,
Company, Security) never appear there — they are always available, since a company must always be able
to manage itself.

But a platform admin **never** touches `PermissionModule` directly — instead it toggles
`BusinessModule` (`company/BusinessModule.java`), a coarser grain matching what a user actually sees in
the menu:

```
Permission        → WHO in the company can do WHAT              (RBAC, unchanged, 11 modules)
BusinessModule    → WHICH modules has the company bought        (PlatformCompanyService, 4 modules)

CUSTOMERS  → { CUSTOMERS }
SALES      → { SALES }
PURCHASES  → { PURCHASES, SUPPLIERS }   -- one toggle, two PermissionModule together
INVENTORY  → { PRODUCTS, STOCK }        -- same idea
```

`PlatformCompanyService` translates both ways: `updateModules()` expands each incoming
`BusinessModule` into its `PermissionModule`s before writing `company.enabledModules`; reading rebuilds
the `BusinessModule`s to show by checking that all their `PermissionModule`s are present. Toggling
"Purchases" always moves Purchases *and* Suppliers together — never one without the other, never at the
fine-grained RBAC level.

At registration, a company receives every module (`BusinessModule.allPermissionModules()`). A platform
admin then restricts them (`PUT /api/platform/companies/{id}/modules`). No free trial, no billing yet: a
human decides, for now. The day automatic plans arrive, only what *populates* `enabledModules` changes
(a checkout instead of an admin's click) — the check itself stays identical.

First platform account: created once at startup by `PlatformAdminBootstrap` from
`PLATFORM_ADMIN_EMAIL` / `PLATFORM_ADMIN_PASSWORD` — no self-service sign-up for this role.

`SessionResponse` (`/api/auth/me`) also carries `enabledModules` — the `BusinessModule`s of **the
caller's own** company, via the same `BusinessModule.enabledAmong(...)` method the platform portal
uses. On the Angular side, `navigation.ts` (`NavItem.module`) and `AuthService.hasModule()` filter the
sidebar with it: a group whose module isn't enabled disappears entirely, exactly like `NavItem.permission`
already does.

### Request processing flow

```
HTTP Request
    │
    ▼
@RestController          validates @Valid on the @RequestBody
    │
    ▼
@Service                 business logic, @Transactional transactions
    │
    ▼
@Repository              database access (JpaRepository)
    │
    ▼
PostgreSQL
    │
    ▼
MapStruct Mapper         Entity → Response DTO
    │
    ▼
HTTP Response (JSON)
```

### Tests

```
src/test/java/com/sales/smartBusiness/
├── IntegrationTest.java            Base class: a real HTTP chain + the smartbusiness_test database
├── CompanyIsolationTest.java       18 cross-company access attempts
├── AuthorizationTest.java          12 tests — 401 / 403 / immediate revocation
├── PrivilegeEscalationTest.java    12 tests — anti-escalation and anti-lockout
├── PlatformAuthorizationTest.java  9 tests — company token / platform token isolation
├── CompanyProfileIntegrationTest.java  5 tests — real upload/delete on disk
├── DataConsistencyIntegrationTest.java 3 tests — @Version, LOWER() index, numbered customer reference
├── CatalogueIntegrationTest.java   8 tests — nested categories, cycle protection, category/brand/tax resolution on a product
├── StockIntegrationTest.java       18 tests — levels from real SQL sums, "low stock" filter, exit /
│                                   adjustment / transfer rules, reservation by a confirmed order (and refusal), guards, permissions
├── PrintProfileIntegrationTest.java 4 tests — identity + only flagged accounts shown, cross-company isolation, rights
├── PurchaseIntegrationTest.java    19 tests — totals, numbering at validation, receipt → stock (and cancellation),
│                                   partial receipts, refused cancellation, guards, permissions
├── warehouse/  stock/  purchase/   Service (Mockito) and controller tests · common/DocumentTotalsTest (pure calculation)
├── SalesIntegrationTest.java       14 tests — totals for the Finco case (37.057), workflow, numbering at issue,
│                                   quote → order conversion, customer/product guard, permissions
├── CreditNoteIntegrationTest.java  12 tests — credit note → invoice (partial, cap, full, paid invoice, cancellation), no
│                                   stock movement, links and refusals, permissions
├── DashboardIntegrationTest.java     7 tests — figures against real SQL (net of credit notes, unpaid, overdue, 6 months, stock value),
│                                   isolation, per-domain rights, switched-off module
├── dashboard/                      DashboardControllerTest
├── LineQuantityTrackingIntegrationTest.java 7 tests — left to deliver / receive / return: prefill, refusing an
│                                   over-take (two drafts for the same remainder), a cancellation giving the quantity back, a foreign link refused
├── ReturnNoteIntegrationTest.java   10 tests — customer return note → stock (from a note, an invoice, by hand),
│                                   refused cancellation, links, parties, permissions
├── PurchaseReturnNoteIntegrationTest.java 9 tests — supplier return note → stock (exit, refusal, cancellation), links, permissions
├── PurchaseCreditNoteIntegrationTest.java 11 tests — supplier credit note → invoice (partial, cap, full, paid invoice,
│                                   cancellation), no stock movement, links and refusals, permissions
├── PurchaseInvoiceIntegrationTest.java 16 tests — purchase invoice → stock (by hand, from an order, from a receipt),
│                                   supplier payments, links between documents, permissions
├── InvoiceIntegrationTest.java     18 tests — invoice → stock (by hand, from an order, from a note), payments
│                                   (status, over-payment, cancellation), links between documents, permissions
├── sales/  payment/                SalesDocumentTest (calculation + transitions, no Spring) · Service · Controller
├── audit/  branch/  company/  role/  user/   Service (Mockito) and controller tests
```

`src/test/resources/application.properties` redirects every test to `smartbusiness_test`, recreated on
every run — the development database is never touched by `mvn test`.

### Backend rules

- Each feature has exactly 7 files: Entity, Repository, Service, Controller, Mapper, Request, Response
- No extra layers (Facade, Handler, UseCase...)
- Every entity extends `BaseEntity`
- URLs follow the REST pattern: `GET /api/customers`, `POST /api/customers`, etc.
- A service only injects **its own** repository; for another feature it goes through that feature's
  service (`CompanyService.currentReference()`, `RoleService.resolveAssignable()`,
  `BranchService.getAssignable()`). Direction of dependencies: `user → role / branch → company`, never
  the other way around (`RoleRepository.countHolders` counts users without depending on `user/`)
- A company's default values (roles, taxes, sequences, main branch) are created **at registration
  only** — no sweep over every company at startup. The one exception is `RoleService.syncSystemRoles`,
  which reapplies the `SystemRole` enum
- Errors always bubble up through `GlobalExceptionHandler` → `ErrorResponse`
- Database migrations are versioned under `src/main/resources/db/migration/`

### Flyway migration naming convention

```
V1__init_schema.sql
V2__create_customers_table.sql
V3__create_products_table.sql
V4__create_sales_orders_table.sql
...
```

---

## Frontend

### Folder structure

```
src/
├── environments/
│   └── environment.ts              Backend apiUrl
│
└── app/
    ├── theme.ts                    PrimeNG preset (colors, radius, density)
    ├── app.config.ts               Providers: router, http, PrimeNG, Message/Confirmation
    ├── app.routes.ts               Routes + data.breadcrumb
    │
    ├── core/                       Global services and interceptors
    │   ├── auth/                           ✅ Session and access control
    │   │   ├── auth.service.ts             Session as a signal, has(), login/logout
    │   │   ├── auth.interceptor.ts         Bearer + 401 → clean sign-out
    │   │   ├── auth.guard.ts               authGuard + permissionGuard
    │   │   └── session.model.ts            SessionUser, Permission, BusinessModule (typed unions)
    │   ├── platform-auth/                  ✅ Platform session — sealed off from auth/
    │   │   ├── platform-auth.service.ts    A separate token (smartbusiness.platform.token)
    │   │   ├── platform-auth.interceptor.ts Only acts on /platform/ URLs
    │   │   ├── platform-auth.guard.ts
    │   │   └── platform-session.model.ts
    │   ├── interceptors/
    │   │   └── http-error.interceptor.ts   HTTP errors → a readable toast (401 excluded)
    │   ├── models/
    │   │   └── page.model.ts               Spring Data's Page<T>
    │   └── services/
    │       ├── notification.service.ts     PrimeNG Toast wrapper
    │       ├── platform-company.service.ts Calls to /api/platform/companies
    │       └── user.service.ts             Calls to /api/users
    │
    ├── shared/                     Components reused across the whole ERP
    │   ├── components/
    │   │   ├── page-header/        Title + description + actions slot
    │   │   └── bar-chart/          Bars in plain HTML/CSS (no charting library): relative heights, value on hover
    │   └── directives/
    │       └── has-permission.directive.ts   *appHasPermission="'USER_CREATE'"
    │
    ├── layout/                     The application's shell
    │   ├── layout.service.ts       Sidebar state (signals + localStorage)
    │   ├── navigation.ts           Navigation model — NavItem.permission + .module
    │   ├── main-layout/            Wrapper: sidebar + topbar + router-outlet
    │   ├── sidebar/                Custom navigation (expanded / rail / overlay)
    │   └── header/                 Topbar: breadcrumbs, search, notifications, user
    │
    └── features/                   A feature is a folder
        ├── auth/                   ✅ Outside MainLayout (no sidebar, no topbar)
        │   ├── login/
        │   └── register/           Company sign-up + first administrator
        ├── audit/                  ✅ Security log (action / area / period filters)
        ├── branches/               ✅ Company sites
        ├── dashboard/              ✅ Home page: per domain (sales, purchases, stock) a row of cards, a 6-month
        │                           chart and a table (overdue invoices, products at their minimum); each domain
        │                           is only requested — and shown — when the person has that module's right
        ├── roles/                  ✅ Roles + permission matrix
        │   ├── roles.component.*   List (Standard / Custom)
        │   ├── role.model.ts
        │   ├── role-form/          Create / edit dialog
        │   └── permission-matrix/  Grid generated from the backend's catalogue
        ├── users/                  ✅ Full module — reference model
        │   ├── users.component.*   List
        │   ├── user.model.ts       Types + options
        │   ├── user-form/          Create / edit dialog
        │   └── user-detail/        Overview / History drawer
        ├── stock/                  ✅ Inventory + movement ledger
        │   ├── stock.model.ts                 Types, movement-type labels and severities
        │   ├── stock.component.*              Inventory: in stock / reserved / available / minimum, "low stock" filter
        │   ├── stock-movements.component.*    Ledger (product / warehouse / type filters; link to the source order)
        │   └── stock-movement-form/           ONE dialog: entry, exit, adjustment (counted quantity), transfer
        ├── settings/warehouses/    ✅ List + dialog (mirrors brands/)
        ├── print/                  ✅ Printing — ONE A4 sheet for every document type (Finco-style)
        │   ├── printable-document.model.ts    PrintableDocument (shared shape), WORDING (title, "Arrêté…" phrase,
        │   │                                  due-date label, party label — ONE row per type), fromSalesDocument / fromPurchaseDocument
        │   ├── amount-in-words.ts             Amount spelled out in French (dinars / millimes), a tested legal wording
        │   ├── printable-document/            The sheet: header (logo, identity, tax ID), title + number + dates, party, lines,
        │   │                                  totals + VAT recap, amount in words, notes, terms, bank details, stamp, legal footer
        │   ├── document-print.component.*     Preview page OUTSIDE the layout (/print/sales/:id, /print/purchases/:id): toolbar
        │   │                                  (prices, reference column) + "Print / Save as PDF"; the tab title = the document number
        │   ├── pdf-export.service.ts          PdfExportService: element → a PDF file (html2pdf.js, loaded on demand),
        │   │                                  downloaded as "<number> - <TITLE>.pdf" without going through the print dialog
        │   └── print-link.ts                  openPrintPage(): opens the preview in a new tab
        ├── purchases/              ✅ Purchase orders + goods receipts + purchase invoices — mirrors sales/
        │   ├── purchase-document.model.ts     Types, PURCHASE_CONFIGS, statuses (Draft / Validated / Cancelled)
        │   ├── purchase-document-actions.ts   The life-cycle STEPS of a document (validate, create the receipt, cancel), described ONCE:
        │   │                                  label, right, confirmation, server call, message — shared by the list and the page
        │   ├── purchase-documents.component.* List (search, status filter); the row menu carries the steps + deleting a draft
        │   └── purchase-document-editor/      Page: form + lines + LIVE totals via /preview; a goods receipt
        │                                      picks its warehouse (the default pre-selected); "Create receipt" on a validated order
        ├── payments/               ✅ An invoice's payments
        │   ├── payment.model.ts               Types, payment methods (labels)
        │   ├── payment-form/                  "Add payment" dialog: amount (capped at what remains due, open on the balance), date,
        │   │                                  method, reference, notes; used by both the invoice list AND the invoice page
        │   │                                  (`family` = customer | supplier: the same dialog and panel serve both payments
        │   │                                  received from customers and payments made to suppliers — service, SALE_* / PURCHASE_* rights and labels follow)
        │   └── invoice-payments/              Panel under an issued invoice: Total / Credited / Paid / Balance ("To refund" if negative, from the server), a payments table
        │                                      (cancelled ones included, struck through), "Add payment", a confirmed cancellation; emits `changed` to reload the invoice
        ├── sales/                  ✅ Quotes + orders + delivery notes + invoices — one set of components for all four types
        │   ├── sales-document.model.ts        Types, DOCUMENT_CONFIGS (label, route, statuses), status labels
        │   ├── sales-document-actions.ts      The life-cycle STEPS of a document (issue, accept, reject, put back to issued, confirm,
        │   │                                  convert, cancel), described ONCE — shared by the list and the page
        │   ├── sales-documents.component.*    List (search, status filter); the row menu carries the steps + deleting a draft
        │   └── sales-document-editor/         Page: form + lines (FormArray) + LIVE totals via /preview;
        │                                      read-only once issued, workflow buttons depending on status;
        │                                      a delivery note or invoice picks its source warehouse (the default pre-selected);
        │                                      an issued invoice shows its payments panel; the invoice list shows the balance;
        │                                      a credit note keeps its invoice's customer (locked field), with no warehouse and no "New" button
        ├── settings/company/       ✅ Company profile (logo, stamp, currency, tax ID...)
        │   └── image-upload/       Shared logo/stamp component (preview + upload + delete)
        ├── platform/               ✅ Platform portal — /platform/** routes, no sidebar
        │   ├── login/              A dedicated sign-in page, no sign-up
        │   ├── platform-layout/    Minimal shell (topbar + sign-out)
        │   └── companies/          Company list + a checkbox per module
        │       └── company-modules-dialog/
        └── …                       The other modules follow the same pattern
```

**Where to put what:**

| What | Where |
|---|---|
| A feature's HTTP calls | `core/services/{feature}.service.ts` |
| A feature's types | `features/{feature}/{feature}.model.ts` |
| A component used by 2+ features | `shared/components/` |
| A component used by a single feature | inside that feature's folder |

### Routing

```
/login           → LoginComponent       (public, outside the layout)
/register        → RegisterComponent    (public, outside the layout)
/                → redirects to /dashboard
/dashboard       → DashboardComponent   (lazy)
/users             → UsersComponent          (lazy, permission USER_VIEW)
/roles             → RolesComponent          (lazy, permission ROLE_VIEW)
/customers         → CustomersComponent      (lazy, permission CUSTOMER_VIEW, module CUSTOMERS)
/stock             → StockComponent (inventory)  · /stock/movements → StockMovementsComponent  (permission STOCK_VIEW)
/settings/warehouses → WarehousesComponent (permission STOCK_VIEW ; writes need STOCK_ADJUST)
/quotes            → SalesDocumentsComponent (list)         · /quotes/new · /quotes/:id  → SalesDocumentEditorComponent
/sales-orders      → SalesDocumentsComponent (list)         · /sales-orders/new · /sales-orders/:id
/delivery-notes    → SalesDocumentsComponent (list)         · /delivery-notes/new · /delivery-notes/:id
/invoices          → SalesDocumentsComponent (list)         · /invoices/new · /invoices/:id
/credit-notes      → SalesDocumentsComponent (list, no "New": a credit note is created from an invoice) · /credit-notes/:id
/return-notes      → SalesDocumentsComponent (list)         · /return-notes/new · /return-notes/:id
                     (same components, `data.documentType` says QUOTE, SALES_ORDER, DELIVERY_NOTE, INVOICE or CREDIT_NOTE ; permissions SALE_*)
/purchase-orders   → PurchaseDocumentsComponent (list)      · /purchase-orders/new · /purchase-orders/:id
/print/sales/:id · /print/purchases/:id → DocumentPrintComponent  (OUTSIDE MainLayout: no sidebar, no topbar;
                     authGuard + permissionGuard SALE_VIEW / PURCHASE_VIEW; opened in a new tab from the editor or the list)
/goods-receipts    → PurchaseDocumentsComponent (list)      · /goods-receipts/new · /goods-receipts/:id
/purchase-invoices → PurchaseDocumentsComponent (list)      · /purchase-invoices/new · /purchase-invoices/:id
/purchase-credit-notes → PurchaseDocumentsComponent (list, no "New": a credit note is created from an invoice) · /purchase-credit-notes/:id
/purchase-return-notes → PurchaseDocumentsComponent (list)  · /purchase-return-notes/new · /purchase-return-notes/:id
                     (same components, `data.documentType` says PURCHASE_ORDER or GOODS_RECEIPT ; permissions PURCHASE_*)
/branches          → BranchesComponent       (lazy, permission BRANCH_VIEW)
/audit             → AuditComponent          (lazy, permission AUDIT_VIEW)
/settings          → SettingsComponent       (a hub — no permission of its own, each tile filters itself)
/settings/profile  → ProfileComponent        (personal account: name, phone, password — any user)
/settings/company       → CompanySettingsComponent (lazy, permission COMPANY_VIEW)
/settings/bank-accounts → BankAccountsComponent    (lazy, permission COMPANY_VIEW)
/settings/taxes         → TaxesComponent           (lazy, permission COMPANY_VIEW)
/settings/numbering     → NumberingComponent       (lazy, permission COMPANY_VIEW)
…                       → the other modules will follow the same pattern
```

`/settings` is a parent route with no component of its own (`breadcrumb: 'Settings'`): the `''` index
forces `breadcrumb: ''` so the crumb isn't duplicated, and each child adds its own. The personal account
screen relies on `PATCH /api/auth/me` (first name, last name, phone — the email stays the sign-in
identifier) and `PATCH /api/auth/change-password`, both already exposed by `auth`.

Application routes go through `MainLayoutComponent` (sidebar + topbar), guarded by `authGuard`. A route
that requires a specific right adds `permissionGuard` and its permission:

```typescript
{
  path: 'users',
  canActivate: [permissionGuard],
  data: { breadcrumb: 'Users', permission: 'USER_VIEW' },
  loadComponent: () => import('./features/users/users.component').then(m => m.UsersComponent)
}
```
Every route declares its breadcrumb:

```typescript
{
  path: 'users',
  data: { breadcrumb: 'Users' },
  loadComponent: () => import('./features/users/users.component').then(m => m.UsersComponent)
}
```

### Visual layout

```
┌─────────────────────────────────────────────────────┐
│  SIDEBAR (260px fixed)  │  HEADER (56px fixed)        │
│                        ├────────────────────────────┤
│  SmartBusiness         │                            │
│  ─────────────         │   CONTENT (router-outlet)  │
│  > Dashboard           │   scrollable               │
│  > Sales          ▼    │                            │
│    Sales Orders        │                            │
│    Invoices            │                            │
│  > Purchases      ▼    │                            │
│  > Inventory      ▼    │                            │
│  > Partners       ▼    │                            │
│                        │                            │
│  v1.0.0                │                            │
└────────────────────────┴────────────────────────────┘
```

---

## Database

### Configuration

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/smartbusiness
spring.jpa.hibernate.ddl-auto=validate     # Flyway owns the schema, not Hibernate
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
```

### Common fields (BaseEntity)

Every table automatically has:
```sql
created_at  TIMESTAMP NOT NULL
updated_at  TIMESTAMP NOT NULL
version     BIGINT    NOT NULL DEFAULT 0   -- @Version : optimistic lock
```

### Applied migrations

| Version | Content |
|---|---|
| `V1__init_schema.sql` | Initial placeholder |
| `V2__create_users_table.sql` | `users` + `user_history` (status/role indexes, cascading FK) |
| `V3__create_companies_and_branches.sql` | `companies` + `branches` |
| `V4__create_roles_and_permissions.sql` | `roles` + `role_permissions` |
| `V5__link_users_to_company_and_roles.sql` | `users.company_id`, `user_roles`, purges accounts with no company |
| `V6__create_audit_logs.sql` | `audit_logs` + `DROP TABLE user_history` |
| `V7__link_users_to_branch.sql` | `users.branch_id` (nullable — organisational, not a permission) |
| `V8__add_company_profile_fields.sql` | `companies.tax_id/currency/postal_code/city/logo_path/stamp_path/...` |
| `V9__create_platform_admins_and_company_modules.sql` | `platform_admins` + `company_modules` (backfill: everything enabled for existing companies) |
| `V10__create_company_bank_accounts.sql` | `company_bank_accounts` (the company's bank accounts, shown on documents) |
| `V11__create_taxes.sql` | `taxes` (VAT, FODEC, stamp duty… — the Tunisian set seeded by `TaxService`, not the migration) |
| `V12__create_numbering_sequences.sql` | `numbering_sequences` (one sequence per `DocumentType` and per company, seeded by `NumberingService`) |
| `V13__create_customers.sql` | `customers` (COMPANY/INDIVIDUAL, reference unique per company) |
| `V14__customer_type_identifiers_and_addresses.sql` | splits `fiscal_id` → `tax_id` / `national_id` + `birth_date`, adds `vat_suspension_number` and the `billing_*` / `shipping_*` addresses |
| `V15__optimistic_locking_and_case_insensitive_uniqueness.sql` | a `version` column on all 9 `BaseEntity` tables; unique constraints replaced with `LOWER(...)` indexes; `customers.reference` → 30 chars ; a `CUSTOMER` sequence created for every existing company |
| `V16__create_suppliers.sql` | `suppliers` (mirrors `customers` V13+V14+V15); no sequence to create — `SUPPLIER` is allocated on the fly by `NumberingService`, no company has a supplier yet |
| `V17__create_catalogue.sql` | `categories` (self-referencing), `brands`, `products`, `product_taxes` (N-N join to `taxes`); all with `version` and `LOWER(...)` indexes from the start |
| `V18__add_product_image.sql` | `products.image_path` / `image_content_type` (a single photo — replaced by V19) |
| `V19__create_product_images.sql` | `product_images` (up to 4 photos per product); an already-uploaded photo is kept as-is; V18's columns dropped |
| `V22__create_purchase_documents.sql` | `purchase_documents` (mirrors V20 + `supplier_id`, `warehouse_id` for a goods receipt), `purchase_document_lines`, `purchase_document_taxes` ; unique `LOWER(reference)` index per company and type |
| `V21__create_stock.sql` | `warehouses` (`LOWER(name)` index, a single `is_default` per company, a default warehouse inserted for every existing company), `stock_movements` (append-only, signed quantity), `products.min_stock` |
| `V23__delivery_notes.sql` | `sales_documents.warehouse_id` (delivery note) ; `stock_movements.origin_id` + a `(source_type, origin_id)` index |
| `V27__supplier_credit_notes.sql` | `purchase_documents.credited_amount` (sum of a purchase invoice's validated credit notes, rewritten, never incremented) |
| `V26__purchase_invoices_and_supplier_payments.sql` | `purchase_documents.paid_amount` ; `supplier_payments` (mirrors `payments`: an ACTIVE/CANCELLED register, amount > 0) |
| `V25__credit_notes.sql` | `sales_documents.credited_amount` (sum of an invoice's issued credit notes, rewritten, never incremented) |
| `V24__invoices_and_payments.sql` | `sales_documents.paid_amount` ; `payments` (register: amount > 0, date, method, reference, status ACTIVE/CANCELLED) |
| `V20__create_sales_documents.sql` | `sales_documents` (type + status, reference NULL while a draft, self-referencing `source_id`), `sales_document_lines`, `sales_document_taxes` ; unique `LOWER(reference)` index per company and type |

### Planned schema (simplified)

```
users ──< audit_logs           ✅ done
customers                      ✅ done
company_bank_accounts · taxes · numbering_sequences   ✅ done

customers ✅        suppliers ✅
    │                  │
  sales_documents ✅  purchase_orders
    │                  │
  sales_document_lines ✅  purchase_items
    └──────┬───────────┘
         products ✅ (+ categories ✅ · brands ✅)
           │
         stock_movements
```

---

## Frontend ↔ Backend communication

- Every API request goes through `HttpClient` with base URL `http://localhost:8080`
- The `httpErrorInterceptor` automatically shows a toast on HTTP errors
- Angular services (`core/services/`) wrap the HTTP calls
- The error response shape is always `ErrorResponse { status, message, errors[] }`

# ROADMAP.md — Fonctionnalités et progression

## Légende

| Symbole | Signification |
|---|---|
| ✅ | Terminé |
| 🔲 | À faire |
| 🚧 | En cours |

---

## Phase 1 — Fondation ✅

Objectif : mettre en place l'architecture propre avant toute feature métier.

| Tâche | Statut |
|---|---|
| Structure backend (config, exception, common, security) | ✅ |
| Dépendances Maven (Web, JPA, PostgreSQL, Flyway, Lombok, MapStruct) | ✅ |
| Configuration PostgreSQL + Flyway | ✅ |
| Fix SSL Maven (`.mvn/jvm.config`) | ✅ |
| Structure frontend (core, shared, layout, features) | ✅ |
| Installation et configuration PrimeNG (thème Aura) | ✅ |
| Layout ERP (sidebar + header + main-layout) | ✅ |
| Routing avec lazy loading | ✅ |
| NotificationService + httpErrorInterceptor | ✅ |
| Composant page-header partagé | ✅ |
| Dashboard placeholder | ✅ |
| Documentation (CLAUDE.md, README, docs/) | ✅ |

---

## Phase 1.5 — Shell ERP professionnel ✅

| Tâche | Statut |
|---|---|
| Thème custom (preset Deep Indigo, densité ERP) | ✅ |
| Sidebar 3 modes : étendu / rail 64px / overlay | ✅ |
| Navigation groupée + état actif + tooltips en mode rail | ✅ |
| Topbar : breadcrumbs, recherche `Ctrl+K`, notifications, menu utilisateur | ✅ |
| Persistance de l'état sidebar (localStorage) | ✅ |
| Responsive desktop / laptop / tablette / mobile | ✅ |

---

## Phase 2 — Multi-entreprise, authentification et RBAC 🚧

Objectif : isoler les entreprises et sécuriser l'application.
Conception détaillée : [`docs/RBAC.md`](RBAC.md).

### Tranche A — Socle multi-entreprise + authentification ✅

| Tâche | Statut |
|---|---|
| Entités `Company`, `Branch`, `Role` + `user_roles` | ✅ |
| Enums `Permission` (41), `PermissionModule`, `SystemRole` (7) | ✅ |
| Migrations `V3`, `V4`, `V5` | ✅ |
| Spring Security + filtre JWT + `@EnableMethodSecurity` | ✅ |
| `/api/auth/register` — entreprise + rôles système + branche + admin | ✅ |
| `/api/auth/login`, `/api/auth/me`, `/api/auth/change-password` | ✅ |
| `CurrentUser` + isolation `companyId` dans tous les repositories | ✅ |
| Module `user` multi-rôles, `@PreAuthorize` sur chaque endpoint | ✅ |
| Règles anti-escalade (rôles propres, auto-désactivation, dernier admin) | ✅ |
| Remplir `lastLoginAt` et `performedBy` | ✅ |
| Tests : `UserServiceTest` (19) + `UserControllerTest` (7) | ✅ |

### Tranche B — Rôles & branches (backend) ✅

| Tâche | Statut |
|---|---|
| CRUD `Role` + catalogue `GET /api/roles/permissions` (38 permissions, 11 modules) | ✅ |
| Nom stable dérivé du libellé, accents retirés | ✅ |
| Protection des rôles système (ni modifiables ni supprimables) | ✅ |
| Règle « accorder seulement ce que l'on possède » à la création et à l'édition | ✅ |
| Suppression bloquée tant qu'un utilisateur porte le rôle (`userCount`) | ✅ |
| CRUD `Branch` + activation/désactivation + dernière branche protégée | ✅ |
| `users.branch_id` (nullable, organisationnel, jamais une permission) — `V7` | ✅ |
| `GET`/`PUT` `/api/company` (le tenant ne peut pas changer son `status`) | ✅ |
| Corps JSON invalide → 400 au lieu de 500 | ✅ |
| Tests : `RoleServiceTest` (13) + `BranchServiceTest` (6) | ✅ |

### Tranche C — Frontend authentification ✅

| Tâche | Statut |
|---|---|
| Page de login (erreur affichée dans le formulaire, pas en toast) | ✅ |
| Page d'inscription entreprise (confirmation du mot de passe) | ✅ |
| `authInterceptor` (Bearer + 401 → déconnexion propre) | ✅ |
| `authGuard` (+ `redirectTo`) et `permissionGuard` (`data.permission`) | ✅ |
| Session en signal, `AuthService.has()`, initiales, logout | ✅ |
| Restauration de session au démarrage (`provideAppInitializer` → `/auth/me`) | ✅ |
| Nom d'utilisateur, rôles et entreprise affichés dans le shell | ✅ |
| `p-toast` déplacé dans `AppComponent` (survit à une redirection hors layout) | ✅ |
| Tests frontend : 17 SUCCESS | ✅ |

### Tranche D — Frontend administration ✅

| Tâche | Statut |
|---|---|
| Écran Rôles (type Standard/Custom, nb de permissions, nb d'utilisateurs) | ✅ |
| Composant `app-permission-matrix` construit depuis le catalogue backend | ✅ |
| Formulaire de rôle (création, édition, rôle système en lecture seule) | ✅ |
| Formulaire utilisateur multi-rôles (`p-multiselect`, chips) | ✅ |
| Sélecteur de branche (organisationnel) dans le formulaire + colonne liste + détail | ✅ |
| Verrou visuel sur ses propres rôles | ✅ |
| Filtre par rôle alimenté par l'API, chips de rôles dans la liste | ✅ |
| Suppression d'utilisateur retirée (l'endpoint n'existe plus) | ✅ |
| Écran Branches (CRUD + activation/désactivation) | ✅ |
| Menu dynamique filtré par permission (groupe vide → masqué) | ✅ |
| Directive `*appHasPermission` sur les actions | ✅ |
| Tests frontend : 19 SUCCESS | ✅ |

### Tranche E — Audit ✅

| Tâche | Statut |
|---|---|
| Migration `V6` : `audit_logs` + `DROP TABLE user_history` | ✅ |
| `AuditAction` (17 événements), `AuditEntity`, `AuditService` | ✅ |
| Enregistrement dans Auth, User, Role, Branch et Company | ✅ |
| `LOGIN_FAILED` écrit en transaction séparée (survit au refus) | ✅ |
| Adresse IP (`X-Forwarded-For` prioritaire) | ✅ |
| `GET /api/audit-logs` filtrable (action, domaine, période) — `AUDIT_VIEW` | ✅ |
| `GET /api/users/{id}/history` bascule sur l'audit — reste en `USER_VIEW` | ✅ |
| Écran de consultation + entrée de menu | ✅ |
| Timeline du drawer utilisateur alimentée par l'audit | ✅ |
| Suppression de `UserHistory`, `UserEvent` et leurs DTO | ✅ |
| Tests : `AuditServiceTest` (5) — total backend 51, frontend 19 | ✅ |

### Tranche F — Vérification ✅

| Tâche | Statut |
|---|---|
| Base de test dédiée, recréée à chaque run (`IntegrationTest`) | ✅ |
| `CompanyIsolationTest` — 17 tests d'accès croisés | ✅ |
| `AuthorizationTest` — 12 tests (401 anonyme, 403 par permission, révocation immédiate) | ✅ |
| `PrivilegeEscalationTest` — 12 tests anti-escalade et anti-verrouillage | ✅ |
| Suite vérifiée par régression volontaire (6 échecs sur une ligne cassée) | ✅ |
| **Total : 92 tests backend, 19 frontend** | ✅ |

### Tranche G — Rattachement branche, profil entreprise, plateforme ✅

Ajouts après la clôture initiale du RBAC — hors des six tranches d'origine.

| Tâche | Statut |
|---|---|
| `users.branch_id` (organisationnel, jamais une permission) — `V7` | ✅ |
| Profil entreprise étendu : matricule fiscale, devise, adresse, logo, cachet — `V8` | ✅ |
| Logo/cachet stockés sur disque (`smartbusiness.uploads.dir`), servis en base64 | ✅ |
| Écran Angular `/settings/company` | ✅ |
| `PlatformAdmin` — compte hors modèle tenant, token et interceptor Angular séparés | ✅ |
| `company_modules` — modules métier activables par entreprise (`V9`) | ✅ |
| `PlatformAdminBootstrap` — premier compte créé au démarrage depuis l'environnement | ✅ |
| Portail Angular `/platform/**` (connexion, liste des entreprises, bascule de modules) | ✅ |
| `PlatformAuthorizationTest` — 9 tests d'étanchéité entre les deux mondes de token | ✅ |
| **Total : 119 tests backend** (frontend non testé sur cette tranche, à la demande) | ✅ |

---

## Phase 1.6 — Gestion des utilisateurs ✅

Premier module métier complet.

### Backend
| Tâche | Statut |
|---|---|
| Entités `User` + `UserHistory` | ✅ |
| Enums `UserRole`, `UserStatus`, `UserEvent` | ✅ |
| CRUD complet + activate / deactivate / reset-password | ✅ |
| Recherche + filtres rôle/statut + tri + pagination | ✅ |
| Validation (unicité username/email, format, contraintes) | ✅ |
| Historique automatique écrit par le service | ✅ |
| Migration `V2__create_users_table.sql` (index + FK cascade) | ✅ |
| Tests : `UserServiceTest` (15) + `UserControllerTest` (7) | ✅ |

### Frontend
| Tâche | Statut |
|---|---|
| Page liste avec `p-table` (lazy, tri serveur, pagination) | ✅ |
| Recherche globale avec debounce 350 ms | ✅ |
| Filtres rôle + statut + bouton "clear filters" | ✅ |
| Menu d'actions contextuel par ligne | ✅ |
| Formulaire création / édition (dialog, sections, validation inline) | ✅ |
| Drawer détail avec onglets Overview / History | ✅ |
| Timeline d'historique avec icônes et couleurs par événement | ✅ |
| Activation / désactivation avec confirmation | ✅ |
| Reset password | ✅ |
| Empty states distincts (aucun user / aucun résultat) | ✅ |
| Tests : `UserFormComponent` (11) + `LayoutService` (4) | ✅ |

---

## Phase 2.5 — Nettoyage administration & paramètres ✅

Objectif : consolider la zone Paramètres avant d'y ajouter la configuration métier
(numérotation, taxes, comptes bancaires). Comparaison de référence : `docs/` + analyse Finco.

### Tranche A — Hub Paramètres + compte personnel ✅

| Tâche | Statut |
|---|---|
| `PATCH /api/auth/me` — l'utilisateur modifie son prénom, nom, téléphone (pas l'email) | ✅ |
| `firstName` / `lastName` / `phone` ajoutés à `SessionResponse` / `SessionUser` | ✅ |
| Page hub `/settings` — tuiles filtrées par permission, tuiles « Soon » pour la suite | ✅ |
| Page `/settings/profile` — identité + changement de mot de passe (`p-password`) | ✅ |
| Routes `settings` imbriquées, fil d'Ariane `Settings > …` | ✅ |
| Menu utilisateur de la topbar câblé (My profile, Settings) — entrées mortes retirées | ✅ |
| `navigation.ts` : « Settings » pointe vers le hub, sans permission | ✅ |
| Tests : `SelfServiceProfileIntegrationTest` (4) — total backend 123, frontend 21 | ✅ |

### Tranche B — Configuration métier (comptes bancaires, taxes, numérotation) ✅

Permissions : **réutilisation de `COMPANY_VIEW` / `COMPANY_UPDATE`** (config d'entreprise,
même bucket que le profil — pas de nouvelles permissions tant qu'un besoin de découpage
n'est pas exprimé).

**Comptes bancaires**
| Tâche | Statut |
|---|---|
| `CompanyBankAccount` (libellé, banque, RIB/IBAN, devise, affiché sur documents) — feature `bankaccount/`, `V10` | ✅ |
| `GET/POST/PUT/DELETE /api/settings/bank-accounts` (`COMPANY_VIEW` / `COMPANY_UPDATE`) | ✅ |
| Audit `BANK_ACCOUNT_CREATED/UPDATED/DELETED` + `AuditEntity.BANK_ACCOUNT` | ✅ |
| Écran `/settings/bank-accounts` (liste + dialog + empty state) + tuile du hub | ✅ |
| Tests : `CompanyBankAccountServiceTest` (3) + `CompanyIsolationTest` (+3) | ✅ |

**Taxes**
| Tâche | Statut |
|---|---|
| `Tax` + `TaxKind` (`VAT_RATE` / `PERCENTAGE_SURCHARGE` / `FIXED_PER_DOCUMENT`), `includedInVatBase`, `appliesToLine` dérivé, drapeau `system` — feature `tax/`, `V11` | ✅ |
| Seed du set tunisien par entreprise (à l'inscription ; le rattrapage au démarrage a été retiré en Phase 3.0) : TVA 19/13/7/0 %, FODEC 1 %, Timbre 1,000 — éditable, taxe système non supprimable, type immuable | ✅ |
| `GET/POST/PUT/DELETE /api/settings/taxes` + audit `TAX_*` | ✅ |
| Écran `/settings/taxes` (liste + `tax-form` à champs conditionnels) + tuile du hub | ✅ |
| Tests : `TaxServiceTest` (7) + `CompanyIsolationTest` (+3) | ✅ |

**Numérotation**
| Tâche | Statut |
|---|---|
| `NumberingSequence` (préfixe, longueur, année, `nextValue`, `yearOfLast`) + enum `DocumentType` (6 valeurs) — feature `numbering/`, `V12` | ✅ |
| `NumberingService.allocate()` — verrou pessimiste, remise à zéro annuelle, appelé à la validation d'un document (Phase 4+) ; matérialisation à l'inscription (type manquant créé au premier usage) | ✅ |
| `GET /api/settings/numbering`, `PUT /api/settings/numbering/{documentType}` + audit `NUMBERING_UPDATED` | ✅ |
| `MethodArgumentTypeMismatchException` → 400 dans `GlobalExceptionHandler` (enum de path invalide) | ✅ |
| Écran `/settings/numbering` (liste + `numbering-form` avec aperçu live) + tuile du hub | ✅ |
| Tests : `NumberingServiceTest` (6) + `CompanyIsolationTest` (+2) | ✅ |

**Total backend : 146 tests · frontend : 21.**

---

## Phase 3 — Référentiels 🚧

Objectif : gérer les données de base (clients, fournisseurs, produits).

### Consolidation technique (Phase 3.0) ✅

Faite avant Fournisseurs, pour que les modules suivants ne recopient pas les défauts de Clients.

| Tâche | Statut |
|---|---|
| Identifiants plateforme par défaut retirés de `application.properties` | ✅ |
| `DataIntegrityViolationException` → 409 (unique `23505`, clé étrangère `23503`), le reste en 500 | ✅ |
| `@Version` dans `BaseEntity` + `OptimisticLockingFailureException` → 409 — `V15` | ✅ |
| Contraintes uniques remplacées par des index `LOWER(...)` (email, username, code branche, rôle, taxe, référence client, email plateforme) — `V15` | ✅ |
| Référence client via `NumberingService.allocate(DocumentType.CUSTOMER)` (plus de `count + 1`) ; séquence semée pour l'existant | ✅ |
| `DocumentType` porte `defaultPadding` / `defaultIncludeYear` ; logique d'allocation déplacée dans `NumberingSequence.allocate(year)` | ✅ |
| `GET /api/settings/numbering` en lecture seule ; balayages `@EventListener` de `TaxService` / `NumberingService` retirés | ✅ |
| Couches : `CompanyService.currentReference()` / `register()`, `BranchService.getAssignable()` / `createMain()`, `RoleService.resolveAssignable()` / `getSystemRole()` ; seul `PlatformCompanyService` garde `CompanyRepository` | ✅ |
| Cycle `role → user` cassé : `RoleRepository.countHolders` remplace `UserRepository.countByRolesId` | ✅ |
| `common/SearchPattern` + `common/BaseMapperConfig` (tous les mappers) | ✅ |
| Tests : `GlobalExceptionHandlerTest` (4) + `DataConsistencyIntegrationTest` (3) + `RoleServiceTest` (+3) + `BranchServiceTest` (+1) + `NumberingServiceTest` (+2) — règles de rôle/branche déplacées de `UserServiceTest` | ✅ |

**Total : 178 tests backend.**

### Clients ✅

Cadré d'après l'analyse Finco (§ Clients — spécification « Création — champs observés »).

| Tâche | Statut |
|---|---|
| `Customer` + `CustomerType` (`COMPANY` / `INDIVIDUAL`, une seule table) — feature `customer/`, `V13` + `V14` | ✅ |
| Référence unique par entreprise, générée (`C-0001`) si laissée vide, sinon reprise et validée | ✅ |
| Identifiants pilotés par le type : `taxId` (matricule, entreprise) · `nationalId` (CIN) + `birthDate` (particulier) | ✅ |
| `Address` (`@Embeddable`, value object) × 2 : `billingAddress` + `shippingAddress` (rue, ville, région, code postal, pays) | ✅ |
| `vatSuspensionNumber` — permis de suspension de TVA | ✅ |
| Note privée | ✅ |
| `GET /api/customers` (recherche + filtre type + pagination/tri) · `GET/{id}` · `POST` · `PUT` · `DELETE` — permissions `CUSTOMER_*` | ✅ |
| Page `/customers` (p-table lazy, recherche debounce, filtre type, menu de ligne, 2 empty states) | ✅ |
| `customer-form` (dialog, bascule Business/Individual → libellés + champs ; `p-datepicker` date de naissance ; adresses billing/shipping avec « même que facturation ») | ✅ |
| Suppression `p-confirmDialog` (permanente) — Phase 4 : bloquer si le client porte des documents | ✅ |
| Entrée de menu « Customers » activée (permission `CUSTOMER_VIEW` + module `CUSTOMERS`) | ✅ |
| Tests : `CustomerServiceTest` (7) + `CustomerControllerTest` (6) + `CustomerIntegrationTest` (5) + `CompanyIsolationTest` (+3) + `customer-form.spec` (13) | ✅ |

**Différé (dépend d'un module absent, pas de câblage à vide) :**
- **Liste de prix par défaut** → aucun module « Listes de prix » n'existe encore.
- **Solde de départ + relevé de compte** → aucun grand-livre client (dépend de la facturation, Phase 7).
- **Pièces jointes** → pas de système de pièces jointes générique (seuls logo/cachet, 1 image).
- **Comptes bancaires du client** → faisable seul (miroir de `company_bank_accounts`) — non fait pour l'instant.

**Total après Clients : 167 tests backend · 34 frontend.**

### Fournisseurs ✅

Miroir exact de Clients (§ analyse Finco — même formulaire, référence `F-0001` inversée).

| Tâche | Statut |
|---|---|
| `Supplier` + `SupplierType` (COMPANY/INDIVIDUAL, une seule table) — feature `supplier/`, `V16` | ✅ |
| `Address`/`AddressDto` déplacés vers `common/`, partagés par `customer/` et `supplier/` | ✅ |
| Pas de permis de suspension de TVA (propre au client en franchise, absent chez Finco côté fournisseur) | ✅ |
| Référence unique par entreprise, générée (`F-0001`) via `numberingService.allocate(DocumentType.SUPPLIER)`, sinon reprise et validée | ✅ |
| `GET /api/suppliers` (recherche + filtre type + pagination/tri) · `GET/{id}` · `POST` · `PUT` · `DELETE` — permissions `SUPPLIER_*` (déjà dans l'enum) | ✅ |
| Page `/suppliers` (p-table lazy, recherche debounce, filtre type, menu de ligne, 2 empty states) — miroir de `/customers` | ✅ |
| `supplier-form` (dialog, bascule Business/Individual, adresses billing/shipping avec « même que facturation ») | ✅ |
| Suppression `p-confirmDialog` (permanente) — Phase 5 : bloquer si le fournisseur porte des documents | ✅ |
| Entrée de menu « Suppliers » sortie du groupe « Purchases » (n'était qu'un lien désactivé) — permission `SUPPLIER_VIEW` + module `PURCHASES` | ✅ |
| Tests : `SupplierServiceTest` (7) + `SupplierControllerTest` (6) + `SupplierIntegrationTest` (6) + `CompanyIsolationTest` (+3) + `supplier-form.spec` (13) | ✅ |

**Total après Fournisseurs : 200 tests backend · 47 frontend.**

### Produits + Catégories ✅

Cadré d'après l'analyse Finco (§05 — Articles/services).

| Tâche | Statut |
|---|---|
| `Category` auto-référencée (Famille/Sous-famille), `Brand` — features `category/` et `brand/`, `V17` | ✅ |
| Anti-cycle sur `Category` (une catégorie ne peut pas devenir sa propre descendante) | ✅ |
| Suppression de catégorie/marque bloquée tant qu'un produit ou une sous-catégorie y pointe | ✅ |
| `Product` + `ProductKind` (GOOD/SERVICE) + `ProductPurpose` (SALE/PURCHASE/BOTH) + `ProductUnit` (catalogue fixe, pas d'écran de paramétrage — non observé chez Finco) | ✅ |
| Pas de permis de suspension de TVA ni de champs de variantes (YAGNI, non observés comme configurables) | ✅ |
| Référence unique par entreprise, générée (`P-0001`) via `numberingService.allocate(DocumentType.PRODUCT)` | ✅ |
| `defaultTaxes` (N-N vers `Tax`, table `product_taxes`) résolu via `TaxService.resolveAssignable()` — simple défaut, le calcul de ligne (Phase 4) en prendra un instantané | ✅ |
| `allowNegativeStock` (Finco : « Autoriser Stock Vide », coché par défaut) — champ prêt pour le futur module Stock, sans table `Warehouse` | ✅ |
| **Différé (dépend d'un module absent, pas de câblage à vide) :** seuils de réapprovisionnement par entrepôt (aucun module Entrepôts), listes de prix, pièces jointes, scanner IA, variantes | — |
| `GET /api/products` (recherche + filtre nature + filtre catégorie + pagination/tri) · `GET/{id}` · `POST` · `PUT` · `DELETE` — permissions `PRODUCT_*` (déjà dans l'enum) ; `GET/POST/PUT/DELETE /api/categories` et `/api/brands` sur les mêmes permissions (Finco n'a pas de ligne RBAC séparée pour les référentiels) | ✅ |
| Page `/products` (miroir de `/customers`, filtre nature en plus) ; `/settings/categories` et `/settings/brands` (miroir de `/settings/bank-accounts`, liste + dialog) | ✅ |
| `product-form` : bascule Produit/Service, sélecteurs catégorie/marque/unité, prix vente/achat, multi-sélection des taxes par défaut, case « stock négatif autorisé » masquée pour un service | ✅ |
| Entrée de menu « Products » activée sous Inventory (permission `PRODUCT_VIEW`, module `INVENTORY`) | ✅ |
| Tests : `CategoryServiceTest` (11) + `CategoryControllerTest` (6) + `BrandServiceTest` (6) + `BrandControllerTest` (5) + `ProductServiceTest` (8) + `ProductControllerTest` (6) + `CatalogueIntegrationTest` (8) + `CompanyIsolationTest` (+9) + `product-form.spec` (7) | ✅ |

| Photo produit — `common/ImageStorage` (générique, domaine paramétré, nouveau — `company/CompanyImageStorage` non touché pour ne pas casser les chemins déjà stockés), `V18` (`image_path`/`image_content_type`), `POST`/`DELETE /api/products/{id}/image` | ✅ |
| `ImageUploadComponent` déplacé de `features/settings/company/image-upload/` vers `shared/components/image-upload/` — réutilisé par le formulaire produit | ✅ |
| Photo choisie dès la **création** (aperçu local, envoyée juste après l'enregistrement du produit ; un échec d'upload n'annule pas le produit) + colonne **Photo** (vignette) dans la liste `/products` | ✅ |
| **4 photos par produit** (Finco : galerie) — `ProductImage` + `V19`, limite appliquée par `ProductService`, 1re photo = couverture, `POST/DELETE /api/products/{id}/images[/{imageId}]` ; liste = couverture seule, détail = toutes ; suppression du produit = suppression des fichiers | ✅ |
| Composant partagé `app-image-gallery` (4 emplacements, badge « Cover », validation type/taille) ; à la création les photos sont envoyées **une par une** dans l'ordre choisi ; le formulaire d'édition recharge le produit pour récupérer toutes ses photos | ✅ |
| Tests : `ProductServiceTest` (limite 4, suppression, liste vs détail), `CatalogueIntegrationTest` (écriture disque réelle, 5e photo refusée, suppression d'une photo / du produit), `CompanyIsolationTest` (+1), `image-gallery.spec` (8), `product-form.spec` (+galerie) | ✅ |
| Tests : `ImageStorageTest` (4) + `ProductServiceTest` (+4) + `CatalogueIntegrationTest` (+3, écriture disque réelle) | ✅ |

**Total après Produits + Catégories (+ 4 photos) : 277 tests backend · 69 frontend.**

---

## Phase 4 — Ventes 🚧

Objectif : gérer le cycle de vente. Décision (analyse Finco §06/§15) : **un document commercial
générique** dont le type change, pas une table `sales_orders`. Étape **4a** (devis + commande) faite ;
4b : le bon de livraison est fait (voir plus bas) ; facture, avoir et paiements suivent avec la Phase 7.

| Tâche | Statut |
|---|---|
| Migration `V20` : `sales_documents` + `sales_document_lines` + `sales_document_taxes` (instantanés) | ✅ |
| Modèle générique `SalesDocument` (`QUOTE`, `SALES_ORDER` ; facture/avoir s'ajouteront sans nouvelle table) | ✅ |
| Calcul des totaux, écrit et testé une seule fois (`SalesDocument.recalculate()`) : remise ligne → FODEC (par taux de TVA) → TVA sur base + FODEC → timbre ; HALF_UP 3 décimales ; cas Finco = 37,057 | ✅ |
| `POST /api/sales-documents/preview` : mêmes règles, rien de sauvé — le frontend ne recalcule jamais | ✅ |
| Brouillon sans numéro ; numéro alloué à l'**émission** (`numberingService.allocate`, sans trou) ; document figé ensuite ; suppression = brouillon seulement | ✅ |
| Workflow devis : brouillon → émis → accepté / rejeté (réversible) ; commande : brouillon → émise → confirmée / annulée | ✅ |
| Conversion devis → commande brouillon (`source_id`, lignes et taxes recopiées telles quelles, une commande vivante par devis) ; lien `derived` sur le devis | ✅ |
| Client / produits / taxes résolus par les services de leurs features ; produit « achat seul » refusé ; taxe inactive ou de mauvais type refusée | ✅ |
| Suppression d'un client ou d'un produit présent sur un document bloquée (JPQL `COUNT`, sans dépendance inverse) | ✅ |
| `GET /api/settings/taxes` ouvert aussi à `SALE_CREATE/UPDATE` et `PRODUCT_CREATE/UPDATE` (un commercial doit lire la liste des taxes) ; lecture par id et écritures restent `COMPANY_*` | ✅ |
| `MissingServletRequestParameterException` → 400 (un paramètre obligatoire absent renvoyait 500) | ✅ |
| Pages `/quotes` et `/sales-orders` (liste + éditeur à lignes, totaux live, boutons du workflow) ; menu Sales activé | ✅ |
| Tests : `SalesDocumentTest` (calcul, transitions) · `SalesDocumentServiceTest` · `SalesDocumentControllerTest` · `SalesIntegrationTest` (14) · `CompanyIsolationTest` (+4) · `GlobalExceptionHandlerTest` (+1) · specs éditeur (26) et liste (7) | ✅ |
| **Différé (aucun module en aval, pas de câblage à vide) :** bon de livraison / bon de sortie, facture, avoir, paiements (Phase 7) ; effet sur le stock réservé/retiré (Phase 6) ; remise globale ; mode « taxe incluse » ; devise ; projet ; pièces jointes ; envoi e-mail ; instantané de l'identité du client (prévu avec la facture) ; conversion partielle (quantités reprises ligne à ligne) | — |
| Vérification du module côté serveur : faite globalement (voir « Vérification du module »), une entreprise sans le module obtient 403 | ✅ |

**Total après Ventes 4a (devis + commande) : 359 tests backend · 102 frontend.**

---

## Phase 5 — Achats 🚧

Objectif : gérer les commandes auprès des fournisseurs. Étape **5a** faite — le **miroir de la vente**
(analyse Finco §06 : commande fournisseur et bon de réception n'ont que Brouillon / Validé / Annulé), avec le
lien au stock : valider un bon de réception fait ENTRER les marchandises. La facture d'achat, l'avoir
fournisseur et le bon de retour attendent la Phase 7.

| Tâche | Statut |
|---|---|
| Migration `V22` : `purchase_documents` + `purchase_document_lines` + `purchase_document_taxes` (instantanés) | ✅ |
| Feature `purchase/` : document générique `PURCHASE_ORDER` / `GOODS_RECEIPT`, brouillon sans numéro → numéro à la **validation** → figé | ✅ |
| **Calcul extrait dans `common/DocumentTotals`** (une seule implémentation pour ventes ET achats) ; `SalesDocument` et `PurchaseDocument` lui passent leurs lignes ; `POST /preview` | ✅ |
| Fournisseur résolu par `SupplierService.getAssignable`, produits par `ProductService.resolvePurchasable` (« vente seule » refusé), prix par défaut = prix d'achat | ✅ |
| Bon de réception : entrepôt obligatoire (actif) ; **valider = entrée de stock** (`stockService.receive`, une par bien), même transaction que le numéro | ✅ |
| **Annuler un bon validé = sortie inverse** (`reverseReceipt`) ; refusée si un produit à stock négatif interdit n'a plus les marchandises — le bon reste validé | ✅ |
| Commande validée → bon de réception brouillon (entrepôt par défaut, lignes recopiées, à ajuster) ; **plusieurs réceptions possibles** par commande (livraisons partielles) ; commande à réception vivante non annulable | ✅ |
| Suppression d'un fournisseur / d'un produit présent sur un document d'achat bloquée (JPQL `COUNT`) | ✅ |
| `GET /api/settings/taxes` ouvert aussi à `PURCHASE_CREATE/UPDATE` (un acheteur n'a pas `COMPANY_VIEW`) | ✅ |
| Pages `/purchase-orders` et `/goods-receipts` (liste + éditeur, entrepôt du bon, totaux live, « Create receipt ») ; menu Purchases activé | ✅ |
| Tests : `DocumentTotalsTest` · `PurchaseDocumentServiceTest` · `PurchaseDocumentControllerTest` · `PurchaseIntegrationTest` (19) · `StockServiceTest` (+5) · `CompanyIsolationTest` (+2) · specs éditeur (28) et liste (7) | ✅ |
| **Différé :** facture d'achat, avoir fournisseur, bon de retour fournisseur, paiements (Phase 7) ; quantités reçues suivies ligne à ligne (aujourd'hui une commande peut être sur-réceptionnée) ; « reste à recevoir » ; prix d'achat mémorisé par fournisseur ; envoi e-mail ; retenue à la source | — |
| Vérification du module côté serveur : faite globalement (voir « Vérification du module »), une entreprise sans le module obtient 403 | ✅ |

**Total après Achats 5a : 492 tests backend · 171 frontend.**

### Impression des documents ✅

Cadrée d'après l'analyse Finco (§06-§07 : « Aperçu » → « Télécharger PDF » / « Imprimer », montant en toutes lettres,
paramètres d'affichage). **Une seule feuille pour tous les types de document** — devis, commande client, commande
fournisseur, bon de réception, et les factures à venir.

| Tâche | Statut |
|---|---|
| `GET /api/print-profile` : identité de l'entreprise, matricule fiscal, logo, cachet et **seuls** les comptes bancaires cochés « afficher sur les documents » — ouvert à `SALE_VIEW` / `PURCHASE_VIEW` / `COMPANY_VIEW` (un commercial n'a pas `COMPANY_VIEW`) | ✅ |
| Feuille A4 (`PrintableDocumentComponent`) : en-tête logo + identité + MF, titre + n° + dates, client/fournisseur (adresse de facturation, de livraison, entrepôt d'un bon de réception), lignes N° · Réf. · Désignation · Qté · P.U HT · Remise · TVA · Total HT, totaux (HT, FODEC, TVA, timbre, **Net à payer**) + récap TVA par taux avec la base FODEC incluse, montant en lettres, notes, conditions générales, RIB, cachet, pied légal | ✅ |
| **Montant en toutes lettres** en français (« trente-sept dinars et cinquante-sept millimes »), phrase légale par type (« Arrêté le présent devis à la somme de… ») | ✅ |
| Formats Finco : virgule décimale, 3 décimales, « DT » ; français (`fr-TN`) | ✅ |
| Filigrane **BROUILLON** / **ANNULÉ** : l'aperçu d'un brouillon ne peut pas passer pour un document valide | ✅ |
| Options d'affichage (le « Paramètres » de Finco, l'utile) : masquer les prix, masquer la colonne Référence | ✅ |
| Aperçu dans un onglet à part, hors layout ; **Print / Save as PDF** = la boîte d'impression du navigateur (un seul chemin pour papier et PDF) ; le titre de l'onglet devient le nom du PDF (« DEVIS-2026-0002 - DEVIS ») | ✅ |
| Bouton **Print** dans l'éditeur (document enregistré) et dans le menu de chaque ligne des quatre listes | ✅ |
| **Étapes de statut dans le menu de chaque ligne** des quatre listes (émettre / valider, accepter, rejeter, remettre en émis, confirmer, convertir, créer la réception, annuler) — filtrées par droit, définies une fois (`*-document-actions.ts`) et **chacune demande confirmation** (`p-confirmDialog`, bouton rouge pour ce qui est irréversible), depuis la liste comme depuis la page du document | ✅ |
| Tests : `amount-in-words.spec` (chaque règle du français : 21, 71, 80, 81, 91, cent(s), mille, million…) · `printable-document.model.spec` · `printable-document.component.spec` (le cas Finco 37,057) · `document-print.component.spec` · `PrintProfileServiceTest` · `PrintProfileControllerTest` · `PrintProfileIntegrationTest` | ✅ |
| **Download PDF** : télécharge la feuille en fichier PDF **sans imprimer** (`PdfExportService`, html2pdf.js chargé à la demande). Le fichier reprend les options choisies (prix, colonne Référence), est nommé « QUO-2026-00002 - DEVIS.pdf » (« Brouillon - DEVIS.pdf » pour un brouillon), A4 marge 12 mm, plusieurs pages si besoin ; bouton occupé pendant la génération, message si elle échoue | ✅ |
| **Différé :** PDF généré côté serveur (fichier joint à un e-mail, archivage) ; texte sélectionnable dans le PDF (aujourd'hui une capture de la feuille, choix retenu) ; envoi par e-mail / WhatsApp ; images des produits et pied de page personnalisé ; « Afficher comme note d'honoraires » ; mention légale d'une retenue à la source ; libellés d'impression dans une autre langue que le français | — |

**Total après l'impression et le téléchargement PDF : 499 tests backend · 301 frontend.**

### 4b — Bon de livraison ✅

| Tâche | Statut |
|---|---|
| Migration `V23` : `sales_documents.warehouse_id`, `stock_movements.origin_id` (+ index) | ✅ |
| Type `DELIVERY_NOTE` (numéro `BL`), statut `DELIVERED` ; entrepôt de départ obligatoire et actif | ✅ |
| `POST /{id}/convert-to-delivery-note` : commande confirmée → bon brouillon (plusieurs autorisés = livraisons partielles) | ✅ |
| Workflow : brouillon → émis → **livré** ; annulable émis ou livré | ✅ |
| **Livrer = sortie de stock** (`stockService.deliver`, même transaction) qui **consomme la réservation** de la commande ; annuler un bon livré remet les biens et ré-réserve pour la commande si elle tient encore (`undoDelivery`) | ✅ |
| Une commande ayant un bon vivant ne s'annule pas ; un devis ne se convertit pas en bon | ✅ |
| Bon sans commande : livrable, refusé si le stock disponible ne suffit pas | ✅ |
| Frontend : pages `/delivery-notes`, champ entrepôt, étapes « Create delivery note » / « Mark as delivered » / « Cancel delivery note » (liste + page, avec confirmation) ; menu Sales : Delivery notes | ✅ |
| Impression : « BON DE LIVRAISON », bloc « Entrepôt de départ » (`warehouseLabel` dans `WORDING`) | ✅ |
| Tests : `SalesDocumentTest` · `SalesDocumentServiceTest` · `StockServiceTest` · `SalesDocumentControllerTest` · `StockIntegrationTest` (+8) · `CompanyIsolationTest` (+1) · specs actions, liste, éditeur, impression | ✅ |
| **Différé :** conversion devis → bon ; facture depuis un bon ; bon de retour | — |

**Total après le bon de livraison : 536 tests backend · 317 frontend.**

---

## Phase 6 — Stock 🚧

Objectif : suivre les mouvements et niveaux de stock. Étape **6a** faite (entrepôts, registre, réservation
par les commandes) ; la sortie physique attend la livraison / facture (4b, 7) ; l'entrée d'achat est faite (Phase 5a).

| Tâche | Statut |
|---|---|
| Migration `V21` : `warehouses` + `stock_movements` (append-only, quantité signée) + `products.min_stock` ; entrepôt par défaut inséré pour chaque entreprise existante | ✅ |
| Feature `warehouse/` : CRUD, entrepôt par défaut (créé à l'inscription, ni supprimable ni désactivable), inactif = plus de mouvement, suppression refusée s'il a des mouvements | ✅ |
| Feature `stock/` : le stock est la **somme** des mouvements, jamais un compteur ; physique / réservé / disponible | ✅ |
| Mouvements manuels : entrée, sortie, **ajustement** (on saisit la quantité comptée, le service écrit l'écart), **transfert** entre entrepôts (deux lignes) | ✅ |
| Règle « stock négatif » (`allowNegativeStock`) appliquée : un produit strict ne descend pas sous 0 disponible dans l'entrepôt concerné | ✅ |
| Confirmer une commande **réserve** ses biens (entrepôt par défaut, refus si insuffisant — la commande reste « émise ») ; l'annuler **libère** ce qu'elle tenait | ✅ |
| Seuil `minStock` par produit (unique pour l'entreprise) + drapeau et filtre « stock bas » (sous-requête JPQL) ; formulaire produit : champ « Minimum stock » | ✅ |
| Suppression d'un produit / d'un entrepôt qui a des mouvements bloquée | ✅ |
| Pages `/stock` (inventaire), `/stock/movements` (registre filtrable), `/settings/warehouses` ; menu Inventory : Stock + Movements | ✅ |
| Tests : `StockServiceTest` · `WarehouseServiceTest` · contrôleurs · `StockIntegrationTest` (18) · `CompanyIsolationTest` (+3) · `SalesDocumentServiceTest` (+4) · specs formulaire de mouvement, inventaire, registre, entrepôts | ✅ |
| **Différé :** sortie physique à la livraison / facture (Phases 4b, 7) ; entrée à la réception d'achat (Phase 5) ; seuil par entrepôt ; verrouillage (LOCK/UNLOCK) ; lots et séries ; valorisation ; document de transfert / d'inventaire imprimable | — |
| Indicateur "stock bas" dans le tableau de bord | 🔲 |

**Total après Stock 6a : 431 tests backend · 136 frontend.**

---

## Phase 7 — Facturation 🚧

Objectif : émettre et suivre les factures et paiements. Étape **7a** faite : la **facture de vente** et ses
**paiements**. Décision (analyse Finco §06/§09) : la facture est un type de plus du document générique de vente
(pas de table `invoices`), son statut de règlement se **dérive** des paiements, jamais édité à la main.

| Tâche | Statut |
|---|---|
| Migration `V24` : `sales_documents.paid_amount` + table `payments` (registre : ajout et annulation, ni modification ni suppression) | ✅ |
| Type `INVOICE` (numéro `INV`, alloué à l'émission), entrepôt obligatoire et actif comme le bon de livraison | ✅ |
| Statuts : brouillon → émise (« impayée ») → **partiellement payée** → **payée**, ou annulée ; jamais changés à la main (`changeStatus` refuse une facture) | ✅ |
| `applyPaid(somme)` : `paid_amount` et statut réécrits depuis la SOMME des paiements actifs ; solde calculé côté serveur (`balance`) | ✅ |
| `POST /{id}/convert-to-invoice` depuis un devis émis/accepté, une commande confirmée ou un bon de livraison livré ; une facture vivante par source | ✅ |
| Une commande livrée par bons se facture par ses bons ; une commande facturée ne se livre plus ; une source portant une facture vivante ne s'annule pas | ✅ |
| **Stock à l'émission** : facture issue d'un bon = aucun mouvement ; sinon sortie (`stockService.deliver`) qui consomme la réservation de la commande ; refusée (facture reste brouillon, numéro rendu) si le stock strict manque ; annuler une facture impayée remet les biens (`undoDelivery`) | ✅ |
| Une facture payée, même en partie, ne s'annule pas : on annule d'abord ses paiements | ✅ |
| Feature `payment/` : `POST /api/payments` (refus au-delà du reste dû, facture non émise / annulée / soldée), `POST /{id}/cancel`, `GET ?invoiceId=` ; droits `SALE_VIEW` / `SALE_UPDATE` / `SALE_CANCEL` | ✅ |
| `GlobalExceptionHandler` : verbe non offert → 405, adresse inconnue → 404 (renvoyaient 500) | ✅ |
| Frontend : pages `/invoices` (colonne Solde, statuts Unpaid / Partially paid / Paid), champ entrepôt, « Create invoice » depuis devis / commande / bon livré, « Cancel invoice », menu Sales : Invoices | ✅ |
| Frontend : dialogue « Add payment » (menu de ligne de la liste ET page de la facture) et panneau Paiements (Total / Payé / Solde, tableau, annulation confirmée) | ✅ |
| Impression : « FACTURE » (`WORDING.INVOICE`), phrase légale « Arrêtée la présente facture à la somme de » | ✅ |
| Tests : `SalesDocumentTest` · `SalesDocumentServiceTest` · `PaymentServiceTest` · `PaymentControllerTest` · `InvoiceIntegrationTest` (18) · `CompanyIsolationTest` (+1) · `GlobalExceptionHandlerTest` (+2) · specs actions, liste, éditeur, impression, `payment-form`, `invoice-payments` | ✅ |
| **Différé :** facture d'achat et paiements fournisseurs ; avoir (annulation d'une facture émise : aujourd'hui seule une facture impayée s'annule) ; bon de retour ; comptes de trésorerie et suivi d'un chèque / d'une traite jusqu'à l'encaissement ; retenue à la source ; paiement non alloué ou réparti sur plusieurs factures ; page globale « Payments » ; relevé de compte client ; facture partielle d'une commande ; « Afficher comme note d'honoraires » ; facturation électronique (TTN) ; instantané de l'identité du client | — |
| Vérification du module côté serveur : faite globalement (voir « Vérification du module »), une entreprise sans le module obtient 403 | ✅ |

**Total après la facture de vente et ses paiements : 592 tests backend · 355 frontend.**

### 7b — Avoir (credit note) ✅

Décision : l'avoir est un type de plus du document générique, **imputé sur UNE facture** (pas de crédit libre chez le
client) et **sans effet sur le stock** (document financier).

| Tâche | Statut |
|---|---|
| Migration `V25` : `sales_documents.credited_amount` | ✅ |
| Type `CREDIT_NOTE` (numéro `CN`, à l'émission) ; jamais créé à la main : `POST /{id}/convert-to-credit-note` depuis une facture émise (payée ou non), le client de la facture est verrouillé | ✅ |
| Brouillon = copie des lignes, abaissées par l'utilisateur à ce qui est crédité ; plusieurs avoirs par facture, jusqu'à son total | ✅ |
| Émettre = imputer : `credited_amount` de la facture réécrit depuis la SOMME des avoirs émis ; refusé (brouillon, numéro rendu) au-delà du total de la facture | ✅ |
| Annuler un avoir émis rend son montant à la facture (`ISSUED` → `CANCELLED` seulement) | ✅ |
| Règlement d'une facture = paiements + avoirs ; solde = total − crédité − payé, **négatif** si l'avoir suit un paiement (« To refund ») ; un paiement est plafonné à ce qui reste dû | ✅ |
| Une facture portant un avoir vivant, même brouillon, ne s'annule pas | ✅ |
| Frontend : `/credit-notes` (liste sans « New »), « Create credit note » depuis une facture émise (liste et page), « Cancel credit note », client verrouillé, statut « Applied », panneau de paiements : Crédité et « To refund » ; menu Sales : Credit notes | ✅ |
| Impression : « AVOIR », phrase légale, total « Net à déduire » (`netLabel` dans `WORDING`) | ✅ |
| Tests : `SalesDocumentTest` · `SalesDocumentServiceTest` · `PaymentServiceTest` · `SalesDocumentControllerTest` · `CreditNoteIntegrationTest` (12) · `CompanyIsolationTest` (+1) · specs actions, liste, éditeur, impression, panneau de paiements | ✅ |
| **Différé :** remboursement d'un solde négatif (paiement sortant) ; avoir libre / crédit reporté sur une autre facture (statuts Non utilisé / Partiellement utilisé de Finco) ; réintégration des biens au stock (fait : bon de retour, phase 8) ; avoir d'une facture d'achat (fait : 7d) ; libellé « Réglé » distinct d'une facture soldée par avoir | — |

**Total après l'avoir : 621 tests backend · 370 frontend.**

### 7c — Facture d'achat et paiements fournisseur ✅

Décision : le **miroir** de la facture de vente (analyse Finco §06 : statuts identiques à la facture de vente) ; les
paiements fournisseur sont un registre miroir de `payments` ; le dialogue et le panneau de paiements du frontend sont
**partagés** (`family`).

| Tâche | Statut |
|---|---|
| Migration `V26` : `purchase_documents.paid_amount` + table `supplier_payments` (registre : ajout et annulation) | ✅ |
| Type `PURCHASE_INVOICE` (numéro `PINV`, alloué à la validation), entrepôt obligatoire et actif comme le bon de réception | ✅ |
| Statuts : brouillon → validée (« impayée ») → partiellement payée → payée, ou annulée ; jamais changés à la main (`applyPaid(somme)`) | ✅ |
| `POST /{id}/convert-to-invoice` depuis une commande validée ou un bon de réception validé ; une facture vivante par source | ✅ |
| Une commande reçue par bons se facture par ses bons ; une commande facturée ne se réceptionne plus ; une source portant une facture vivante ne s'annule pas | ✅ |
| **Stock à la validation** : facture issue d'un bon = aucun mouvement ; sinon entrée (`stockService.receive`) ; annuler une facture impayée remet les biens dehors (`reverseReceipt`, refusé si vendus) | ✅ |
| Une facture payée, même en partie, ne s'annule pas : on annule d'abord ses paiements | ✅ |
| Feature `supplierpayment/` : `POST /api/supplier-payments` (plafond = reste dû), `POST /{id}/cancel`, `GET ?invoiceId=` ; droits `PURCHASE_VIEW` / `PURCHASE_UPDATE` / `PURCHASE_CANCEL` | ✅ |
| Frontend : `/purchase-invoices` (colonne Solde, statuts Unpaid / Partially paid / Paid), champ entrepôt, « Create invoice » depuis commande / bon de réception, « Pay supplier » (menu de ligne), panneau de paiements sous la facture ; menu Purchases : Purchase invoices | ✅ |
| Impression : « FACTURE D'ACHAT » (`WORDING.PURCHASE_INVOICE`) | ✅ |
| Tests : `PurchaseDocumentTest` · `PurchaseDocumentServiceTest` · `SupplierPaymentServiceTest` · `SupplierPaymentControllerTest` · `PurchaseInvoiceIntegrationTest` (16) · `CompanyIsolationTest` (+1) · specs actions, liste, éditeur, impression, panneau et dialogue de paiements | ✅ |
| **Différé :** avoir fournisseur ; bon de retour fournisseur ; n° de la facture du fournisseur (référence externe) ; comptes de trésorerie, suivi des chèques, retenue à la source côté achats ; paiement non alloué ou réparti sur plusieurs factures ; page globale « Payments » | — |
| Vérification du module côté serveur : faite globalement (voir « Vérification du module »), une entreprise sans le module obtient 403 | ✅ |

**Total après la facture d'achat : 670 tests backend · 391 frontend.**

### Vérification du module côté serveur ✅

Le trou signalé depuis Ventes 4a : un admin plateforme pouvait couper un module, mais seul le **menu** le cachait — un appel
direct à l'API passait encore. Réglé **en un seul endroit**, sans toucher aux contrôleurs.

| Tâche | Statut |
|---|---|
| `Company.allows(permission)` : une permission d'un module administratif est toujours permise, une permission d'un module métier seulement si `enabledModules` le contient (`BusinessModule.gates`) | ✅ |
| `User.collectPermissions()` retire les permissions des modules coupés : c'est la source de **tous** les `@PreAuthorize` et de la session — Clients, Fournisseurs, Produits, Stock, Entrepôts, Ventes, Paiements, Achats, Paiements fournisseur, et tout endpoint futur, sont refusés (403) d'un coup | ✅ |
| Effet immédiat dans les deux sens (permissions relues à chaque requête), le même jeton passe de 403 à 200 quand le module revient | ✅ |
| La session ne liste plus ces permissions : `*appHasPermission` et `permissionGuard` disent la même chose que le serveur | ✅ |
| Un module coupé ne se redistribue pas (on ne peut pas donner à un rôle une permission qu'on n'a plus) ; les modules administratifs restent toujours disponibles | ✅ |
| Tests : `CompanyModulesTest` (5) · `ModuleGateIntegrationTest` (10 : chaque module, administration, bascule à chaud, session, autre entreprise, redistribution) | ✅ |

**Total après la vérification du module : 685 tests backend · 391 frontend.**

### 7d — Avoir fournisseur ✅

Décision : le **miroir** de l'avoir de vente (7b) — imputé sur **UNE** facture d'achat, **sans effet sur le stock**.

| Tâche | Statut |
|---|---|
| Migration `V27` : `purchase_documents.credited_amount` | ✅ |
| Type `PURCHASE_CREDIT_NOTE` (numéro `PCN`, à la validation) ; jamais créé à la main : `POST /{id}/convert-to-credit-note` depuis une facture validée (payée ou non), le fournisseur de la facture est verrouillé | ✅ |
| Brouillon = copie des lignes abaissées à ce qui est crédité ; plusieurs avoirs par facture, jusqu'à son total | ✅ |
| Valider = imputer : `credited_amount` réécrit depuis la SOMME des avoirs validés ; refusé (brouillon, numéro rendu) au-delà du total de la facture | ✅ |
| Annuler un avoir validé rend son montant à la facture (`VALIDATED` → `CANCELLED` seulement) | ✅ |
| Règlement = paiements + avoirs ; solde = total − crédité − payé, **négatif** si l'avoir suit un paiement (« To recover ») ; un paiement fournisseur est plafonné à ce qui reste dû | ✅ |
| Une facture portant un avoir vivant, même brouillon, ne s'annule pas | ✅ |
| Frontend : `/purchase-credit-notes` (liste sans « New »), « Create credit note » depuis une facture (liste et page), « Cancel credit note », fournisseur verrouillé, statut « Applied », panneau de paiements (Crédité, « To recover ») ; menu Purchases : Supplier credit notes | ✅ |
| Impression : « AVOIR FOURNISSEUR », total « Net à déduire » | ✅ |
| Tests : `PurchaseDocumentTest` · `PurchaseDocumentServiceTest` · `SupplierPaymentServiceTest` · `PurchaseDocumentControllerTest` · `PurchaseCreditNoteIntegrationTest` (11) · `CompanyIsolationTest` (+1) · specs actions, liste, éditeur, impression, panneau | ✅ |
| **Différé :** récupération d'un solde négatif (encaissement d'un fournisseur) ; bon de retour fournisseur (sortie des biens du stock) ; avoir libre / crédit reporté ; n° de la facture du fournisseur | — |

**Total après l'avoir fournisseur : 713 tests backend · 403 frontend.**

### 8 — Bons de retour (client et fournisseur) ✅

Analyse Finco §06/§08 : le bon de retour a les statuts Brouillon / Validé / Annulé, et un retour client **réintègre** les
biens, un retour fournisseur les **sort**. C'est ce qui manquait pour que les avoirs (financiers) ne soient plus les seuls
à « corriger » une vente ou un achat. Type de plus des documents génériques : **aucune migration**.

| Tâche | Statut |
|---|---|
| `RETURN_NOTE` (numéro `RN`) côté ventes, `PURCHASE_RETURN_NOTE` (numéro `PRN`) côté achats ; entrepôt obligatoire et actif | ✅ |
| Client : **émettre = entrée de stock** (`receive`, jamais refusée), annuler = sortie (`reverseReceipt`, refusée si un produit strict n'a plus les biens) | ✅ |
| Fournisseur : **valider = sortie de stock** (`deliver`, refusée — le bon reste brouillon, numéro rendu — si le stock strict manque), annuler = entrée (`undoDelivery`) | ✅ |
| Créé à la main (retour d'un client sans document) OU `POST /{id}/convert-to-return-note` depuis un bon de livraison livré / une facture émise (client) ou un bon de réception / une facture validés (fournisseur) : lignes recopiées puis abaissées, tiers alors verrouillé | ✅ |
| Un document portant un bon de retour vivant (même brouillon) ne s'annule pas : sa remise en stock / sa sortie se ferait deux fois | ✅ |
| Plusieurs bons de retour par document (retour en plusieurs parties) | ✅ |
| Indépendant des avoirs : retourner sans créditer, et inversement | ✅ |
| Frontend : `/return-notes` et `/purchase-return-notes` (avec « New »), « Create return note » depuis bon de livraison / facture / bon de réception (liste et page), « Cancel return note », statuts « Received » / « Sent back », menus Sales et Purchases | ✅ |
| Impression : « BON DE RETOUR » (« Entrepôt de retour ») et « BON DE RETOUR FOURNISSEUR » (« Entrepôt de départ »), total « Net à déduire » | ✅ |
| Tests : `SalesDocumentTest` · `PurchaseDocumentTest` · services · contrôleurs · `ReturnNoteIntegrationTest` (10) · `PurchaseReturnNoteIntegrationTest` (9) · `CompanyIsolationTest` (+1) · specs actions, listes, éditeurs, impression | ✅ |
| **Différé :** contrôle que les biens retournés sont bien ceux du document d'origine ; remise en réserve sur la commande d'origine ; lien automatique retour → avoir | — |

**Total après les bons de retour : 756 tests backend · 426 frontend.**

---

## Phase 8 — Tableau de bord ✅

Objectif : remplacer le placeholder par de vraies données. **Fait** — écart voulu avec le plan d'origine : trois endpoints (un par
domaine, chacun avec le droit de son module) plutôt qu'un seul `/stats`, et un graphique en HTML/CSS plutôt que Chart.js (six
nombres ne justifient pas une dépendance ; `chart.js` n'est d'ailleurs pas installé).

| Tâche | Statut |
|---|---|
| Endpoints `GET /api/dashboard/sales` (SALE_VIEW), `/purchases` (PURCHASE_VIEW), `/stock` (STOCK_VIEW) ; pas d'entité, de repository ni de service propres au dashboard | ✅ |
| KPI ventes : chiffre d'affaires **net** (factures − avoirs) du jour et du mois, factures impayées (nombre + montant), en retard | ✅ |
| KPI achats : la même chose avec les factures d'achat et leurs avoirs (« To pay », « Overdue payables ») | ✅ |
| KPI stock : valeur au prix d'achat, nombre de produits au minimum | ✅ |
| Graphique des 6 derniers mois (ventes nettes, achats nets) — `shared/components/bar-chart`, sans librairie | ✅ |
| Tableau des factures en retard à relancer / à payer (5 plus anciennes, lien vers la facture) | ✅ |
| Tableau des produits sous leur minimum (5 premiers, « and N more ») | ✅ |
| Chaque domaine n'est demandé et affiché que si la personne a le droit du module ; un module coupé répond 403 ; message si aucun domaine n'est accessible ; état vide et message d'échec par domaine | ✅ |
| Tests : `DashboardIntegrationTest` (7) · `DashboardControllerTest` · figures de `SalesDocumentServiceTest` / `PurchaseDocumentServiceTest` · specs `dashboard`, `bar-chart` | ✅ |
| **Différé :** nombre de clients ; dernières commandes ; période au choix (aujourd'hui / mois / année) ; comparaison avec la période précédente ; marge ; top clients / produits ; valeur du stock par entrepôt ; rafraîchissement automatique | — |

**Total après le tableau de bord : 771 tests backend · 443 frontend.**

---

### 8 — Suivi des quantités par ligne ✅

Objectif : savoir, ligne par ligne, ce qui a déjà été livré / reçu / retourné, afficher le « reste » et refuser d'aller au-delà.

| Tâche | Statut |
|---|---|
| Migration `V28` : `source_line_id` (clé étrangère + index) sur `sales_document_lines` et `purchase_document_lines` | ✅ |
| Une ligne recopiée d'un document source retient la ligne d'origine (`copyContentFrom(source, quantityOf)` : seule la quantité restante est recopiée, une ligne soldée ne l'est pas) | ✅ |
| Ventes : commande ← bons de livraison (émis / livrés) = « livré · reste à livrer » ; bon de livraison ou facture ← bons de retour émis = « retourné · reste à retourner » | ✅ |
| Achats : commande ← bons de réception validés = « reçu · reste à recevoir » ; bon de réception ou facture d'achat ← bons de retour fournisseur validés | ✅ |
| La quantité prise est une SOMME lue en base (`quantitiesTaken`), jamais un compteur ; une annulation rend la quantité | ✅ |
| `convert-to-delivery-note` / `-receipt` / `-return-note` préremplissent avec le reste et refusent (422) quand tout est déjà pris | ✅ |
| Émission / validation d'un document qui dépasse le reste → 422 « Only 3 of "…" left to deliver » ; le contrôle est fait à l'émission, moment où les biens sont engagés | ✅ |
| Un lien vers une ligne qui n'est pas celle du document source → 422 ; sans source, le lien est ignoré | ✅ |
| Réponses : `sourceLineId`, `fulfilledQuantity`, `remainingQuantity`, `sourceRemaining` | ✅ |
| Frontend : sous la quantité d'un brouillon « N left to deliver / receive / return » (et quantité plafonnée), sur un document émis « delivered 4 · 6 left » ; `sourceLineId` renvoyé avec le brouillon | ✅ |
| Tests : `LineQuantityTrackingIntegrationTest` (7) · `copyContentFrom` dans `SalesDocumentTest` / `PurchaseDocumentTest` · specs des deux éditeurs (+8) | ✅ |
| **Différé :** quantité facturée suivie (facture partielle d'une commande) ; quantité créditée par ligne (l'avoir reste plafonné au total de la facture) ; devis → bon ; deux sources pour un même retour (bon retourné à la fois sur la facture et sur le bon) ; ligne ajoutée à la main sur un bon de retour d'un document source (non suivie) | — |

**Total après le suivi des quantités : 781 tests backend · 451 frontend.**

---

### 9 — Durcissement pour un MVP réel ✅

| Tâche | Statut |
|---|---|
| Audit : chaque endpoint (hors auth / self-service / plateforme) porte un `@PreAuthorize` ; toute requête de repository non filtrée par entreprise est un simple comptage clé-par-id d'une entité déjà lue dans l'entreprise | ✅ |
| **Course « vérifier puis écrire »** sur le stock : deux sorties simultanées des dernières pièces d'un produit strict passaient toutes les deux. `ProductService.lockForStock` verrouille la ligne du produit (`PESSIMISTIC_WRITE`) avant de lire le niveau | ✅ |
| Même course sur le reste d'une commande : deux bons de livraison (réception, retour) émis en même temps pour le même reste passaient tous les deux. `checkAgainstSource` verrouille la ligne du document source (`lockById`) | ✅ |
| `ConcurrencyIntegrationTest` (2) — requêtes réellement simultanées ; le premier test échoue sans le verrou (vérifié) | ✅ |
| Profil `prod` (`application-prod.properties`) : base, JWT, CORS et dossier d'uploads **sans valeur par défaut** — une variable absente arrête le démarrage au lieu de tourner avec le secret de développement | ✅ |
| CORS configurable (`smartbusiness.cors.allowed-origins` / `CORS_ALLOWED_ORIGINS`, `http://localhost:4200` par défaut) | ✅ |
| Build de production Angular : `environment.prod.ts` (`apiUrl: '/api'`, même origine derrière un reverse proxy) via `fileReplacements` | ✅ |
| `tools/seed_demo.py` : script d'amorçage de données d'ESSAI (5 entreprises × 20 de chaque type : catégories, marques, produits, clients, fournisseurs, utilisateurs, devis → commandes → bons → factures → paiements, achats compris) via l'API ; mot de passe lu dans `SEED_PASSWORD`, jamais écrit ; à lancer par la personne qui teste | ✅ |
| `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'` sur chaque réponse (`SecurityConfig`) — les autres en-têtes (`X-Content-Type-Options`, `X-Frame-Options`, `Cache-Control`, HSTS en HTTPS) sont déjà les défauts de Spring Security | ✅ |
| **Verrouillage du compte après des mots de passe erronés** (`V29` : `users.failed_login_attempts`, `users.locked_until`) : 5 échecs de suite verrouillent 15 minutes (`smartbusiness.login.lockout-threshold` / `-lockout-duration`), remis à zéro par une connexion réussie. La mise à jour du compteur doit survivre au rejet qui suit (la transaction de `login()` est annulée par l'exception) : `UserService.saveLoginOutcome` l'écrit dans sa PROPRE transaction (`REQUIRES_NEW`), comme `AuditService` le fait déjà pour l'audit d'un échec — **piège vérifié par le test** (`LoginLockoutIntegrationTest`, échouait sans le `REQUIRES_NEW`) | ✅ |
| Audit des dépendances : `npm audit` remonte 9 failles (dont 3 hautes) sur `@angular/core`/`compiler`/`router` ≤ 19.2.25, sans correctif dans la branche 19 — seul un saut vers Angular 21 (changement majeur, hors `CLAUDE.md`) les couvre. Sans SSR/hydratation ni i18n actifs, le risque réel est plus faible que le score ; décision à prendre par l'utilisateur | ⚠️ |
| **Différé :** verrou par ordre stable des produits (deux documents à produits croisés peuvent se bloquer mutuellement — Postgres en annule un) ; limitation par IP (le verrouillage est par compte) ; HTTPS / reverse proxy (hors application) ; sauvegardes de la base ; journal applicatif structuré ; montée vers Angular 21 | — |

**Total après le durcissement : 785 tests backend · 451 frontend.**

---

## Notes de progression

Mettre à jour ce fichier au fur et à mesure de l'avancement en changeant 🔲 → ✅.

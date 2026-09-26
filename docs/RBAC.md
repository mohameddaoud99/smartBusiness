# RBAC.md — Conception du système de rôles et permissions

Document de conception. Rédigé **avant** l'implémentation, à valider puis à faire vivre.

Modèle retenu :

```
Company → User → Role → Permission
```

Une seule règle d'isolation : **tout est filtré par `company_id`**.
Les branches n'interviennent jamais dans le calcul des droits.

---

## 1. Modèle de données

```
companies
   │
   ├── branches ──────────┐        (données métier, aucun rôle RBAC)
   │                      │ users.branch_id (optionnel, organisationnel)
   ├── roles ──< role_permissions  (permission = nom de l'enum, pas de table)
   │      │                       │
   │      └──< user_roles >── users
   │
   └── audit_logs
```

`users.branch_id` est le seul lien entre `Branch` et le reste du modèle. Il indique
**où** travaille la personne — pas ce qu'elle a le droit de faire. Sélectionné (ou non)
à la création/édition de l'utilisateur, il n'entre dans aucune vérification de permission :
un utilisateur rattaché à Sfax garde exactement les mêmes droits que s'il était rattaché
à Tunis ou à aucune branche. C'est la même règle qu'au §12 : les permissions s'appliquent
à toute l'entreprise, jamais branche par branche.

### Tables

```sql
companies
  id, name, email, phone, address, status, created_at, updated_at

branches
  id, company_id → companies, code, name, address, phone, status, ...
  UNIQUE (company_id, code)

users
  id, company_id → companies, branch_id → branches (nullable), first_name, last_name,
  username, email, phone, password_hash, status, last_login_at, ...
  UNIQUE (email)                  -- identifiant de connexion, global
  UNIQUE (company_id, username)   -- lisible, unique dans l'entreprise seulement

roles
  id, company_id → companies, name, label, description, system BOOLEAN, ...
  UNIQUE (company_id, name)

role_permissions
  role_id → roles, permission VARCHAR(40)
  PRIMARY KEY (role_id, permission)

user_roles
  user_id → users, role_id → roles
  PRIMARY KEY (user_id, role_id)

audit_logs
  id, company_id, user_id (nullable), username, action, entity_type, entity_id,
  detail, ip_address, occurred_at
  INDEX (company_id, occurred_at DESC)
```

### Trois décisions qui simplifient le modèle

**a) Pas de table `permissions`.**
Une permission est une valeur de l'enum Java `Permission`. `role_permissions` stocke son nom.
Conséquences : aucun seed à maintenir, aucune dérive entre code et base, autocomplétion et
sécurité à la compilation. Ajouter un module = ajouter des valeurs à l'enum, rien d'autre.

**b) Les rôles système sont copiés par entreprise.**
`roles.company_id` est **NOT NULL** pour tout le monde. À la création d'une entreprise, les
7 rôles système sont insérés pour elle avec `system = true`. Pas de rôle « global » à
`company_id = NULL` → une seule règle d'isolation, aucun cas particulier dans les requêtes.
Un rôle système ne peut être ni supprimé, ni renommé, ni modifié dans ses permissions.

**c) `audit_logs` remplace `user_history`.**
La table `user_history` actuelle et l'audit log demandé décrivent la même chose. On garde une
seule table. L'onglet « History » du drawer utilisateur lit
`/api/audit-logs?entityType=USER&entityId={id}` — le composant timeline existant est conservé.

---

## 2. Entités JPA

| Package | Fichiers |
|---|---|
| `company/` | `Company`, `CompanyRepository`, `CompanyService`, `CompanyController`, `CompanyMapper`, `CompanyRequest`, `CompanyResponse`, `CompanyStatus` |
| `branch/` | `Branch`, `BranchRepository`, `BranchService`, `BranchController`, `BranchMapper`, `BranchRequest`, `BranchResponse`, `BranchStatus` |
| `user/` | existant, étendu : `company`, `roles`, `UserStatus` (+ `LOCKED`), suppression de `UserRole`, `UserHistory`, `UserEvent` |
| `role/` | `Role`, `RoleRepository`, `RoleService`, `RoleController`, `RoleMapper`, `RoleRequest`, `RoleResponse`, `Permission`, `PermissionModule`, `SystemRole` |
| `audit/` | `AuditLog`, `AuditLogRepository`, `AuditService`, `AuditLogController`, `AuditAction`, `AuditLogResponse` |
| `security/` | `SecurityConfig`, `JwtService`, `JwtAuthenticationFilter`, `CompanyUserDetails`, `CurrentUser`, `PasswordEncoderConfig` (existant) |
| `auth/` | `AuthController`, `AuthService`, `LoginRequest`, `LoginResponse`, `RegisterCompanyRequest`, `ChangePasswordRequest`, `MeResponse` |

Toutes les entités étendent `BaseEntity`. `AuditLog` ne l'étend pas (append-only, `occurredAt`
suffit) — même choix que l'actuel `UserHistory`.

---

## 3. Relations

```java
// User
@ManyToOne(fetch = LAZY, optional = false)
@JoinColumn(name = "company_id", nullable = false)
private Company company;

@ManyToMany(fetch = LAZY)
@JoinTable(name = "user_roles",
    joinColumns = @JoinColumn(name = "user_id"),
    inverseJoinColumns = @JoinColumn(name = "role_id"))
private Set<Role> roles = new HashSet<>();

// Role
@ManyToOne(fetch = LAZY, optional = false)
private Company company;

@ElementCollection(fetch = FetchType.EAGER)
@CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
@Column(name = "permission", length = 40)
@Enumerated(EnumType.STRING)
private Set<Permission> permissions = EnumSet.noneOf(Permission.class);

// Branch
@ManyToOne(fetch = LAZY, optional = false)
private Company company;
```

`Role.permissions` en `@ElementCollection` EAGER : un rôle a au maximum ~40 permissions, elles
sont nécessaires à chaque requête authentifiée. Pas de N+1 problématique.

Le chargement de session utilise une requête dédiée :

```java
@Query("SELECT u FROM User u LEFT JOIN FETCH u.roles WHERE u.id = :id")
Optional<User> findByIdWithRoles(@Param("id") Long id);
```

---

## 4. Enums

| Enum | Valeurs |
|---|---|
| `CompanyStatus` | `ACTIVE`, `SUSPENDED` |
| `BranchStatus` | `ACTIVE`, `INACTIVE` |
| `UserStatus` | `ACTIVE`, `INACTIVE`, `LOCKED` |
| `PermissionModule` | `USERS`, `ROLES`, `BRANCHES`, `CUSTOMERS`, `SUPPLIERS`, `PRODUCTS`, `SALES`, `PURCHASES`, `STOCK`, `COMPANY`, `SECURITY` |
| `Permission` | voir §10 |
| `SystemRole` | `COMPANY_ADMIN`, `SALES_MANAGER`, `SALES_AGENT`, `PURCHASE_MANAGER`, `STOCK_MANAGER`, `ACCOUNTANT`, `VIEWER` |
| `AuditAction` | `LOGIN_SUCCESS`, `LOGIN_FAILED`, `USER_CREATED`, `USER_UPDATED`, `USER_ACTIVATED`, `USER_DISABLED`, `USER_PASSWORD_RESET`, `ROLE_CREATED`, `ROLE_UPDATED`, `ROLE_DELETED`, `ROLE_ASSIGNED`, `ROLE_REMOVED`, `BRANCH_CREATED`, `BRANCH_UPDATED`, `BRANCH_DISABLED`, `COMPANY_UPDATED` |

`Permission` porte son module et son libellé — c'est ce qui rend la matrice UI générique :

```java
public enum Permission {
    USER_VIEW(PermissionModule.USERS, "View users"),
    USER_CREATE(PermissionModule.USERS, "Create users"),
    ...
}
```

`SystemRole` porte son jeu de permissions par défaut :

```java
public enum SystemRole {
    COMPANY_ADMIN("Company Administrator", EnumSet.allOf(Permission.class)),
    SALES_MANAGER("Sales Manager", EnumSet.of(CUSTOMER_VIEW, CUSTOMER_CREATE, ...)),
    ...
}
```

---

## 5. Architecture des services

Aucune couche nouvelle. `Controller → Service → Repository`, comme le reste du projet.

| Service | Responsabilité |
|---|---|
| `AuthService` | login (vérifie mot de passe + statut), inscription entreprise, `/me`, changement de mot de passe |
| `CompanyService` | création de l'entreprise + seed des rôles système + premier admin ; lecture/màj des paramètres |
| `BranchService` | CRUD branches, toujours filtré par entreprise |
| `UserService` | CRUD utilisateurs, activation/désactivation, reset password, **affectation des rôles** |
| `RoleService` | CRUD rôles, édition des permissions, protection des rôles système, catalogue de permissions |
| `AuditService` | `record(action, entityType, entityId, detail)` + lecture filtrée |

`AuditService.record(...)` est appelé **explicitement** dans les services, exactement comme
l'actuel `UserService.record(...)`. Pas d'AOP, pas d'écouteur d'entité.

```java
auditService.record(AuditAction.ROLE_ASSIGNED, "USER", user.getId(),
        "Role " + role.getName() + " assigned");
```

---

## 6. Architecture de sécurité Spring Security

### Dépendances à ajouter

```xml
spring-boot-starter-security
io.jsonwebtoken:jjwt-api / jjwt-impl / jjwt-jackson  (0.12.x)
spring-security-test                                  (scope test)
```

> Spring Boot 4 : l'auto-configuration sécurité vit dans `spring-boot-starter-security`.
> `@PreAuthorize` reste inactif sans `@EnableMethodSecurity` explicite.

### Chaîne de filtres

```
Request
  │
  ├─ CorsFilter                    (config existante de WebConfig, activée via http.cors())
  │
  ├─ JwtAuthenticationFilter       lit "Authorization: Bearer <token>"
  │     ├─ JwtService.parse()      → userId
  │     ├─ UserRepository.findByIdWithRoles(userId)
  │     ├─ statut != ACTIVE        → 401
  │     └─ SecurityContext ← CompanyUserDetails
  │
  ├─ Autorisation                  @PreAuthorize("hasAuthority('CUSTOMER_CREATE')")
  │
  └─ Controller
```

```java
http
  .csrf(csrf -> csrf.disable())                 // API stateless, pas de cookie de session
  .cors(Customizer.withDefaults())
  .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
  .authorizeHttpRequests(a -> a
      .requestMatchers("/api/auth/login", "/api/auth/register").permitAll()
      .anyRequest().authenticated())
  .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
```

### Contenu du JWT — volontairement minimal

```json
{ "sub": "42", "companyId": 7, "exp": ... }
```

**Les permissions ne sont pas dans le token.** Elles sont rechargées depuis la base à chaque
requête. Une requête de plus par appel, en échange de :

- un utilisateur désactivé perd l'accès **immédiatement** (critère 6) ;
- un rôle modifié s'applique **immédiatement** (critère 15, pas d'escalade via un vieux token) ;
- pas de token à révoquer, pas de liste noire à gérer.

C'est le bon compromis pour un ERP PME. Un cache pourra être ajouté le jour où le besoin est
mesuré, pas avant.

### `CompanyUserDetails`

```java
userId, companyId, username, passwordHash, status
authorities = permissions cumulées de tous les rôles → SimpleGrantedAuthority("SALE_CREATE")
```

Les **rôles ne sont pas des authorities** : seules les permissions le sont. Un seul vocabulaire
dans `@PreAuthorize`, aucune confusion `ROLE_` / `hasRole` / `hasAuthority`.

### `CurrentUser` (bean injectable)

```java
@Component
public class CurrentUser {
    public Long id();
    public Long companyId();
    public String username();
    public boolean has(Permission permission);
}
```

---

## 7. Endpoints REST

| Méthode | URL | Permission |
|---|---|---|
| `POST` | `/api/auth/register` | public — crée entreprise + premier `COMPANY_ADMIN` |
| `POST` | `/api/auth/login` | public |
| `GET` | `/api/auth/me` | authentifié |
| `PATCH` | `/api/auth/change-password` | authentifié (soi-même) |
| `GET` | `/api/company` | `COMPANY_VIEW` |
| `PUT` | `/api/company` | `COMPANY_UPDATE` |
| `GET` | `/api/branches` | `BRANCH_VIEW` |
| `POST` | `/api/branches` | `BRANCH_CREATE` |
| `PUT` | `/api/branches/{id}` | `BRANCH_UPDATE` |
| `PATCH` | `/api/branches/{id}/activate` · `/deactivate` | `BRANCH_DISABLE` |
| `GET` | `/api/users` · `/api/users/{id}` | `USER_VIEW` |
| `POST` | `/api/users` | `USER_CREATE` |
| `PUT` | `/api/users/{id}` | `USER_UPDATE` — `roleIds` fait partie du corps |
| `PATCH` | `/api/users/{id}/activate` · `/deactivate` | `USER_DISABLE` |
| `PATCH` | `/api/users/{id}/reset-password` | `USER_UPDATE` |
| `GET` | `/api/roles` · `/api/roles/{id}` | `ROLE_VIEW` |
| `GET` | `/api/roles/permissions` | `ROLE_VIEW` — catalogue groupé par module |
| `POST` | `/api/roles` | `ROLE_CREATE` |
| `PUT` | `/api/roles/{id}` | `ROLE_UPDATE` |
| `DELETE` | `/api/roles/{id}` | `ROLE_DELETE` |
| `GET` | `/api/audit-logs` | `AUDIT_VIEW` — filtres action / entityType / entityId / dates |
| `POST` | `/api/platform/auth/login` | public — jamais d'inscription pour ce rôle |
| `GET` | `/api/platform/auth/me` | `PLATFORM_ADMIN` |
| `GET` | `/api/platform/companies` | `PLATFORM_ADMIN` — toutes les entreprises, paginé |
| `GET` | `/api/platform/companies/{id}` | `PLATFORM_ADMIN` |
| `PUT` | `/api/platform/companies/{id}/modules` | `PLATFORM_ADMIN` — remplace `enabledModules` |

`DELETE /api/users/{id}` est **supprimé** : la désactivation est le cycle de vie normal
(exigence 5) et un utilisateur supprimé casserait les journaux d'audit.

Il n'y a pas d'endpoint séparé pour les rôles d'un utilisateur : `roleIds` est un champ de
`UserRequest`. Créer un utilisateur et choisir son rôle est donc un seul appel, un seul
formulaire — c'est le parcours décrit au §19 du besoin.

`/api/company` est au singulier et sans id : un utilisateur n'a accès qu'à la sienne, jamais par
identifiant. C'est l'isolation exprimée dans l'URL elle-même.

---

## 8. Stratégie de vérification des permissions

Deux vérifications indépendantes, aucune ne remplace l'autre.

**1. La permission — déclarative, sur le contrôleur**

```java
@PostMapping
@PreAuthorize("hasAuthority('BRANCH_CREATE')")
public BranchResponse create(@Valid @RequestBody BranchRequest request) { ... }
```

**2. L'entreprise — explicite, dans le repository**

```java
Optional<Branch> findByIdAndCompanyId(Long id, Long companyId);
Page<Branch> findByCompanyId(Long companyId, Pageable pageable);
```

```java
Branch branch = branchRepository.findByIdAndCompanyId(id, currentUser.companyId())
        .orElseThrow(() -> ResourceNotFoundException.of("Branch", id));
```

Pas de filtre Hibernate, pas de `@TenantId`, pas d'aspect : le `companyId` est visible dans la
signature de chaque requête. C'est plus verbeux et c'est voulu — une règle de sécurité qu'on lit
vaut mieux qu'une règle qu'on suppose active.

**Le `companyId` ne vient jamais de la requête HTTP.** Toujours de `currentUser.companyId()`,
donc du token vérifié. Aucun DTO d'entrée ne contient de `companyId`.

**Une ressource d'une autre entreprise renvoie 404, jamais 403** — un 403 confirmerait son
existence.

### Règles anti-escalade

| Règle | Mise en œuvre |
|---|---|
| On ne modifie pas ses propres rôles | `PUT /users/{id}` refuse un changement de `roleIds` si `id == currentUser.id()` → 422 |
| On ne se désactive pas soi-même | idem sur `deactivate` |
| On n'accorde pas une permission qu'on n'a pas | `UserService` (affectation) **et** `RoleService` (création/édition) vérifient que les permissions demandées ⊆ permissions de l'appelant |
| Les rôles système sont immuables | `role.isSystem()` → refus sur update/delete |
| Une entreprise garde au moins un admin actif | refus de retirer le dernier `COMPANY_ADMIN` actif |
| Une entreprise garde au moins une branche active | refus de désactiver la dernière |
| Un rôle utilisé ne disparaît pas sous ses porteurs | suppression refusée tant que `userCount > 0` |

---

## 9. Structure Angular

```
core/
├── auth/
│   ├── auth.service.ts          signal session, login, logout, has(permission)
│   ├── auth.interceptor.ts      Bearer + 401 → logout & redirect /login
│   ├── auth.guard.ts            authGuard (+ redirectTo) et permissionGuard
│   └── session.model.ts         SessionUser + union typée Permission
├── services/
│   ├── role.service.ts   branch.service.ts   company.service.ts   audit.service.ts
│   └── user.service.ts          (existant, étendu)
│
shared/
└── directives/has-permission.directive.ts     *appHasPermission="'CUSTOMER_CREATE'"
│
features/
├── auth/
│   ├── login/                   page hors MainLayout
│   └── register/                inscription entreprise
├── roles/
│   ├── roles.component.*        liste des rôles (badge « System »)
│   ├── role-form/               nom + description + matrice
│   └── permission-matrix/       composant réutilisable, construit depuis le catalogue
├── branches/
├── audit/                       liste filtrable
└── users/                       existant, étendu (multi-rôles au lieu d'un rôle)
```

`AuthService` expose la session dans un `signal` ; la sidebar et la directive le lisent.
Le token est en `localStorage` (V1, cohérent avec un SPA sans SSR).

### Menu dynamique

`NavItem` gagne un champ optionnel :

```typescript
{ label: 'Roles', icon: 'pi pi-key', route: '/roles', permission: 'ROLE_VIEW' }
```

La sidebar filtre `NAVIGATION` avec `authService.has(...)` ; un groupe dont tous les enfants sont
masqués disparaît. Les routes portent la même permission dans `data` et sont protégées par
`permissionGuard` — le masquage du menu est du confort, le garde est la vraie barrière côté client,
et le backend reste la seule source de vérité.

---

## 10. Matrice de permissions

| Module | Permissions |
|---|---|
| **Users** | `USER_VIEW` · `USER_CREATE` · `USER_UPDATE` · `USER_DISABLE` |
| **Roles** | `ROLE_VIEW` · `ROLE_CREATE` · `ROLE_UPDATE` · `ROLE_DELETE` |
| **Branches** | `BRANCH_VIEW` · `BRANCH_CREATE` · `BRANCH_UPDATE` · `BRANCH_DISABLE` |
| **Customers** | `CUSTOMER_VIEW` · `CUSTOMER_CREATE` · `CUSTOMER_UPDATE` · `CUSTOMER_DELETE` |
| **Suppliers** | `SUPPLIER_VIEW` · `SUPPLIER_CREATE` · `SUPPLIER_UPDATE` · `SUPPLIER_DELETE` |
| **Products** | `PRODUCT_VIEW` · `PRODUCT_CREATE` · `PRODUCT_UPDATE` · `PRODUCT_DELETE` |
| **Sales** | `SALE_VIEW` · `SALE_CREATE` · `SALE_UPDATE` · `SALE_CANCEL` |
| **Purchases** | `PURCHASE_VIEW` · `PURCHASE_CREATE` · `PURCHASE_UPDATE` · `PURCHASE_CANCEL` |
| **Stock** | `STOCK_VIEW` · `STOCK_ADJUST` · `STOCK_TRANSFER` |
| **Company** | `COMPANY_VIEW` · `COMPANY_UPDATE` |
| **Security** | `AUDIT_VIEW` |

**Ventes (`/api/sales-documents`)** — lecture (liste, détail, `preview`) : `SALE_VIEW` · création
et conversion devis → commande : `SALE_CREATE` · modification, suppression d'un brouillon, émission,
changement de statut (accepter / rejeter / confirmer) : `SALE_UPDATE` · annulation d'une commande :
`SALE_CANCEL`. Pas de permission de suppression : seul un brouillon disparaît, un document émis
s'annule ou se rejette.

**Facture** (même endpoint) — création et « créer depuis un devis / une commande / un bon » : `SALE_CREATE` ·
émission (elle sort les biens du stock) : `SALE_UPDATE` · annulation d'une facture impayée : `SALE_CANCEL`. Son statut de
règlement (impayée, partiellement payée, payée) ne se change jamais à la main : il suit les paiements.

**Avoir** (même endpoint) — « créer depuis une facture » : `SALE_CREATE` · émission (il s'impute sur la facture) :
`SALE_UPDATE` · annulation : `SALE_CANCEL`. Aucun droit de stock : un avoir ne touche pas au stock.

**Paiements (`/api/payments`)** — pas de permission propre, ils suivent les ventes : lecture : `SALE_VIEW` ·
enregistrer un paiement : `SALE_UPDATE` · l'annuler : `SALE_CANCEL`. Un paiement n'a ni modification ni suppression.

**Bon de livraison** (même endpoint) — création et « créer depuis une commande » : `SALE_CREATE` ·
émission et passage à « livré » : `SALE_UPDATE` · annulation : `SALE_CANCEL`. Livrer ou annuler un bon livré
écrit dans le registre de stock : aucun droit de stock n'est demandé, l'effet découle de
`SALE_UPDATE` / `SALE_CANCEL` (comme la réservation d'une commande).

**Achats (`/api/purchase-documents`)** — le miroir des ventes : lecture (liste, détail, `preview`) :
`PURCHASE_VIEW` · création et conversion commande → bon de réception : `PURCHASE_CREATE` · modification,
suppression d'un brouillon et **validation** : `PURCHASE_UPDATE` · annulation : `PURCHASE_CANCEL`. Valider ou
annuler un bon de réception écrit dans le registre de stock : cela ne demande **aucun droit de stock** — comme
la réservation d'une commande, l'effet découle de `PURCHASE_UPDATE` / `PURCHASE_CANCEL`.

**Bon de retour** (client : `/api/sales-documents`, fournisseur : `/api/purchase-documents`) — création et « créer depuis un
bon de livraison / une facture / un bon de réception » : `SALE_CREATE` ou `PURCHASE_CREATE` · émission / validation (elle écrit
dans le registre de stock : entrée pour un retour client, sortie pour un retour fournisseur) : `SALE_UPDATE` ou `PURCHASE_UPDATE` ·
annulation : `SALE_CANCEL` ou `PURCHASE_CANCEL`. Aucun droit de stock n'est demandé : l'effet découle du droit sur le document.

**Avoir fournisseur** (même endpoint) — « créer depuis une facture » : `PURCHASE_CREATE` · validation (il s'impute sur la facture) :
`PURCHASE_UPDATE` · annulation : `PURCHASE_CANCEL`. Aucun droit de stock : un avoir ne touche pas au stock.

**Facture d'achat** (même endpoint `/api/purchase-documents`) — création et « créer depuis une commande / un bon de
réception » : `PURCHASE_CREATE` · validation (elle fait entrer les biens) : `PURCHASE_UPDATE` · annulation d'une facture
impayée : `PURCHASE_CANCEL`. **Paiements fournisseur (`/api/supplier-payments`)** — pas de permission propre : lecture
`PURCHASE_VIEW` · enregistrer un paiement `PURCHASE_UPDATE` · l'annuler `PURCHASE_CANCEL`. Ni modification ni suppression.

**Stock (`/api/stock`, `/api/warehouses`)** — lecture (niveaux, registre, entrepôts) : `STOCK_VIEW` ·
entrée, sortie, ajustement d'inventaire et gestion des entrepôts : `STOCK_ADJUST` · transfert entre
entrepôts : `STOCK_TRANSFER`. Le registre n'a ni modification ni suppression : une erreur se corrige par
un mouvement inverse. Les réservations faites par une commande confirmée ne demandent aucun droit de
stock — elles découlent de `SALE_UPDATE`.

**Tableau de bord (`/api/dashboard/*`)** — pas de permission propre : chaque domaine demande celle de son module.
`/sales` : `SALE_VIEW` · `/purchases` : `PURCHASE_VIEW` · `/stock` : `STOCK_VIEW`. Un comptable (Sales + Purchases en lecture) voit
ventes et achats mais pas le stock ; une entreprise sans le module répond 403. La page d'accueil ne demande — et n'affiche — que
les domaines que la personne a le droit de voir.

**Impression (`GET /api/print-profile`)** — `SALE_VIEW`, `PURCHASE_VIEW` ou `COMPANY_VIEW` : ce que la feuille
imprimée dit de l'entreprise (identité, matricule, logo, cachet, comptes bancaires **cochés « afficher sur les
documents »** — jamais les autres). Un commercial ou un acheteur n'a pas `COMPANY_VIEW` : sans cette route ils ne
pourraient pas imprimer. La feuille lit aussi le client / le fournisseur par `GET /api/customers/{id}` /
`GET /api/suppliers/{id}` (`CUSTOMER_VIEW` / `SUPPLIER_VIEW`, que ces rôles ont déjà). Les routes `/print/sales/:id` et
`/print/purchases/:id` demandent `SALE_VIEW` / `PURCHASE_VIEW` — confort d'affichage, le backend revérifie chaque appel.

**Lecture de la liste des taxes** — `GET /api/settings/taxes` accepte aussi `SALE_CREATE`,
`SALE_UPDATE`, `PURCHASE_CREATE`, `PURCHASE_UPDATE`, `PRODUCT_CREATE` et `PRODUCT_UPDATE` : un commercial n'a pas `COMPANY_VIEW` mais ne
peut pas rédiger un devis sans cette liste. Seule la liste est ouverte — lire une taxe par id et
toute écriture restent sous `COMPANY_VIEW` / `COMPANY_UPDATE`.

38 permissions. Les modules Customers → Stock sont déclarés maintenant et deviendront actifs quand
les modules métier arriveront (Phases 3 à 6) : les contrôleurs futurs n'auront qu'à poser
l'annotation, sans toucher au RBAC (critère 16).

### Rôles système par défaut

| Rôle | Permissions |
|---|---|
| `COMPANY_ADMIN` | toutes |
| `SALES_MANAGER` | Customers (tout) · Sales (tout) · Products/Stock en lecture |
| `SALES_AGENT` | `CUSTOMER_VIEW` `CUSTOMER_CREATE` `SALE_VIEW` `SALE_CREATE` `PRODUCT_VIEW` `STOCK_VIEW` |
| `PURCHASE_MANAGER` | Suppliers (tout) · Purchases (tout) · Products/Stock en lecture |
| `STOCK_MANAGER` | Stock (tout) · Products (tout) · Sales/Purchases en lecture |
| `ACCOUNTANT` | lecture sur Customers, Suppliers, Sales, Purchases, Products |
| `VIEWER` | toutes les permissions `*_VIEW` métier |

Les rôles système sont resynchronisés au démarrage depuis l'enum `SystemRole` : ajouter une
permission à un rôle système la propage à toutes les entreprises existantes.

### Nom d'un rôle personnalisé

L'administrateur ne saisit qu'un libellé. Le nom stable est dérivé automatiquement, accents
retirés : « Responsable dépôt » → `RESPONSABLE_DEPOT`. Ce nom est fixé à la création — renommer
un rôle ne change que le libellé affiché, jamais sa clé.

### Écran de la matrice

Rendu à partir de `GET /api/roles/permissions`, groupé par module, une ligne par module et une
case par action. Cases à cocher PrimeNG + « tout cocher » par ligne. Aucun terme technique visible :
libellés (« View users ») et non noms d'enum.

`GET /api/roles` n'est **pas paginé** : une entreprise a une poignée de rôles et le formulaire
utilisateur en a besoin de la liste complète. Les branches, elles, suivent la règle REST
habituelle et sont paginées.

---

## 11. Règles d'isolation Company / Tenant

1. `company_id` est **NOT NULL** sur toute table métier — y compris `roles` et `audit_logs`.
2. Toute méthode de repository qui lit ou écrit une donnée métier prend un `companyId`.
   Aucun usage de `findById`, `findAll`, `existsById` nus dans un service métier.
3. Le `companyId` provient exclusivement de `CurrentUser`, jamais d'un paramètre client.
4. Les contraintes d'unicité métier sont **portées par l'entreprise** :
   `UNIQUE (company_id, code)`, `UNIQUE (company_id, name)`, `UNIQUE (company_id, username)`.
   Seul `users.email` reste globalement unique — c'est l'identifiant de connexion.
5. Un accès à une ressource d'une autre entreprise → `ResourceNotFoundException` (404).
6. Chaque entité RBAC (rôle, branche) est vérifiée comme appartenant à l'entreprise de l'appelant
   **avant** toute association (ex. affecter un rôle à un utilisateur).
7. `CompanyIsolationTest` : deux entreprises, chaque endpoint tenté en croisé, 404 partout,
   et aucune liste ni journal d'audit ne laisse filtrer une ligne du voisin.

### Comment on sait que ça tient

Ces règles sont prouvées par des tests d'intégration qui traversent la vraie chaîne HTTP,
la vraie sécurité et une vraie base PostgreSQL — un `findById` qui oublie son `companyId`
ne se voit pas avec des mocks.

Contrôle de la suite : en remplaçant une seule ligne de `UserService`
(`findByIdAndCompanyId` → `findById`), `CompanyIsolationTest` produit **6 échecs**, dont la
réinitialisation réussie du mot de passe d'un utilisateur d'une autre entreprise. Une suite
verte qui ne rougit jamais ne prouve rien : celle-ci a été vérifiée.

---

## 12. Audit logs

Enregistré par `AuditService.record(...)`, appelé explicitement là où l'événement se produit.

| Champ | Source |
|---|---|
| `companyId` | `CurrentUser.companyId()` (ou l'entreprise ciblée au login) |
| `userId` / `username` | `CurrentUser` — `null` / saisie brute pour `LOGIN_FAILED` |
| `action` | `AuditAction` |
| `entityType` / `entityId` | `"USER"` / `42` |
| `detail` | phrase lisible : « Role SALES_MANAGER assigned » |
| `ipAddress` | `HttpServletRequest.getRemoteAddr()` |
| `occurredAt` | `@PrePersist` |

Événements couverts : connexions réussies et échouées, cycle de vie utilisateur, cycle de vie des
rôles, affectation/retrait de rôle, modification des permissions d'un rôle, cycle de vie des
branches, modification des paramètres d'entreprise.

Consultation : `GET /api/audit-logs` (permission `AUDIT_VIEW`), filtrable par action, type
d'entité, utilisateur et période.

L'onglet « History » du drawer utilisateur passe par `GET /api/users/{id}/history`, gardé par
**`USER_VIEW`** et non `AUDIT_VIEW` : consulter l'historique d'un utilisateur qu'on a déjà le
droit de gérer fait partie de sa gestion. L'écran d'audit complet, lui, reste réservé.

`LOGIN_FAILED` est écrit dans une transaction séparée (`REQUIRES_NEW`) afin de survivre à
l'échec de l'authentification. Il n'est enregistré que si le compte visé **existe** : un e-mail
inconnu n'appartient à aucune entreprise, et chaque ligne d'audit appartient à exactement une.

⚠️ Les bornes de dates ne sont **jamais** passées à `NULL` : PostgreSQL ne peut pas déduire le
type d'un paramètre timestamp nul dans `:from IS NULL`, exactement comme pour un `NULL` dans
`LOWER()`. `AuditService` élargit une borne absente au lieu de la laisser nulle.

---

## 13. Plan d'implémentation

| Tranche | Contenu | Statut |
|---|---|---|
| **A — Socle multi-entreprise + authentification** | `companies`, `branches`, `roles`, `user_roles` ; `Permission` / `SystemRole` ; Spring Security + JWT ; `/api/auth/*` ; module `user` multi-rôles isolé par entreprise ; `@PreAuthorize` | ✅ |
| **B — Rôles & branches (backend)** | CRUD des rôles + catalogue de permissions + règles anti-escalade à l'édition, CRUD branches, paramètres d'entreprise | ✅ |
| **C — Frontend authentification** | page de login, inscription, `authInterceptor`, `authGuard` + `permissionGuard`, session en signal | ✅ |
| **D — Frontend administration** | écrans Roles + matrice de permissions, users multi-rôles, branches, menu dynamique, `*appHasPermission` | ✅ |
| **E — Audit** | `audit_logs`, `AuditService`, écran de consultation, remplacement de `user_history` | ✅ |
| **F — Vérification** | `CompanyIsolationTest`, tests d'autorisation, tests des règles anti-escalade | ✅ |

Les tranches A et B initialement prévues ont été fusionnées : dès que `users.company_id` est
`NOT NULL`, le module utilisateurs a besoin de `CurrentUser` pour savoir dans quelle entreprise
écrire. Il n'existe pas de découpage intermédiaire qui compile.

### Migrations Flyway

```
V3__create_companies_and_branches.sql      ✅
V4__create_roles_and_permissions.sql       ✅
V5__link_users_to_company_and_roles.sql    ✅
V6__create_audit_logs.sql                  🔲 (+ DROP TABLE user_history)
```

---

## 14. Ce qui n'est volontairement pas fait

Permissions par branche · scopes · permissions temporaires ou conditionnelles · ABAC ·
permissions individuelles par utilisateur · hiérarchie de rôles · délégation · workflow
d'approbation · refresh token (V1 : un seul access token, durée 8 h) · Redis/cache de permissions.

---

## 15. Plateforme et modules par entreprise

Un second axe, orthogonal au RBAC : le RBAC répond à « qui dans l'entreprise peut faire
quoi », pas à « quels modules l'entreprise a-t-elle le droit d'utiliser ». Sans ce second
axe, dès qu'un module métier (Ventes, Stock...) existe, **toutes** les entreprises y ont
accès automatiquement — impossible de vendre « Stock seul » à un client.

```
Permission (RBAC)        → qui, dans l'entreprise, peut faire quoi
Module (entitlement)     → quels modules l'entreprise a-t-elle le droit d'utiliser, tout court
```

### Compte plateforme — hors modèle tenant

`PlatformAdmin` ne porte aucun `company_id` : c'est le seul type de compte autorisé à
voir plusieurs entreprises. Il vit dans un package et un token entièrement séparés de
`User` :

```json
// Token entreprise
{ "sub": "42", "companyId": 7 }
// Token plateforme
{ "sub": "1", "platform": true }
```

`JwtAuthenticationFilter` authentifie l'un ou l'autre selon ce que `JwtService.parse()`
trouve dans le token, jamais les deux. Un token plateforme n'obtient que l'autorité
`PLATFORM_ADMIN` — aucune permission métier. Un token entreprise n'obtient jamais
`PLATFORM_ADMIN`. La séparation repose entièrement sur `@PreAuthorize`, sans mécanisme
nouveau : deux vocabulaires d'autorités qui ne se recoupent jamais.

Premier compte créé une seule fois au démarrage (`PlatformAdminBootstrap`), depuis
`PLATFORM_ADMIN_EMAIL` / `PLATFORM_ADMIN_PASSWORD` — **aucune inscription en libre-service**
pour ce rôle, contrairement à `/api/auth/register`. Ces variables n'ont **aucune valeur par
défaut** dans `application.properties` : un identifiant commité ouvrirait toutes les entreprises.
Sans elles et sans compte existant, le démarrage le signale en `WARN` et le portail reste fermé.

### `BusinessModule` — le grain qu'un admin plateforme manipule, pas celui du RBAC

Deux granularités coexistent, et il ne faut jamais les confondre :

```
PermissionModule (role/)    → catalogue RBAC fin : 11 valeurs, dont USERS, SUPPLIERS, STOCK...
                               sert à construire la matrice de permissions d'un rôle
BusinessModule (company/)   → ce qu'un admin plateforme active/désactive : 4 valeurs
                               CUSTOMERS, SALES, PURCHASES, INVENTORY
```

Le premier essai avait exposé les 6 `PermissionModule` activables (`CUSTOMERS`,
`SUPPLIERS`, `PRODUCTS`, `SALES`, `PURCHASES`, `STOCK`) directement comme cases à cocher
côté plateforme — trop fin : ça aurait permis d'activer `SUPPLIERS` sans `PURCHASES`, une
combinaison qui ne correspond à aucun écran réel de l'application. `BusinessModule`
corrige ça en regroupant au niveau où un utilisateur navigue réellement :

```java
CUSTOMERS  → { CUSTOMERS }
SALES      → { SALES }
PURCHASES  → { PURCHASES, SUPPLIERS }   // un seul bouton, deux permissions RBAC en dessous
INVENTORY  → { PRODUCTS, STOCK }        // idem
```

Basculer `PURCHASES` déplace toujours `PURCHASES` et `SUPPLIERS` ensemble — jamais l'un
sans l'autre. Le RBAC (`Permission`, `PermissionModule`, la matrice de permissions d'un
rôle) reste inchangé et toujours aussi fin : un `SALES_MANAGER` continue de n'avoir que
`SALE_VIEW`/`SALE_CREATE`, pas `SUPPLIER_*`. Les deux axes ne se recoupent jamais :
l'un décide *quoi* l'entreprise a acheté, l'autre décide *qui* dans l'entreprise peut
s'en servir.

`Company.enabledModules` reste stocké en base comme un `Set<PermissionModule>` (fin,
6 valeurs possibles, **aucune migration nécessaire** pour ce changement) — `BusinessModule`
n'existe que dans les couches service/API qui parlent à un humain : le portail plateforme
et la session d'un utilisateur entreprise. La traduction est centralisée dans une seule
méthode, `BusinessModule.enabledAmong(Set<PermissionModule>)`, utilisée par les deux :
`PlatformCompanyService` (toutes les entreprises, vue admin) et `AuthService.toSession()`
(sa propre entreprise, vue utilisateur). `updateModules()` fait le trajet inverse :
il explose chaque `BusinessModule` choisi en ses `PermissionModule` avant d'écrire.

Les modules administratifs (`USERS`, `ROLES`, `BRANCHES`, `COMPANY`, `SECURITY`) ne sont
représentés dans **aucun** `BusinessModule` — impossible de les toucher via l'endpoint
plateforme, la contrainte est portée par le système de types plutôt que par une règle
métier à vérifier à l'exécution.

À l'inscription : tous les modules (`BusinessModule.allPermissionModules()`). Un admin
plateforme les restreint ensuite via `PUT /api/platform/companies/{id}/modules`.

### Le menu ne montre que ce que l'entreprise a le droit d'utiliser

`GET /api/auth/me` renvoie `enabledModules` — les `BusinessModule` de **sa propre**
entreprise, calculés avec la même méthode que côté plateforme. Côté Angular,
`NavItem.module` (dans `navigation.ts`) et `AuthService.hasModule()` filtrent la sidebar
exactement comme `NavItem.permission` le fait déjà pour les permissions : un groupe
« Sales » sans le module `SALES` disparaît entièrement, pas seulement grisé. Un admin
d'entreprise restreint à Purchases + Inventory ne voit donc que ces deux groupes dans
« Operations » — jamais Sales ni Customers, même comme entrée « Soon ».

Le menu est du confort d'affichage. La barrière réelle est côté serveur, et elle passe par les
permissions elles-mêmes :

### Un module désactivé est refusé par le serveur, sur tous ses endpoints

`User.collectPermissions()` — la seule source des autorités que lit chaque `@PreAuthorize`, et
des permissions de la session — **retire** les permissions d'un module que l'entreprise n'a pas le
droit d'utiliser (`Company.allows(permission)` : une permission d'un module administratif est
toujours permise, une permission d'un module métier seulement si `enabledModules` le contient).
Conséquences :

- aucun contrôleur n'a rien à faire : `SALE_*`, `PURCHASE_*`, `SUPPLIER_*`, `CUSTOMER_*`, `PRODUCT_*`,
  `STOCK_*` disparaissent tous ensemble, donc **403** sur les ventes, les paiements clients, les
  achats, les paiements fournisseur, les fournisseurs, les clients, les produits, le stock et les
  entrepôts — y compris pour les endpoints futurs, qui héritent de la règle en posant leur
  `hasAuthority(...)` habituel ;
- l'effet est immédiat, dans les deux sens : les permissions sont relues à chaque requête, le même
  jeton est refusé puis accepté à nouveau quand un admin plateforme bascule le module ;
- la session (`GET /api/auth/me`) ne liste plus ces permissions : le frontend (`*appHasPermission`,
  `permissionGuard`) et le serveur disent la même chose ;
- un module désactivé ne peut pas non plus être redistribué : l'administrateur ne détient plus la
  permission, il ne peut donc pas la donner à un rôle (règle « on ne donne pas ce qu'on n'a pas ») ;
- les modules administratifs (utilisateurs, rôles, agences, entreprise, sécurité) restent
  toujours disponibles, même pour une entreprise sans aucun module métier.

À noter : la validation du corps d'une requête (`@Valid`) s'exécute avant le contrôle de permission,
donc un corps invalide sur un module désactivé renvoie 400 et non 403 — rien n'est révélé.

### `PlatformCompanyService` — l'exception délibérée

Toute cette conception (§11) repose sur « jamais un `companyId` du client, toujours
`currentUser.companyId()` ». `PlatformCompanyService` est le seul endroit qui déroge à
cette règle — il lit une `Company` par un id de chemin, sans aucun filtrage. C'est
correct **uniquement** parce que l'appelant est un `PlatformAdmin`, qui par construction
n'appartient à aucune entreprise : il n'y a rien à filtrer par rapport à. Cette exception
est commentée dans le code (`PlatformCompanyController`, `PlatformCompanyService`) et
couverte par `PlatformAuthorizationTest`.

### Journal d'audit d'une action plateforme

Une modification de modules par un admin plateforme est enregistrée dans le journal
**de l'entreprise ciblée** (`AuditService.recordForPlatform`), avec `userId = null` et
`username = "[platform] admin@example.com"` — visible et non ambigu pour l'entreprise
qui consulte son propre historique, sans confondre l'admin plateforme avec un de ses
employés.

### Pas d'essai gratuit, pas de facturation — pour l'instant

Décision explicite : à ce stade, un humain (l'admin plateforme) décide des modules,
manuellement, sans notion de plan tarifaire ni de date d'expiration. Le jour où des
packs automatiques ou un essai limité dans le temps arrivent, seul ce qui *peuple*
`enabledModules` change (un checkout ou un job planifié, au lieu d'un clic d'admin) —
la vérification elle-même (traduction `BusinessModule` → `PermissionModule`, l'audit)
reste identique. C'est pour cette raison que `enabledModules` est modélisé comme un
ensemble de données ordinaire plutôt que comme un champ `plan`
figé : un pack n'est jamais qu'un nom donné à un ensemble de modules prédéfini.

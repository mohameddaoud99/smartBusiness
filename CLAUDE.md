# CLAUDE.md — Règles de travail pour Claude Code

## Mise à jour de la documentation

Ces fichiers ne se mettent PAS à jour automatiquement.
À la fin de chaque tâche, mettre à jour les fichiers concernés :

| Fichier | Mettre à jour quand... |
|---|---|
| `docs/ROADMAP.md` | Une tâche est terminée (🔲 → ✅) |
| `docs/ARCHITECTURE.md` | Un nouveau package, dossier ou flux est ajouté |
| `docs/DEVELOPMENT.md` | Une nouvelle convention de code est établie |
| `docs/UI-UX.md` | Un nouveau composant PrimeNG est introduit |
| `docs/RBAC.md` | Une permission, un rôle système ou une règle de sécurité change |
| `CLAUDE.md` | Une nouvelle règle absolue est décidée |

`README.md` et `docs/PROJECT.md` ne se mettent à jour QUE sur demande explicite (portfolio, présentation
à un recruteur...) — jamais en routine à la fin d'une tâche, contrairement aux autres fichiers de cette
table. Dernière mise à jour : 2026-09-26, pour un usage portfolio (voir `docs/screenshots/`).

**Langue de la documentation : bilingue, volontairement.** `README.md`, `docs/PROJECT.md` et
`docs/ARCHITECTURE.md` sont en ANGLAIS (lecture recruteur/international). `docs/DEVELOPMENT.md`,
`docs/RBAC.md`, `docs/UI-UX.md`, `docs/ROADMAP.md` et ce fichier restent en FRANÇAIS (langue de travail
avec l'utilisateur). Ne jamais retraduire l'un vers l'autre sans demande explicite — mettre à jour un de
ces fichiers, c'est le faire dans SA langue actuelle, pas dans celle du reste de la table.

---

## Documents à lire avant toute tâche

Lis ces fichiers au début de chaque session avant d'écrire du code :

| Document | Quand le lire |
|---|---|
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Toujours — structure backend et frontend |
| [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) | Toujours — règles de code à respecter |
| [`docs/UI-UX.md`](docs/UI-UX.md) | Avant toute tâche frontend |
| [`docs/RBAC.md`](docs/RBAC.md) | Toujours — sécurité, permissions, isolation par entreprise |
| [`docs/ROADMAP.md`](docs/ROADMAP.md) | Pour savoir ce qui est fait / à faire |
| [`docs/PROJECT.md`](docs/PROJECT.md) | Pour comprendre le contexte métier |

---

## Contexte du projet

Mini ERP / Gestion Commerciale.
Développeur : ~2 ans d'expérience professionnelle.
Objectif : code simple, lisible, maintenable. Pas d'over-engineering.

---

## Stack — ne jamais changer

### Backend
- Java 21 + Spring Boot 4.0.7 + Maven
- Spring Web, Spring Data JPA, Hibernate
- PostgreSQL + Flyway
- Bean Validation, Lombok, MapStruct
- Package racine : `com.sales.smartBusiness`

### Frontend
- Angular 19.1.0 — Standalone Components uniquement
- PrimeNG 19.x avec thème Aura — seule librairie UI autorisée
- Reactive Forms + HttpClient + RxJS
- `html2pdf.js` — export d'une feuille imprimée en fichier PDF (chargé à la demande, seulement au clic
  sur « Download PDF » ; c'est la seule librairie hors Angular / PrimeNG / RxJS)

### Build
- Maven compile : `JAVA_HOME=C:\Users\moham\.jdks\ms-21.0.12`
- Le fichier `.mvn/jvm.config` règle l'SSL (Windows trust store)
- Angular build : `npx ng build --configuration development`

---

## Règles absolues

### Ce qu'il ne faut JAMAIS faire
- Ne jamais introduire Angular Material, Bootstrap, Tailwind, ng-bootstrap
- Ne jamais ajouter une seconde librairie UI côté frontend
- Ne jamais créer de microservices, CQRS, Event Sourcing, Architecture Hexagonale
- Ne jamais ajouter de couches : Facade, Manager, Handler, Processor, UseCase
- Ne jamais créer de dossiers vides pour des features non implémentées
- Ne jamais downgrader ou upgrader Spring Boot
- Ne jamais écrire de fausses données métier (fake customers, fake products...) dans le code de l'application, les migrations
  ou les tests de production. Seule exception : `tools/seed_demo.py`, outil d'essai demandé explicitement, hors de `src/`

### Ce qu'il faut TOUJOURS faire
- Suivre KISS + YAGNI + Clean Code
- Utiliser la structure feature-based (voir ARCHITECTURE.md)
- Étendre `BaseEntity` pour chaque entité JPA
- **Isoler par entreprise** : toute table métier porte `company_id NOT NULL`, toute requête
  de repository porte le `companyId`, qui vient de `CurrentUser` et jamais du client.
  Seule exception : `PlatformCompanyService`, qui lit une `Company` par id de chemin —
  légitime uniquement parce que l'appelant est un `PlatformAdmin`, qui n'appartient à
  aucune entreprise. Ne jamais élargir cette exception à un autre service
- **Un service n'injecte que le repository de sa propre feature.** Pour une autre feature,
  il passe par son service : `companyService.currentReference()`, `roleService.resolveAssignable()`,
  `branchService.getAssignable()`, `numberingService.allocate()`… La règle métier de chaque
  feature reste alors écrite une seule fois. Seule exception : `PlatformCompanyService` (ci-dessus)
- **`@Mapper(config = BaseMapperConfig.class)`** sur chaque mapper, et
  `@InheritConfiguration(name = "toEntity")` sur `updateEntity` — ne jamais recopier les
  `ignore` de `createdAt` / `updatedAt`
- **Unicité insensible à la casse côté base** : si le service vérifie `…IgnoreCase`, la migration
  crée `CREATE UNIQUE INDEX … (company_id, LOWER(col))`, pas une contrainte `UNIQUE` simple
- **Un montant de document ne se calcule qu'une fois, côté serveur** (`common/DocumentTotals`, utilisé par les documents de vente et d'achat).
  Le frontend affiche les totaux renvoyés par `POST /api/sales-documents/preview` et ne les recalcule
  jamais. Un document recopie ce qu'il imprime (désignation, prix, taux) — jamais de lien vivant vers
  le catalogue — et son numéro est alloué à l'**émission**, pas à la création du brouillon
- **Le stock est la somme de ses mouvements, jamais un compteur** : registre append-only
  (`StockMovement`, quantité signée), corrigé par un mouvement inverse. Un document qui touche le stock
  passe par `StockService` (`reserve` / `release`), dans la même transaction que son changement de statut.
  Un bon de retour client entre en stock, un bon de retour fournisseur en sort ; un document portant un bon de retour
  vivant ne s'annule pas (son effet sur le stock se ferait deux fois)
- **Un contrôle suivi d'une écriture verrouille d'abord** la ligne qui porte l'état contrôlé (stock du produit, document source) —
  sinon deux requêtes simultanées passent le même contrôle. Voir `docs/DEVELOPMENT.md`
- **Ce qui a été livré / reçu / retourné d'une ligne se déduit des lignes qui la suivent** (`sourceLine`, somme `quantitiesTaken`) :
  jamais un compteur stocké. Le plafond (« reste ») est contrôlé à l'émission / validation, côté serveur
- **Le règlement d'une facture se déduit de ses paiements et de ses avoirs** : registre `payments` (ajout et annulation, jamais modifié ni supprimé),
  `SalesDocument.applyPaid(somme des paiements actifs)` et `applyCredited(somme des avoirs émis)` réécrivent `paid_amount` / `credited_amount` et le statut.
  Un avoir ne touche pas au stock. Même règle côté achats : `supplier_payments` + `PurchaseDocument.applyPaid(somme)` et
  `applyCredited(somme des avoirs fournisseur validés)`. Ne jamais changer ce statut à la main
  ni l'incrémenter (« +x »). Une facture qui n'est pas issue d'un bon de livraison sort ses biens du stock **à l'émission**
- **Tout document s'imprime avec la même feuille** (`features/print`, à la Finco) : un nouveau type de
  document ajoute une entrée à `WORDING` et un mapper vers `PrintableDocument`, jamais une mise en page à lui.
  La feuille n'additionne rien — elle affiche ce que le serveur a calculé
- **`@PreAuthorize("hasAuthority('XXX')")` sur chaque endpoint**, avec une valeur de l'enum
  `Permission` — les rôles ne sont jamais des authorities (voir `docs/RBAC.md`)
- Utiliser `NotificationService` pour tous les feedbacks utilisateur
- Utiliser `<app-page-header>` pour toutes les pages ERP
- Masquer les actions non permises avec `*appHasPermission="'XXX'"` — confort d'affichage,
  jamais une protection : le backend revérifie tout
- Toute action destructive = confirmation `p-confirmDialog`. **Tout changement de statut d'un document aussi**,
  depuis la liste comme depuis sa page : les étapes sont décrites une fois (`*-document-actions.ts`)
- Toujours fournir un état vide (empty state) dans les tables

---

## Structure backend — règle d'or

Chaque feature suit exactement ce patron, rien de plus :

```
com.sales.smartBusiness.{feature}/
├── {Feature}.java                  (@Entity extends BaseEntity)
├── {Feature}Repository.java        (extends JpaRepository)
├── {Feature}Service.java           (logique métier)
├── {Feature}Controller.java        (@RestController, @RequestMapping("/api/{features}"))
├── {Feature}Mapper.java            (@Mapper MapStruct)
├── {Feature}Request.java           (DTO entrant avec validations)
└── {Feature}Response.java          (DTO sortant)
```

Flux : `Controller → Service → Repository → DB`

---

## Structure frontend — règle d'or

```
src/app/features/{feature}/
├── {feature}.component.ts          (standalone)
├── {feature}.component.html        (p-table + app-page-header)
├── {feature}.component.scss
└── {feature}-form/                 (si formulaire complexe)
    ├── {feature}-form.component.ts
    ├── {feature}-form.component.html
    └── {feature}-form.component.scss
```

Chaque feature communique avec le backend via un service dans `core/services/`.

---

## Règles pour les APIs REST

```
GET    /api/{resources}          → liste paginée
GET    /api/{resources}/{id}     → détail
POST   /api/{resources}          → création
PUT    /api/{resources}/{id}     → modification complète
DELETE /api/{resources}/{id}     → suppression
```

Réponses : toujours du JSON. Erreurs : `ErrorResponse` via `GlobalExceptionHandler`.

---

## ⚠️ Spring Boot 4 — pièges à connaître

Spring Boot 4 a **découpé les auto-configurations en modules séparés**.
Ajouter la librairie seule ne suffit plus : sans le module Boot correspondant,
l'auto-configuration ne s'active pas et **échoue silencieusement**.

| Besoin | ❌ Ne suffit pas | ✅ Utiliser |
|---|---|---|
| Migrations Flyway | `org.flywaydb:flyway-core` | `spring-boot-starter-flyway` |
| `@WebMvcTest` / MockMvc | `spring-boot-starter-test` | `spring-boot-starter-webmvc-test` |

Autres différences rencontrées :
- `@WebMvcTest` **et** `@AutoConfigureMockMvc` sont dans `org.springframework.boot.webmvc.test.autoconfigure`
  (plus dans `...test.autoconfigure.web.servlet`)
- **Jackson 3 est le défaut** (`tools.jackson`) : `com.fasterxml.jackson.databind.ObjectMapper`
  n'est **plus un bean injectable**, ni dans l'application ni dans les slices de test
  → en instancier un dans le test, ou écrire le JSON à la main pour un cas trivial
- `@MockBean` est remplacé par `@MockitoBean` (`org.springframework.test.context.bean.override.mockito`)

**Règle** : si une auto-configuration ne se déclenche pas, chercher le module
`spring-boot-starter-{techno}` avant de débugger le code.

---

## ⚠️ PostgreSQL — paramètre NULL dans une fonction SQL

PostgreSQL ne peut pas déduire le type d'un paramètre NULL passé à `LOWER()`
(il suppose `bytea` → erreur `function lower(bytea) does not exist`).

❌ À éviter :
```java
WHERE (:search IS NULL OR LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%')))
```

✅ Normaliser dans le service avec `common/SearchPattern`, ne jamais passer NULL :
```java
repository.search(currentUser.companyId(), SearchPattern.like(search), pageable);  // "%" si vide
```
```java
WHERE LOWER(CONCAT(u.firstName, ' ', u.lastName)) LIKE :search
```

Le même piège frappe un **paramètre date nul** — `(:from IS NULL OR a.occurredAt >= :from)`
échoue avec « n'a pas pu déterminer le type de données du paramètre ». Normaliser aussi :

```java
LocalDateTime start = from != null ? from : LocalDateTime.of(1970, 1, 1, 0, 0);
```
```java
WHERE a.occurredAt BETWEEN :from AND :to
```

Les paramètres enum et id peuvent rester en `(:status IS NULL OR u.status = :status)` :
Hibernate les lie avec un type connu.

---

---

## ⚠️ Un bean `Filter` est enregistré deux fois

Un filtre de sécurité déclaré `@Component` ou `@Bean` est ajouté à la chaîne Spring Security
**et** enregistré par Spring Boot sur le conteneur servlet — il s'exécute donc en double.
En prime, `@WebMvcTest` aspire tout bean de type `Filter` avec toute sa chaîne de dépendances,
ce qui casse tous les tests de contrôleur.

✅ L'instancier directement dans la chaîne :

```java
.addFilterBefore(new JwtAuthenticationFilter(jwtService, userRepository, platformAdminRepository),
                 UsernamePasswordAuthenticationFilter.class)
```

---

## ⚠️ LAZY hors transaction dans un filtre

`JwtAuthenticationFilter` s'exécute **avant** toute transaction et `open-in-view` est à `false`.
Toute association touchée dans le filtre doit être chargée par la requête elle-même :

```java
@Query("SELECT u FROM User u JOIN FETCH u.company LEFT JOIN FETCH u.roles WHERE u.id = :id")
Optional<User> findByIdWithRoles(@Param("id") Long id);
```

Sinon : `LazyInitializationException`, et l'utilisateur reçoit un 401 incompréhensible.

---

## Rappels importants

- `MessageService` et `ConfirmationService` sont fournis globalement dans `app.config.ts`
- `p-toast` est dans `AppComponent` et `p-confirmDialog` dans `MainLayoutComponent` —
  ne pas les dupliquer. Le toast est au-dessus du layout pour qu'un message émis pendant
  une redirection vers `/login` ne soit pas détruit avec le layout avant d'être lu
- Le token JWT est dans `localStorage` (`smartbusiness.token`) ; la session est restaurée
  au démarrage par `provideAppInitializer` qui appelle `/api/auth/me`
- Une route protégée déclare `canActivate: [permissionGuard]` et `data.permission` —
  c'est du confort d'affichage, le backend reste la seule barrière réelle
- `BaseEntity` fournit `createdAt` et `updatedAt` automatiquement via JPA Auditing
- Toute opération sensible appelle `auditService.record(...)` explicitement dans le service
  — il n'y a pas de table `{feature}_history`, un seul `audit_logs` par entreprise
- Spring Security est actif : API stateless, tout est authentifié sauf
  `/api/auth/register` et `/api/auth/login`
- Le JWT ne contient que `userId` + `companyId` — les permissions sont **relues en base à
  chaque requête**, pour que désactiver un utilisateur ou modifier un rôle s'applique aussitôt
- Secret JWT dans `smartbusiness.jwt.secret` (variable d'environnement `JWT_SECRET` en dehors du poste local) ;
  le profil `prod` (`application-prod.properties`) n'a AUCUNE valeur par défaut pour la base, le JWT, le CORS et les uploads
- `/api/auth/login` verrouille un compte 15 minutes après 5 mots de passe erronés de suite (`User.failedLoginAttempts` /
  `lockedUntil`, `smartbusiness.login.lockout-*`). L'écriture du compteur doit passer par `UserService.saveLoginOutcome`
  (`@Transactional(REQUIRES_NEW)`), jamais par une simple mutation dans `AuthService.login` : cette méthode lance une
  exception sur un mot de passe erroné, ce qui annule sa propre transaction — et le compteur avec, sans la transaction séparée
- CORS configuré pour `http://localhost:4200` sur `/api/**`
- Le state du sidebar est dans `layout/layout.service.ts` (signals + localStorage)
- L'URL de l'API est dans `src/environments/environment.ts`
- La base de données s'appelle `smartBusiness` (avec un B majuscule)
- `mvn test` utilise `smartbusiness_test`, recréée à chaque exécution par `IntegrationTest` —
  la base de développement n'est jamais touchée par les tests
- Toute nouvelle feature métier ajoute ses endpoints à `CompanyIsolationTest`
- Deux mondes de token, jamais mélangés : entreprise (`companyId`, autorités = permissions)
  et plateforme (`platform: true`, autorité unique `PLATFORM_ADMIN`). Côté Angular,
  `core/auth/` et `core/platform-auth/` ont chacun leur propre clé `localStorage` et leur
  propre intercepteur, qui ne s'active que sur les URLs `/platform/**`
- `PLATFORM_ADMIN_EMAIL` / `PLATFORM_ADMIN_PASSWORD` (**sans valeur par défaut** dans
  `application.properties` — jamais d'identifiant commité) créent le premier compte plateforme
  au démarrage (`PlatformAdminBootstrap`) — aucune inscription en libre-service pour ce rôle
- Un module métier est différent d'une permission : la permission dit qui peut faire quoi
  (`PermissionModule`, 11 valeurs, RBAC fin), un `BusinessModule` (`company/`, 4 valeurs :
  Customers, Sales, Purchases, Inventory) dit ce que l'entreprise a le droit d'utiliser,
  tout court. Un admin plateforme le gère via `/api/platform/companies`. Ne jamais exposer
  `PermissionModule` directement dans l'API plateforme — `PURCHASES` et `SUPPLIERS`
  (ou `PRODUCTS` et `STOCK`) doivent toujours basculer ensemble via leur `BusinessModule`
- `SessionResponse.enabledModules` porte les `BusinessModule` de l'entreprise de
  l'utilisateur (pas juste côté plateforme) — `navigation.ts` (`NavItem.module`) et
  `AuthService.hasModule()` filtrent la sidebar avec, exactement comme `permission` déjà.
  **Le serveur applique aussi le module** : `User.collectPermissions()` retire les permissions d'un module coupé
  (`Company.allows`), donc tout `@PreAuthorize` d'un module désactivé répond 403 — un nouvel endpoint métier n'a rien
  de plus à faire que sa `hasAuthority(...)` habituelle

---

## Exceptions disponibles

| Exception | Code HTTP | Quand l'utiliser |
|---|---|---|
| `ResourceNotFoundException` | 404 | Ressource introuvable, **ou appartenant à une autre entreprise** |
| `DuplicateResourceException` | 409 | Champ unique déjà pris (username, email, code…) |
| *(levée par la base)* `DataIntegrityViolationException` | 409 | Index unique (`23505`) ou clé étrangère (`23503`) violé — deux requêtes concurrentes ; tout autre code reste un 500 |
| *(levée par `@Version`)* `OptimisticLockingFailureException` | 409 | Quelqu'un a modifié la même ligne entre-temps — « Reload it and try again » |
| `BusinessRuleException` | 422 | Opération bloquée par une règle métier |
| `InvalidCredentialsException` | 401 | Connexion refusée (identifiants, compte inactif) |

`AccessDeniedException` (levée par `@PreAuthorize`) est traduite en **403** par
`GlobalExceptionHandler`, et `HttpMessageNotReadableException` (corps JSON illisible, valeur
d'enum inconnue) en **400** — toujours au format `ErrorResponse`.

Le message de ces exceptions est **affiché tel quel à l'utilisateur** par le frontend
(via `httpErrorInterceptor`) → écrire des messages clairs, sans jargon technique.

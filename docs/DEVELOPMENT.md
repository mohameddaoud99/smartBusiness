# DEVELOPMENT.md — Règles de développement

## Principes directeurs

- **KISS** — la solution la plus simple qui fonctionne
- **YAGNI** — n'implémenter que ce qui est demandé maintenant
- **Clean Code** — noms clairs, méthodes courtes, pas de commentaires inutiles
- **SOLID** — uniquement quand c'est utile, pas par dogme

---

## Règles Backend

### Structure d'une feature

Chaque feature est un dossier plat avec exactement ces fichiers :

```
customer/
├── Customer.java               Entité JPA
├── CustomerRepository.java     Accès données
├── CustomerService.java        Logique métier
├── CustomerController.java     Endpoints REST
├── CustomerMapper.java         Entity ↔ DTO (MapStruct)
├── CustomerRequest.java        DTO entrant (validation)
└── CustomerResponse.java       DTO sortant
```

Pas de sous-dossiers. Pas de fichiers supplémentaires sauf si une raison concrète l'exige.

### Entité JPA

```java
@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
public class Customer extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;
}
```

- Toujours étendre `BaseEntity` (createdAt + updatedAt automatiques, `version` pour le verrou
  optimiste — pas de setter, seul Hibernate le fait avancer)
- Une unicité vérifiée `…IgnoreCase` dans le service se déclare dans la migration par un
  **index `LOWER(...)`**, pas par `unique = true` / `@UniqueConstraint` sur l'entité :
  `CREATE UNIQUE INDEX uk_customers_company_reference_ci ON customers (company_id, LOWER(reference));`
  La vérification du service donne le message clair ; l'index protège des requêtes concurrentes
  (`GlobalExceptionHandler` le traduit en 409)
- Toujours nommer la table explicitement avec `@Table(name = "...")`
- Utiliser `@GeneratedValue(strategy = GenerationType.IDENTITY)`
- Pas de `@Data` Lombok sur les entités JPA (problèmes hashCode/equals)

### Value object (`@Embeddable`)

Un groupe de champs qui se répète dans une entité — l'exemple actuel est `common/Address`
(facturation + livraison sur `Customer` **et** `Supplier`) — est un `@Embeddable`, pas une
table à part. Ce n'est pas une couche interdite : c'est de la donnée, pas de la logique.
Partagé entre plusieurs features, il vit dans `common/`, pas dans la feature qui l'a
introduit en premier.

```java
@Embeddable @Getter @Setter @NoArgsConstructor
public class Address { private String street; private String city; /* ... */ }
```

L'entité nomme les colonnes de chaque copie avec `@AttributeOverrides` (sinon collision) :

```java
@Embedded
@AttributeOverrides({ @AttributeOverride(name = "street", column = @Column(name = "billing_street")) /* ... */ })
private Address billingAddress;
```

Le DTO expose un objet imbriqué (`AddressDto`) que MapStruct mappe tout seul dès qu'une
méthode `Address ↔ AddressDto` existe dans le mapper. Le service remet à `null` un value
object entièrement vide avant de sauvegarder.

### Repository — toujours porter le `companyId`

```java
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByIdAndCompanyId(Long id, Long companyId);

    Page<Customer> findByCompanyId(Long companyId, Pageable pageable);
}
```

Règle d'isolation : **aucun `findById`, `findAll` ou `existsById` nu dans un service métier.**
Chaque requête porte l'entreprise, visible dans sa signature. Une ressource d'une autre
entreprise doit remonter en `ResourceNotFoundException` (404), jamais en 403 — un 403
confirmerait son existence.

### Service

```java
@Service
@RequiredArgsConstructor
@Transactional
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;

    @Transactional(readOnly = true)
    public Page<CustomerResponse> findAll(Pageable pageable) {
        return customerRepository.findAll(pageable)
                .map(customerMapper::toResponse);
    }

    public CustomerResponse create(CustomerRequest request) {
        Customer customer = customerMapper.toEntity(request);
        return customerMapper.toResponse(customerRepository.save(customer));
    }

    public CustomerResponse update(Long id, CustomerRequest request) {
        Customer customer = customerRepository.findByIdAndCompanyId(id, currentUser.companyId())
                .orElseThrow(() -> ResourceNotFoundException.of("Customer", id));
        customerMapper.updateEntity(request, customer);
        return customerMapper.toResponse(customer);
    }
}
```

- `@Transactional` sur la classe pour les écritures
- `@Transactional(readOnly = true)` pour les lectures
- Lever `ResourceNotFoundException` quand une ressource est introuvable
- Ne pas mettre de logique dans le Controller
- Injecter `CurrentUser` dès qu'une donnée métier est lue ou écrite
- **N'injecter que le repository de sa propre feature.** Rattacher une ligne à l'entreprise :
  `companyService.currentReference()` (jamais `CompanyRepository`). Valider un rôle ou une
  branche choisis dans un formulaire : `roleService.resolveAssignable(ids)`,
  `branchService.getAssignable(id)`. Obtenir un numéro ou un code : `numberingService.allocate(type)`
- **Bloquer la suppression d'une référence encore utilisée sans dépendre du repository
  du module appelant.** `Category`/`Brand` bloquent leur suppression tant qu'un `Product`
  pointe dessus, via une requête JPQL sur l'entité `Product` directement dans
  `CategoryRepository`/`BrandRepository` (`SELECT COUNT(p) FROM Product p WHERE p.category.id = :id`)
  — aucun import de `Product`, aucune dépendance vers `product/`. Même patron que
  `RoleRepository.countHolders` vers `User`. C'est la manière correcte de résoudre le cas
  où le module « bas niveau » (catégorie) a besoin de savoir si le module « haut niveau »
  (produit) l'utilise, sans inverser la direction des dépendances
- Recherche texte : `SearchPattern.like(search)` (jamais `null` vers PostgreSQL)
- Un `GET` n'écrit jamais : une valeur par défaut manquante s'affiche sans être persistée
- Le `companyId` vient de `CurrentUser`, **jamais** d'un paramètre ou d'un DTO d'entrée

### Controller

```java
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    @PreAuthorize("hasAuthority('CUSTOMER_VIEW')")
    public Page<CustomerResponse> findAll(Pageable pageable) {
        return customerService.findAll(pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CUSTOMER_CREATE')")
    public CustomerResponse create(@Valid @RequestBody CustomerRequest request) {
        return customerService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    public CustomerResponse update(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        return customerService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('CUSTOMER_DELETE')")
    public void delete(@PathVariable Long id) {
        customerService.delete(id);
    }
}
```

- Pas de logique dans le Controller — uniquement délégation au Service
- `@Valid` obligatoire sur les `@RequestBody`
- Statuts HTTP corrects : 201 Created, 204 No Content
- **`@PreAuthorize` sur chaque endpoint**, avec une permission de l'enum `Permission`.
  Seules les permissions sont des authorities — jamais les noms de rôles.
  Un nouveau module déclare ses permissions dans l'enum, puis les pose ici.

### DTO Request (validation)

```java
@Getter
@Setter
public class CustomerRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    @Email(message = "Email must be valid")
    private String email;
}
```

### DTO Response

```java
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CustomerResponse {
    private Long id;
    private String name;
    private String email;
    private LocalDateTime createdAt;
}
```

### Mapper MapStruct

```java
@Mapper(config = BaseMapperConfig.class)
public interface CustomerMapper {

    CustomerResponse toResponse(Customer customer);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "company", ignore = true)      // assigné par le service
    Customer toEntity(CustomerRequest request);

    @InheritConfiguration(name = "toEntity")          // reprend les ignore ci-dessus
    void updateEntity(CustomerRequest request, @MappingTarget Customer customer);
}
```

- `BaseMapperConfig` (`common/`) ignore `createdAt` / `updatedAt` pour toute méthode qui écrit
  dans une `BaseEntity` — ne jamais les répéter
- `name = "toEntity"` est obligatoire : sans lui, MapStruct hésite avec le prototype de la config

### Document commercial : instantanés et calcul unique

Un document (devis, commande…) **recopie** ce qu'il imprime — désignation, prix, taux de TVA,
nom des taxes — au lieu de pointer vers le catalogue : renommer un produit ne réécrit jamais un
document déjà émis. La ligne garde `product_id` (blocage de suppression) et `vat_tax_id`
(présélection à l'édition), pas les valeurs vivantes.

- **Les montants ne se saisissent jamais** : `common/DocumentTotals` les dérive des lignes et des taxes
  choisies (`SalesDocument` et `PurchaseDocument` lui passent les leurs). C'est la seule implémentation —
  une nouvelle famille de documents (facture, avoir…) la réutilise, elle ne la recopie pas. Le frontend appelle `POST …/preview` (mêmes
  règles, rien de sauvé) pour afficher les totaux en direct — jamais de calcul dupliqué en TypeScript.
- **Le numéro se donne à l'émission**, pas à la création du brouillon (`numberingService.allocate`).
- **Un document émis est figé** : l'annuler ou le rejeter, jamais l'éditer ni le supprimer.
- **Transitions dans l'entité** (`canMoveTo`), pas éparpillées dans le service.
- Un contrôle qui dépend d'une autre feature passe par le service de celle-ci
  (`customerService.getAssignable`, `productService.resolveSellable`), et la suppression d'une
  ressource référencée par un document se bloque par un `COUNT` JPQL dans **son propre**
  repository — même patron que `RoleRepository.countHolders`.

### Étapes de statut : définies une fois, confirmées toujours

Ce qu'on peut faire d'un document selon son statut (émettre, accepter, confirmer, annuler…) est décrit **une seule fois**
par famille (`sales-document-actions.ts`, `purchase-document-actions.ts`) : libellé, droit requis, texte de confirmation, appel
serveur et message de résultat. Le menu de ligne d'une liste et l'en-tête de la page du document lisent les mêmes
descripteurs — les deux proposent donc les mêmes étapes, avec les mêmes mots.

- **Chaque changement de statut demande confirmation** (`confirmDocumentAction`, `shared/document-action.ts`), sans exception :
  le texte dit ce qui va se passer, y compris l'effet sur le stock (réserver, entrer, rendre). Ce qui ne se rattrape pas
  (annuler) est en rouge (`danger`) et rangé en dernier.
- Un item de menu ne peut pas porter `*appHasPermission` : la liste filtre les étapes avec `AuthService.has()` — confort
  d'affichage, le backend revérifie.
- Le descripteur **reflète** `canMoveTo` côté serveur, qui reste seul juge : proposer une étape que le serveur refuse donne un 422
  lisible, jamais un changement silencieux. Une règle qui dépend d'une donnée que la ligne de liste n'a pas (une commande déjà
  créée depuis un devis) est appliquée par la page, qui a le document complet, et par le serveur.
- Ajouter une étape = une entrée dans le descripteur + une branche dans `runXxxAction` ; rien à toucher dans la liste ni dans la page.

### Impression : une feuille pour tous les documents

Un document s'imprime avec `features/print` — **jamais** avec une mise en page propre à son type. La feuille
(`PrintableDocumentComponent`) ne connaît que `PrintableDocument`, une forme commune ; chaque famille de documents
(ventes, achats, et les factures) la remplit avec un mapper `fromXxxDocument(document, tiers)`.

- **Ajouter un type de document** = une entrée dans `WORDING` (titre, phrase « Arrêté… », libellé de l'échéance, tiers)
  et, pour une nouvelle famille, un mapper + une route `/print/{famille}/:id`. La feuille ne change pas.
- La feuille **n'additionne ni ne recalcule** rien : elle affiche les montants renvoyés par le serveur (règle de
  `DocumentTotals`). Seul le montant en lettres est écrit ici, dans `amount-in-words.ts`, testé cas par cas.
- Les documents imprimés sont **en français**, au format Finco (virgule décimale, 3 décimales, « DT ») — l'interface
  reste en anglais, la feuille est un document fiscal tunisien.
- **Deux sorties, une seule mise en page.** « Print » ouvre la boîte d'impression du navigateur ; « Download PDF »
  (`PdfExportService`, html2pdf.js chargé à la demande) photographie la même feuille page par page et la télécharge.
  Le PDF est donc une image de la feuille (texte non sélectionnable) : c'est le prix de ne décrire la mise en page qu'une fois.
  Pour le fichier, la feuille est clonée avec la classe `.pdf-mode` (sans largeur ni marge propres — c'est le PDF qui
  ajoute les siennes, 12 mm comme `@page` dans `styles.scss`, que le navigateur lit sur la page entière).
- Garder la feuille **compacte** : un document court doit tenir sur une page A4 (utile page = 273 mm ≈ 1032 px). Le bloc
  final (notes, RIB, cachet) ne se coupe jamais sur deux pages — s'il ne tient pas, il passe entier à la suivante.
- Un brouillon et un document annulé portent un **filigrane** ; un aperçu ne doit jamais passer pour l'original.

### Registre append-only (stock)

Une quantité qui varie ne se stocke pas dans un compteur : elle se **calcule** en additionnant des
lignes qu'on n'édite jamais. Chaque mouvement porte une quantité **signée** (positive ajoute, négative
retire), donc un niveau est un simple `SUM`, une annulation écrit le mouvement inverse et tout niveau
s'explique ligne par ligne.

- Deux dimensions dans le même registre : le **physique** (entrée, sortie, ajustement, transfert) et le
  **réservé** (`RESERVE` / `RELEASE`). Disponible = physique − réservé.
- Un document qui agit sur le stock passe par `StockService` (`reserve`, `release`) avec un
  `StockSource` + un id : le stock ne connaît pas les entités des autres features.
- Une livraison (`deliver`) **consomme** la réservation de la commande : ses lignes `RELEASE` portent la
  commande comme source et le bon comme `origin_id`, ce qui permet à `undoDelivery` de tout défaire
  (retour du physique + nouvelle réservation) sans deviner. Un mouvement causé par un autre document que
  sa source porte `origin_id`.
- Une opération qui peut être refusée (« Not enough stock ») s'exécute **dans la même transaction** que le
  changement de statut du document : refusée, rien n'est modifié.
- Un ajustement d'inventaire reçoit la quantité **comptée** ; le service écrit la différence.

### Un état dérivé se réécrit depuis sa source, jamais « +x » (règlement d'une facture)

Le statut de règlement d'une facture (`ISSUED` impayée, `PARTIALLY_PAID`, `PAID`) n'est jamais changé à la main ni
incrémenté. Le registre `payments` (ajout et annulation, ni PUT ni DELETE) est la source ; à chaque écriture,
`PaymentService` relit la **somme des paiements actifs** et la passe à `SalesDocumentService.applyPaid`, qui fixe
`paid_amount` **et** le statut d'un coup. Le service `payment` ne touche jamais le repository des ventes : il passe par
`getPayableInvoice` / `applyPaid`. Le solde (`balance`) est calculé côté serveur, le frontend l'affiche.

Les avoirs suivent la même règle : `credited_amount` d'une facture est réécrit depuis la **somme des avoirs émis**
(`applyCredited`), à l'émission comme à l'annulation d'un avoir ; l'état de règlement (`refreshSettlement`) se déduit
de payé + crédité. Un plafond qui dépend d'une somme (avoirs ≤ total de la facture) se vérifie APRÈS la réécriture,
sur la somme relue : refusé, la transaction annule tout (le numéro est rendu).

### Ce qui a été pris d'une ligne se lit, ne se stocke pas (quantités suivies)

Une ligne recopiée d'un document source garde un lien `sourceLine`. « Livré », « reçu », « retourné » et « reste » se
calculent par une somme (`quantitiesTaken`) sur les documents qui comptent (type + statuts dans `Tracking`) — aucune
colonne de compteur, donc une annulation rend la quantité toute seule. Le plafond se vérifie à l'émission / validation
(`checkAgainstSource`), pas à l'enregistrement du brouillon. Un nouveau type de document suivi = une entrée dans
`trackingOf` du service de sa famille, rien d'autre.

### « Vérifier puis écrire » se verrouille

Toute règle qui lit un état puis écrit en fonction (assez de stock ? reste à livrer ?) verrouille d'abord la ligne
qui porte cet état (`@Lock(PESSIMISTIC_WRITE)` dans le repository de sa feature : `lockByIdAndCompanyId` du produit,
`lockById` du document source), dans la même transaction. Sans cela, deux requêtes simultanées passent le même contrôle.
Un test de concurrence (`ConcurrencyIntegrationTest`) lance les requêtes ensemble et compte les statuts.

### Migrations Flyway

Chaque modification de schéma = un nouveau fichier de migration :

```
V1__init_schema.sql             (placeholder initial)
V2__create_customers_table.sql
V3__create_products_table.sql
```

**Ne jamais réécrire une migration déjà appliquée**, même en cours de dev, même « pas
encore livrée ». Dès qu'une base l'a jouée, son checksum est figé dans
`flyway_schema_history` ; la modifier casse le démarrage
(`Migration checksum mismatch`). Besoin de changer une table qu'on vient d'ajouter →
un nouveau `Vn+1` qui l'`ALTER`. (Rattrapage d'urgence : `UPDATE flyway_schema_history
SET checksum = NULL WHERE version = '<n>'` désactive la validation de cette ligne.)

Convention de nommage du SQL :
```sql
CREATE TABLE customers (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(150),
    created_at  TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP NOT NULL
);
```

---

## Règles Frontend

### Structure d'une feature

```
features/customers/
├── customers.component.ts          Liste avec p-table
├── customers.component.html
├── customers.component.scss
└── customers-form/                 Formulaire création/édition
    ├── customers-form.component.ts
    ├── customers-form.component.html
    └── customers-form.component.scss
```

### Service Angular (dans `core/services/`)

```typescript
@Injectable({ providedIn: 'root' })
export class CustomerService {

  private readonly apiUrl = 'http://localhost:8080/api/customers';

  constructor(private http: HttpClient) {}

  findAll(params?: HttpParams): Observable<Page<CustomerResponse>> {
    return this.http.get<Page<CustomerResponse>>(this.apiUrl, { params });
  }

  findById(id: number): Observable<CustomerResponse> {
    return this.http.get<CustomerResponse>(`${this.apiUrl}/${id}`);
  }

  create(request: CustomerRequest): Observable<CustomerResponse> {
    return this.http.post<CustomerResponse>(this.apiUrl, request);
  }

  update(id: number, request: CustomerRequest): Observable<CustomerResponse> {
    return this.http.put<CustomerResponse>(`${this.apiUrl}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
```

### Un composant, plusieurs types de document

Devis et commandes partagent leurs composants : la route porte `data.documentType`
(`'QUOTE'` / `'SALES_ORDER'`), le composant le lit dans `ActivatedRoute.snapshot.data` et
`DOCUMENT_CONFIGS[type]` fournit libellé, préfixe de route et statuts. Une route paramétrée
par une fonction (`salesDocumentRoutes(...)` dans `app.routes.ts`) évite de dupliquer les
trois routes `''` / `new` / `:id`. `new` se déclare **avant** `:id`.

### Composant liste (patron standard)

```typescript
@Component({
  selector: 'app-customers',
  standalone: true,
  imports: [TableModule, ButtonModule, TagModule, PageHeaderComponent, ...],
  templateUrl: './customers.component.html'
})
export class CustomersComponent implements OnInit {

  customers: CustomerResponse[] = [];
  loading = false;
  totalRecords = 0;

  constructor(
    private customerService: CustomerService,
    private notification: NotificationService,
    private confirmationService: ConfirmationService
  ) {}

  ngOnInit() {
    this.load();
  }

  load(event?: TableLazyLoadEvent) {
    this.loading = true;
    this.customerService.findAll(/* params */).subscribe({
      next: (page) => {
        this.customers = page.content;
        this.totalRecords = page.totalElements;
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }

  delete(customer: CustomerResponse) {
    this.confirmationService.confirm({
      message: `Are you sure you want to delete ${customer.name}?`,
      accept: () => {
        this.customerService.delete(customer.id).subscribe({
          next: () => {
            this.notification.success('Customer deleted successfully.');
            this.load();
          }
        });
      }
    });
  }
}
```

### Conventions de nommage TypeScript

```typescript
// Interfaces / Types
interface CustomerResponse { ... }
interface CustomerRequest  { ... }

// Composants — même nom que le fichier
export class CustomersComponent { }
export class CustomersFormComponent { }

// Services
export class CustomerService { }
export class NotificationService { }

// Intercepteurs — fonctions
export const httpErrorInterceptor: HttpInterceptorFn = ...
```

### Template HTML — patron page liste

```html
<app-page-header title="Customers" subtitle="Manage your customers">
  <p-button label="New Customer" icon="pi pi-plus" routerLink="new" />
</app-page-header>

<p-table
  [value]="customers"
  [loading]="loading"
  [paginator]="true"
  [rows]="10"
  [totalRecords]="totalRecords"
  [lazy]="true"
  (onLazyLoad)="load($event)"
  dataKey="id"
  styleClass="p-datatable-sm">

  <ng-template pTemplate="header">
    <tr>
      <th pSortableColumn="name">Name <p-sortIcon field="name" /></th>
      <th>Email</th>
      <th>Status</th>
      <th>Actions</th>
    </tr>
  </ng-template>

  <ng-template pTemplate="body" let-customer>
    <tr>
      <td>{{ customer.name }}</td>
      <td>{{ customer.email }}</td>
      <td><p-tag [value]="customer.status" severity="success" /></td>
      <td>
        <p-button icon="pi pi-pencil" [text]="true" size="small" />
        <p-button icon="pi pi-trash" [text]="true" severity="danger" size="small"
          (onClick)="delete(customer)" />
      </td>
    </tr>
  </ng-template>

  <ng-template pTemplate="emptymessage">
    <tr>
      <td colspan="4" class="text-center p-4">
        No customers found. Create your first customer to get started.
      </td>
    </tr>
  </ng-template>

</p-table>
```

---

## Tests

Deux niveaux, avec des rôles distincts :

| Type | Quoi | Comment |
|---|---|---|
| Test de service | règles métier, cas limites | Mockito, aucun contexte Spring |
| Test de contrôleur | contrat HTTP, codes de statut | `@WebMvcTest` + `@AutoConfigureMockMvc(addFilters = false)` |
| Test d'intégration | isolation, autorisation, escalade | `extends IntegrationTest` — vraie chaîne HTTP + vraie base |

### Base de test

Les tests **ne touchent jamais la base de développement**. `src/test/resources/application.properties`
les dirige vers `smartbusiness_test`, que `IntegrationTest` supprime et recrée au démarrage de
chaque exécution. Flyway rebâtit ensuite le schéma : chaque run part d'une base vide, sans
aucune étape manuelle sur un poste neuf. Seul PostgreSQL doit tourner sur `localhost:5432`.

### Ce qui mérite un test d'intégration

Tout ce qu'un mock ne peut pas prouver :

```java
class CompanyIsolationTest extends IntegrationTest {
    // deux entreprises, chaque endpoint tenté en croisé → 404
}
```

Un `findById` qui oublie son `companyId` passe tous les tests unitaires du monde. Dès qu'une
feature métier arrive (clients, produits, ventes), **ajouter ses endpoints à
`CompanyIsolationTest`** — c'est le filet qui empêche une fuite entre entreprises.

---

## Règles générales

### Commentaires
- Pas de commentaires qui expliquent le QUOI (le code le fait déjà)
- Uniquement si le POURQUOI n'est pas évident (contrainte cachée, workaround)

### Gestion d'erreur
- Backend : toujours via `GlobalExceptionHandler`
- Frontend : toujours via `httpErrorInterceptor` + `NotificationService`
- Ne pas avaler les erreurs silencieusement

### Ne jamais faire
- `@Data` Lombok sur les entités JPA
- Logique métier dans un Controller
- Appels HTTP dans un composant Angular (toujours via un service)
- `console.log` laissés en production
- `any` TypeScript sauf cas exceptionnel justifié

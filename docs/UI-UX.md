# UI-UX.md — Design ERP + règles PrimeNG

## Philosophie

L'interface est un **outil de travail**, pas un site vitrine.
- Densité d'information maximale
- Navigation claire et prévisible
- Feedback immédiat sur chaque action
- Zéro surprise pour l'utilisateur

---

## Identité visuelle

Le thème est défini dans `src/app/theme.ts` avec `definePreset(Aura, {...})`.

| Élément | Valeur | Pourquoi |
|---|---|---|
| Couleur d'accent | Indigo `#4F46E5` (`primary.600`) | Sérieux, laisse les couleurs de statut ressortir |
| Surfaces | Palette slate (gris-bleu neutre) | Les seules couleurs saturées à l'écran sont les statuts |
| Border radius | `md: 6px` (au lieu de 10px) | Angles plus nets = plus "outil métier" |
| Taille de police de base | 14px, tables à 13px | Densité d'information |
| Hauteur de ligne de table | ~36px (`p-datatable-sm` + overrides) | Plus de lignes visibles |
| Ripple | désactivé | L'app doit paraître réactive, pas animée |

⚠️ `borderRadius` est un token **primitive**, pas semantic. `formField` et `content`
sont bien dans `semantic`.

---

## Layout global

```
┌──────────┬──────────────────────────────────────────┐
│ SIDEBAR  │ TOPBAR 56px fixe                         │
│ 256px    ├──────────────────────────────────────────┤
│ (ou 64px)│                                          │
│          │   breadcrumb → titre → toolbar → contenu │
│ Brand    │   (seule zone scrollable)                │
│ Nav      │                                          │
│ Version  │                                          │
└──────────┴──────────────────────────────────────────┘
```

- Sidebar et topbar **fixes** — jamais scrollés
- Le contenu central est la seule zone scrollable — padding `1.25rem 1.5rem 2rem`

### Sidebar — 3 modes

| Mode | Largeur | Quand | Comportement |
|---|---|---|---|
| Étendu | 256px | ≥ 992px, par défaut | Icônes + libellés + groupes dépliables |
| Rail | 64px | ≥ 992px, replié | Icônes seules + **tooltips obligatoires** |
| Overlay | 256px | < 992px | Glisse au-dessus du contenu + backdrop |

État géré par `layout/layout.service.ts` (signals), persisté dans `localStorage`.

**Règles sidebar :**
- L'élément actif a une **barre d'accent à gauche** + fond teinté — repérable instantanément
- Le groupe contenant la route active s'ouvre automatiquement après chaque navigation
- Cliquer un groupe en mode rail **déplie d'abord le sidebar** (pas de flyout : illisible)
- Les modules non implémentés restent visibles, désactivés, avec un badge "Soon"

> **Note** : la navigation n'utilise **pas** `p-panelMenu`. Ce composant ne permet ni le rail
> 64px avec tooltips, ni la barre d'accent active. Le remplacement est ~90 lignes de HTML/SCSS
> simples — plus lisible que de contourner le composant.

### Topbar

```
☰  Users / Détail        [🔍 Search…  Ctrl K]        🔔  ?  👤 Mohamed A.
```

- **Breadcrumbs** générés depuis `data: { breadcrumb: '...' }` des routes
- Recherche globale ouverte au clic ou par **`Ctrl+K`** (convention attendue)
- `< 1200px` : la recherche se réduit · `< 992px` : icône seule · `< 640px` : breadcrumbs masqués

---

## Composants PrimeNG — liste officielle du projet

Seuls ces composants sont utilisés. Ne pas en introduire d'autres sans justification.

### Navigation et layout
| Composant | Usage |
|---|---|
| *(HTML custom)* | Navigation sidebar — voir la note plus haut |
| `Popover` | Panneau de notifications dans la topbar |
| `Drawer` | Panneau de détail latéral (fiche utilisateur…) |
| `Tabs` | Onglets dans un drawer ou un formulaire long |

### Tables et données
| Composant | Usage |
|---|---|
| `Table (p-table)` | Toutes les listes de données ERP |
| `Paginator` | Intégré dans p-table |

### Actions
| Composant | Usage |
|---|---|
| `Button` | Toutes les actions |
| `SplitButton` | Actions multiples sur un élément |
| `Menu` | Menu contextuel dans les lignes de table |

### Formulaires
| Composant | Usage |
|---|---|
| `IconField` + `InputIcon` | Champ de recherche avec icône |
| `InputText` | Champ texte standard |
| `Textarea` | Texte multiligne |
| `Select (Dropdown)` | Listes déroulantes |
| `InputNumber` | Montants, quantités |
| `DatePicker` | Saisie de dates |
| `Checkbox` | Cases à cocher |
| `RadioButton` | Choix exclusifs |
| `MultiSelect` | Sélection multiple — rôles d'un utilisateur, `display="chip"` |
| `Password` | Mots de passe — `[toggleMask]="true"`, `[feedback]="false"` hors création de compte |

### Feedback et états
| Composant | Usage |
|---|---|
| `Toast` | Notifications (succès, erreur, info, warning) |
| `ConfirmDialog` | Confirmation des actions destructives |
| `ProgressSpinner` | Chargement d'une action |
| `Skeleton` | Chargement d'un contenu |
| `Message / InlineMessage` | Messages contextuels dans les formulaires |

### Indicateurs
| Composant | Usage |
|---|---|
| `Tag` | Statuts (actif, en attente, annulé...) |
| `Badge` | Compteurs sur icônes |
| `ProgressBar` | Progression d'un workflow |
| `app-bar-chart` (`shared/components`) | Le graphique du tableau de bord : barres en HTML/CSS, pas de librairie de graphique |

### Conteneurs
| Composant | Usage |
|---|---|
| `Card` | Panneaux de contenu, KPI cards |
| `Dialog` | Formulaires en popup |
| `Drawer` | Détail/formulaire en panneau latéral |
| `Tabs` | Formulaires complexes en plusieurs onglets |

### Divers
| Composant | Usage |
|---|---|
| `Tooltip` | Aide contextuelle sur les icônes |
| `Avatar` | Avatar utilisateur dans le header |
| `Divider` | Séparateurs visuels |
| `Timeline` | Historique d'un document (workflow) |

---

## Système de couleurs / statuts

Les statuts ont toujours le même sens visuel dans toute l'application.

| Sévérité PrimeNG | Signification métier | Exemples |
|---|---|---|
| `success` | Positif / Terminé / Actif | Commande livrée, client actif, stock OK |
| `warning` | Attention / En attente | Commande en cours, stock bas |
| `danger` | Erreur / Annulé / Problème | Commande annulée, impayé, rupture |
| `info` | Information neutre | Brouillon, commentaire |
| `secondary` | Inactif / Neutre | Archivé, inactif |

Utilisation dans le code :
```html
<p-tag value="Active"    severity="success"   />
<p-tag value="Pending"   severity="warning"   />
<p-tag value="Cancelled" severity="danger"    />
<p-tag value="Draft"     severity="info"      />
<p-tag value="Archived"  severity="secondary" />
```

**Règle** : ne jamais utiliser des couleurs custom pour les statuts. Toujours utiliser `severity`.

---

## Pages publiques (connexion, inscription)

Elles vivent **hors de `MainLayoutComponent`** : ni sidebar, ni topbar, ni fil d'Ariane.

```
┌───────────────────────┬───────────────────────┐
│  PANNEAU DE MARQUE    │  FORMULAIRE           │
│  dégradé primary      │  carte 380px centrée  │
│  promesse produit     │  (460px à l'inscription)│
│  3 arguments courts   │                       │
└───────────────────────┴───────────────────────┘
        masqué < 900px            plein écran
```

Règle de feedback propre à ces écrans : **une erreur de connexion s'affiche dans le
formulaire** (`p-message severity="error"` au-dessus des champs), jamais en toast. L'utilisateur
regarde ses champs à ce moment-là, pas le coin de l'écran.

Le SCSS est écrit une fois dans `login.component.scss` ; l'inscription le réutilise via
`styleUrls: ['../login/login.component.scss', './register.component.scss']`.

---

## Masquer ce qui n'est pas permis

Une action qu'un utilisateur ne peut pas exécuter ne s'affiche pas :

```html
<p-button *appHasPermission="'USER_CREATE'" label="New user" icon="pi pi-plus" />
```

Le menu suit la même règle : un élément dont la permission manque disparaît, et un groupe
dont tous les enfants ont disparu disparaît aussi — jamais de cul-de-sac.

**Ce n'est pas de la sécurité.** Le backend revérifie chaque appel ; la directive ne fait
qu'éviter d'afficher des boutons qui répondraient 403.

### La matrice de permissions

`app-permission-matrix` est construite à partir de `GET /api/roles/permissions` : une ligne
par module, une case par action, une case « module » qui coche tout (état intermédiaire en
tiret quand la sélection est partielle). Un nouveau module métier apparaît tout seul dans
l'écran dès que ses permissions existent dans l'enum — le composant n'est pas à modifier.

Les libellés viennent du backend (« View users »), jamais les noms d'enum.

---

## Patron de page standard

Chaque page de liste ERP suit exactement ce patron :

```html
<!-- 1. En-tête de page -->
<app-page-header title="Customers" subtitle="Manage your customers">
  <p-button label="New Customer" icon="pi pi-plus" />
</app-page-header>

<!-- 2. Filtres (optionnel) -->
<!-- p-inputText pour la recherche globale -->

<!-- 3. Table de données -->
<p-table styleClass="p-datatable-sm" [paginator]="true" ...>
  ...
  <ng-template pTemplate="emptymessage">
    <!-- Empty state obligatoire -->
  </ng-template>
</p-table>
```

### Empty state obligatoire

Toutes les tables doivent avoir un empty state :
```html
<ng-template pTemplate="emptymessage">
  <tr>
    <td [attr.colspan]="colonnes" class="empty-state">
      <i class="pi pi-inbox"></i>
      <p>No customers found.</p>
      <p-button label="New Customer" icon="pi pi-plus" size="small" />
    </td>
  </tr>
</ng-template>
```

---

## Tables — configuration standard

```html
<p-table
  [value]="items"
  [loading]="loading"
  [paginator]="true"
  [rows]="10"
  [rowsPerPageOptions]="[10, 25, 50]"
  [totalRecords]="totalRecords"
  [lazy]="true"
  (onLazyLoad)="load($event)"
  [sortMode]="'single'"
  dataKey="id"
  styleClass="p-datatable-sm p-datatable-striped"
  responsiveLayout="scroll">
```

- `p-datatable-sm` — taille compacte adaptée à l'ERP
- `p-datatable-striped` — alternance de couleurs pour la lisibilité
- `[lazy]="true"` — pagination et tri côté serveur
- `dataKey="id"` — nécessaire pour la sélection de lignes

---

## Formulaires

### Structure d'un formulaire
```html
<form [formGroup]="form" (ngSubmit)="submit()">

  <!-- Section avec label + champ + message d'erreur -->
  <div class="field">
    <label for="name">Name <span class="required">*</span></label>
    <input pInputText id="name" formControlName="name" class="w-full"
      [class.ng-invalid]="isInvalid('name')" />
    @if (isInvalid('name')) {
      <small class="p-error">Name is required.</small>
    }
  </div>

  <!-- Actions formulaire -->
  <div class="form-actions">
    <p-button label="Cancel" severity="secondary" [outlined]="true" (onClick)="cancel()" />
    <p-button label="Save" type="submit" [loading]="saving" [disabled]="form.invalid" />
  </div>

</form>
```

### Validation visuelle
- Champ invalide touché → bordure rouge via `[class.ng-invalid]="isInvalid('name')"`
- Message d'erreur en rouge sous le champ avec `<small class="p-error">`
- Indicateur obligatoire : `<span class="required">*</span>` (couleur danger)
- Bouton Submit : `[loading]="saving"` pendant l'envoi, `[disabled]="form.invalid"`

---

## UX Workflow (statuts)

Pour les documents avec workflow (commandes, factures...) :

```
Brouillon  →  Confirmé  →  En cours  →  Terminé
   │                                       │
   └──────────── Annulé ───────────────────┘
```

Afficher le workflow avec `p-timeline` ou `p-steps` sur les pages détail.
L'utilisateur doit toujours voir :
1. Le statut actuel (Tag bien visible)
2. Les actions disponibles depuis ce statut
3. Ce qui arrive ensuite

---

## Feedback utilisateur — règles

| Action | Feedback attendu |
|---|---|
| Création réussie | Toast success : `"Customer created successfully."` |
| Modification réussie | Toast success : `"Customer updated successfully."` |
| Suppression | Confirmation dialog → Toast success après confirmation |
| Erreur serveur | Toast error : message de l'API ou message générique |
| Chargement | `[loading]="true"` sur la table ou `p-progressSpinner` |
| Formulaire en envoi | `[loading]="saving"` sur le bouton Submit |
| Identifiants refusés | **Message dans le formulaire**, pas de toast |
| Session expirée (401) | Toast warning « Your session has ended » + retour à `/login` |
| Page interdite | Toast warning + retour au tableau de bord |

Utiliser toujours `NotificationService` :
```typescript
this.notification.success('Customer created successfully.');
this.notification.error('An error occurred. Please try again.');
```

---

## Patron de référence : le module Users

`features/users/` est le **modèle à copier** pour tous les futurs modules métier.

```
features/users/
├── users.component.*          Liste : toolbar + p-table + menu d'actions
├── user.model.ts              Types + options de listes déroulantes
├── user-form/                 Dialog création/édition
└── user-detail/               Drawer avec onglets Overview / History
```

### Structure d'une page liste

```html
<app-page-header title="Users" description="...">
  <p-button label="New user" icon="pi pi-plus" size="small" />
</app-page-header>

<div class="surface-panel">          <!-- carte blanche bordée -->
  <div class="table-toolbar">        <!-- recherche à gauche, filtres à droite -->
    <p-iconfield>…recherche…</p-iconfield>
    <span class="table-toolbar-spacer"></span>
    <p-select …filtres… />
  </div>

  <p-table [lazy]="true" …>…</p-table>
</div>
```

Classes utilitaires globales (définies dans `styles.scss`) :
`surface-panel` · `table-toolbar` · `table-toolbar-spacer` · `empty-state` · `field` · `field-grid` · `w-full`

### Règles de tableau ERP

- **Clic sur la ligne** → ouvre le détail. Ne jamais mettre 5 boutons par ligne.
- **Menu kebab** (`pi-ellipsis-v`) en dernière colonne pour les actions secondaires
- Le menu est **contextuel** : "Désactiver" pour un actif, "Activer" pour un inactif
- L'action destructive est en bas du menu, séparée, avec la classe `menu-danger`
- Recherche **debounce 350 ms** — ne pas appeler l'API à chaque frappe
- Un changement de filtre **revient page 1** (l'offset courant n'a plus de sens)

### Deux empty states distincts

| Situation | Message | Action |
|---|---|---|
| Aucune donnée | "No users yet" | Bouton de création |
| Filtres sans résultat | "No users match your filters" | Bouton "Clear filters" |

Confondre les deux est une erreur classique : l'utilisateur croit que sa base est vide.

### Actions destructives vs cycle de vie

La **désactivation** est l'action normale, la **suppression** reste exceptionnelle
et bloquée par une règle métier (il faut désactiver d'abord).
La confirmation doit toujours dire **qui** est concerné et **ce qui va se passer** :

> « Karim Haddad ne pourra plus se connecter. Vous pourrez réactiver le compte à tout moment. »

---

## Responsive

L'ERP est **desktop-first**. Les breakpoints :

| Taille | Comportement |
|---|---|
| Desktop (1200px+) | Sidebar 260px fixe, layout complet |
| Laptop (992px–1199px) | Sidebar 260px fixe, table avec scroll horizontal |
| Tablet (768px–991px) | Sidebar réduite ou cachée (à implémenter si besoin) |
| Mobile | Non supporté — pas la cible principale |

Utiliser `responsiveLayout="scroll"` sur `p-table` pour le scroll horizontal sur les petits écrans.

---

## Menu de ligne d'une liste de documents

Le menu `⋮` d'une ligne porte, dans cet ordre : **Open**, **Print**, puis (séparateur) les **étapes de statut** que le
statut du document et les droits de l'utilisateur permettent, puis (séparateur) **Delete draft** pour un brouillon.

| Élément | Règle |
|---|---|
| Étapes | Une par changement de statut : Issue / Validate, Accept, Reject, Back to issued, Confirm, Convert to order, Create receipt, Cancel order / receipt — les mêmes que sur la page du document |
| Confirmation | **Toujours** un `p-confirmDialog` : titre = l'étape, texte = ce qui va se passer (dont l'effet sur le stock), bouton = le verbe |
| Irréversible | Item en rouge (`styleClass: 'menu-danger'`), bouton de confirmation rouge (`p-button-danger`), icône d'avertissement |
| Droits | Une étape que l'utilisateur ne peut pas faire n'apparaît pas ; s'il n'en reste aucune, pas de séparateur |
| Après l'étape | Toast de succès puis rechargement de la liste — sauf une conversion, qui ouvre le document créé |

## Éditeur de document (devis, commandes)

Un document s'écrit sur une **page** (`/quotes/new`, `/quotes/:id`), pas dans un dialogue : les
lignes demandent de la largeur. Le même écran sert à écrire (brouillon) et à lire (émis).

| Élément | Règle |
|---|---|
| En-tête | `app-page-header` + `p-tag` du statut + boutons projetés ; **Save draft / Issue / Delete** sur un brouillon, boutons du workflow (Accept, Reject, Convert, Confirm, Cancel) sur un document émis |
| Lignes | `FormArray` dans un tableau HTML simple (pas `p-table` : cellules éditables) — `p-select` produit (vide = ligne libre), `pInputText`, `p-inputnumber`, `p-select` TVA ; `appendTo="body"` sur les listes déroulantes, sinon le conteneur défilant les coupe |
| Totaux | Bloc aligné à droite, **fournis par le serveur** (`/preview`, 300 ms après la dernière frappe) — jamais calculés dans le navigateur |
| Émission | `p-confirmDialog` : « Once issued, the document gets its number and can no longer be edited. » |
| Annulation / suppression | `p-confirmDialog` destructif (`p-button-danger`) |
| Document émis | Formulaire désactivé, colonnes d'action retirées, liens vers le document source / dérivé |

Composants PrimeNG introduits : aucun nouveau — `DatePicker` (dates), `MultiSelect` (taxes du
document), `Select` avec `filter` (client, produit), `Tag` (statut) étaient déjà validés.

---

## Feuille imprimée (aperçu et impression)

Un document imprimé s'ouvre dans un **onglet à part**, sans sidebar ni topbar (`/print/sales/:id`, `/print/purchases/:id`).

| Élément | Règle |
|---|---|
| Barre d'outils | Sticky, **cachée à l'impression** : « Show prices », « Show reference column » (`p-checkbox`), « Close », « Download PDF » (`p-button` secondaire, `[loading]` pendant la génération) et « Print » (`p-button`) |
| Feuille | A4 dessinée en millimètres, noir sur blanc, gris clairs seulement (survit à une imprimante noir et blanc) — police Arial, hors thème PrimeNG |
| Filigrane | BROUILLON / ANNULÉ en diagonale, gris très clair |
| Chargement / erreur | Icône + message centré (`pi-spin pi-spinner` / `pi-exclamation-circle`), jamais une page blanche |

Composants PrimeNG introduits : aucun nouveau (`Checkbox`, `Button`). Voir `docs/DEVELOPMENT.md` pour la règle « une feuille pour tous les documents ».

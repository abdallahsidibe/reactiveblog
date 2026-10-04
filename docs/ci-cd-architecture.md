# Architecture CI/CD — Décisions et rationale

---

## Séparer le build de la CI : intérêt et inconvénients

### Pourquoi certains équipes les séparent

L'idée vient de pipelines très matures où :
- Le **build** produit un artefact (`.jar`, image Docker) réutilisable
- La **CI** consomme cet artefact pour tester, analyser, déployer

```
build.yml  → compile + package → artefact stocké (cache/registry)
ci.yml     → download artefact → test + lint + scan sécurité
```

Ça a du sens quand le build prend 10+ minutes et qu'on veut le réutiliser
entre plusieurs workflows sans recompiler.

---

### Inconvénients de la séparation

| Inconvénient | Impact |
|---|---|
| Complexité inutile | Deux fichiers à maintenir pour ce qui est conceptuellement une seule chose |
| Dépendances entre workflows | `ci.yml` doit attendre `build.yml` → orchestration fragile avec `workflow_run` |
| Debugging plus difficile | Un échec peut venir de l'un ou l'autre, plus difficile à tracer |
| Partage d'artefacts coûteux | Stocker/télécharger des artefacts entre jobs consomme du storage et du temps |
| Overkill pour un petit projet | Sur un monorepo Spring + Angular, le build compile en < 2 min |

---

### Dans un cadre entreprise

La séparation devient **légitime** à partir d'un certain niveau de maturité.

#### Monorepo multi-équipes
```
build/backend.yml   → compile le jar, push image Docker
build/frontend.yml  → ng build, push image Nginx
ci/backend.yml      → tests, SonarQube, OWASP dependency check
ci/frontend.yml     → jest, cypress, lighthouse
```
Chaque équipe ne déclenche que ce qui la concerne. Un commit sur `frontend/`
ne relance pas le build backend.

#### Artefacts partagés entre pipelines
```
build.yml → produit image Docker → push sur ECR/Artifactory
ci.yml    → pull l'image → tests d'intégration
cd.yml    → promeut la même image en staging/prod
```
On teste **exactement** ce qu'on déploie — pas un rebuild à chaque étape.

#### Séparation des responsabilités (compliance)
Certaines entreprises imposent que le build soit signé et traçable séparément
des tests pour des raisons d'audit (SOC2, ISO 27001).

#### Inconvénients spécifiques en entreprise

| Inconvénient | Conséquence concrète |
|---|---|
| **Coût GitHub Actions** | Chaque workflow séparé consomme des minutes facturées indépendamment |
| **Latence cumulée** | Build → upload artefact → CI download → test : +3-5 min vs un seul pipeline |
| **Versioning des artefacts** | Qui gère le nommage ? Que se passe-t-il si le cache expire ? |
| **Onboarding difficile** | Un nouveau dev doit comprendre l'orchestration entre 5-6 fichiers YAML |
| **Flaky `workflow_run`** | Le trigger entre workflows est peu fiable sur GitHub Actions (race conditions connues) |
| **Drift entre environnements** | Si build et CI tournent sur des runners différents, des bugs environnement apparaissent |

---

## Décision retenue pour ce projet

Un seul fichier `ci.yml` qui fait **build + test + (optionnel) package Docker**.
Simple, lisible, maintenable.

### Structure cible

```
.github/workflows/
├── ci.yml          # PR : build + test + lint + scan (déclenché sur push/PR)
├── release.yml     # Tag : build final + push registry + changelog
└── deploy.yml      # Manuel ou CD : déploiement staging/prod
```

**Règle** : séparer par **intention** (vérifier vs livrer vs déployer),
pas par étape technique (build vs test).

---

## Workflow CI cible (`ci.yml`)

Déclenché sur chaque push vers `develop` et chaque PR :

```
push / PR
    │
    ▼
┌─────────────────────────────────────────────┐
│  Job : build-and-test                        │
│                                             │
│  1. Checkout                                │
│  2. Setup Java 21                           │
│  3. Cache Maven (~/.m2)                     │
│  4. mvn verify  (compile + test)            │
│  5. Setup Node 20                           │
│  6. npm ci                                  │
│  7. ng build --configuration=production     │
└─────────────────────────────────────────────┘
```

### Déclencheurs

| Événement | Branche | Action |
|---|---|---|
| `push` | `develop`, `feature/*` | Build + test complet |
| `pull_request` | `develop`, `main` | Build + test + rapport |
| `push` (tag `v*`) | — | Build + push image Docker |

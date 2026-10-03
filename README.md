# ReactiveBlog — Spring WebFlux + Angular

Application Full Stack de blog construite pour apprendre **Spring WebFlux** et **Angular** à travers un exemple concret. La chaîne est entièrement non-bloquante de l'interface jusqu'à la base de données.

---

## Stack technique

| Couche | Technologie |
|---|---|
| Frontend | Angular 17 · TypeScript · RxJS · Reactive Forms |
| Backend | Java 21 · Spring Boot 3.3 · Spring WebFlux · Project Reactor |
| Données | Spring Data R2DBC · PostgreSQL 16+ |
| Migrations | Flyway |
| Tests | StepVerifier · WebTestClient · JUnit 5 · Mockito |
| Build | Maven 3.9 · Angular CLI 17 |
| Infrastructure | Docker · Docker Compose |

---

## Pourquoi WebFlux + R2DBC ?

### Approche classique (bloquante)

```
Thread HTTP ──────────[bloqué en attente JDBC]──────────▶ libre
```

Spring MVC + JPA utilise JDBC, une API **synchrone et bloquante**. Chaque requête HTTP occupe un thread dédié pendant toute la durée de l'accès base de données. Sous forte charge, le pool de threads se sature.

### Approche réactive (non-bloquante)

```
Thread Netty ──▶ envoie SQL ──▶ libre ──▶ traite d'autres requêtes
                                    ▲
                      PostgreSQL répond → thread réassigné
```

Spring WebFlux + R2DBC libère le thread dès l'envoi de la requête SQL. Quand PostgreSQL répond, un thread est réassigné pour continuer. Résultat : le même hardware supporte une concurrence bien plus élevée.

| Aspect | Spring MVC + JPA | Spring WebFlux + R2DBC |
|---|---|---|
| Serveur | Tomcat (thread-per-request) | Netty (event loop) |
| Threads sous charge | 1 bloqué par requête | ~nb cœurs CPU, jamais bloqués |
| Accès BDD | JDBC — synchrone | R2DBC — async TCP |
| Type de retour | `List<T>`, `Optional<T>` | `Flux<T>`, `Mono<T>` |
| Lazy loading | Oui (Hibernate) | Non — tout est explicite |

> **Règle fondamentale :** ne jamais appeler `.block()` dans le code applicatif. Ne jamais utiliser JDBC/JPA dans un controller WebFlux. Ces pratiques bloquent l'event loop Netty et annulent tous les bénéfices réactifs.

---

## Architecture

```
Angular (4200)
    │  Observable / async pipe
    ▼
HttpClient → GET /api/articles
    │
    ▼  HTTP / JSON
Spring WebFlux — Netty (8080)
    │
    ▼  Mono<T> / Flux<T>
ArticleController
    │
    ▼
ArticleService
    │
    ▼
ArticleRepository (ReactiveCrudRepository)
    │
    ▼  R2DBC — TCP non-bloquant
PostgreSQL (5432)
    │
    ▼
Mono/Flux remonte la chaîne → réponse HTTP → Observable Angular
```

---

## Structure du projet

```
reactiveblog/
├── backend/
│   ├── pom.xml
│   └── src/main/java/com/reactiveblog/
│       ├── ReactiveBlogApplication.java
│       ├── config/
│       │   └── R2dbcConfig.java             # @EnableR2dbcRepositories + @EnableR2dbcAuditing
│       ├── model/
│       │   └── Article.java                 # Entité R2DBC (@Table, @Id, @CreatedDate)
│       ├── dto/
│       │   ├── ArticleRequestDto.java        # Payload entrant + validation Bean Validation
│       │   └── ArticleResponseDto.java       # Payload sortant (id, dates Instant)
│       ├── mapper/
│       │   └── ArticleMapper.java            # Article ↔ DTO (synchrone, en mémoire)
│       ├── repository/
│       │   └── ArticleRepository.java        # ReactiveCrudRepository + @Query custom
│       ├── service/
│       │   └── ArticleService.java           # Logique métier — Mono/Flux, flatMap, switchIfEmpty
│       ├── controller/
│       │   └── ArticleController.java        # Endpoints WebFlux (@RestController)
│       └── exception/
│           ├── ArticleNotFoundException.java
│           ├── ArticleAlreadyExistsException.java
│           └── GlobalExceptionHandler.java   # @RestControllerAdvice
│
├── frontend/
│   └── src/app/
│       ├── app.config.ts                     # provideRouter + provideHttpClient
│       ├── app.routes.ts                     # Routes lazy-loaded
│       ├── app.component.ts                  # Shell : navbar + router-outlet
│       ├── core/
│       │   ├── models/article.model.ts       # Interfaces Article + ArticleRequest
│       │   └── services/article.service.ts   # HttpClient → Observable<T>
│       └── features/
│           ├── article-list/                 # Liste + recherche réactive (RxJS pipeline)
│           ├── article-detail/               # Détail + suppression
│           └── article-form/                 # Formulaire création / édition
│
├── docker-compose.yml                        # postgres + backend + frontend
├── docker-compose.dev.yml                    # postgres uniquement (dev local)
└── README.md
```

---

## Démarrage en local

### Prérequis

- Java 21+
- Maven 3.9+
- Node.js 20+
- Angular CLI 17+ (`npm install -g @angular/cli`)
- Docker Desktop

### 1 — Base de données

```bash
docker compose -f docker-compose.dev.yml up -d
```

Flyway exécute `V1__create_articles_table.sql` automatiquement au premier démarrage du backend.

### 2 — Backend

```bash
cd backend
mvn spring-boot:run
# API disponible sur http://localhost:8080
```

Variables d'environnement (valeurs par défaut) :

| Variable | Défaut |
|---|---|
| `DB_HOST` | `localhost` |
| `DB_PORT` | `5432` |
| `DB_NAME` | `reactiveblog` |
| `DB_USER` | `postgres` |
| `DB_PASSWORD` | `postgres` |

### 3 — Frontend

```bash
cd frontend
ng serve
# App disponible sur http://localhost:4200
# Proxy : /api/* → http://localhost:8080 (évite CORS en dev)
```

### 4 — Tests backend

```bash
cd backend
mvn test
# 24 tests : StepVerifier (service) + WebTestClient (controller)
```

### Tout en Docker (simulation production)

```bash
docker compose up --build
# postgres  → port 5432
# backend   → port 8080
# frontend  → port 4200 (Nginx)
```

---

## Contrat d'API REST

Base URL : `http://localhost:8080/api/articles`

| Méthode | URL | Body | Succès | Erreurs |
|---|---|---|---|---|
| `GET` | `/` | — | `200` tableau JSON | — |
| `GET` | `/?search=spring` | — | `200` tableau filtré | — |
| `GET` | `/:id` | — | `200` article | `404` |
| `POST` | `/` | `ArticleRequest` | `201` article créé | `400`, `409` |
| `PUT` | `/:id` | `ArticleRequest` | `200` article mis à jour | `400`, `404` |
| `DELETE` | `/:id` | — | `204` No Content | `404` |

**ArticleRequest :**
```json
{
  "title":   "Spring WebFlux en pratique",
  "content": "Contenu de l'article...",
  "author":  "Alice"
}
```

**ArticleResponse :**
```json
{
  "id":        1,
  "title":     "Spring WebFlux en pratique",
  "content":   "Contenu de l'article...",
  "author":    "Alice",
  "createdAt": "2026-09-29T21:28:01.422Z",
  "updatedAt": "2026-09-29T21:28:01.422Z"
}
```

**Format d'erreur :**
```json
{
  "timestamp": "2026-09-29T21:28:01.422Z",
  "status":    404,
  "error":     "Not Found",
  "message":   "Article introuvable avec l'id : 42"
}
```

---

## Concepts Reactor clés

### Mono vs Flux

```java
Mono<T>  // 0 ou 1 élément → findById(), create(), update(), delete()
Flux<T>  // 0 à N éléments → findAll(), search()
```

Un Publisher ne fait **rien** tant qu'il n'est pas souscrit. WebFlux s'abonne automatiquement quand le controller retourne un `Mono` ou `Flux`.

### map vs flatMap

```java
// map : transformation synchrone (pas d'I/O)
.map(mapper::toDto)                        // Article → ArticleResponseDto

// flatMap : transformation async (retourne un Publisher)
.flatMap(existing -> repository.save(...)) // Article → Mono<Article>
```

Utiliser `map` avec une fonction qui retourne un `Mono` produit `Mono<Mono<T>>` — un Publisher non souscrit. `flatMap` aplatit ce niveau.

### switchIfEmpty — gérer le cas vide

```java
repository.findById(id)
    .switchIfEmpty(Mono.error(new ArticleNotFoundException(id)))
    // Si vide → ArticleNotFoundException → GlobalExceptionHandler → HTTP 404
    .map(mapper::toDto)
```

### Opérateurs utilisés

| Opérateur | Usage |
|---|---|
| `map` | Article → DTO (synchrone) |
| `flatMap` | Enchaîner un I/O (save, delete) |
| `switchIfEmpty` | Transformer un vide en erreur |
| `filter` | Rejeter si condition non remplie |
| `then` | Ignorer la valeur, retourner `Mono<Void>` |
| `doOnNext` | Logger sans altérer le flux |

### StepVerifier — tester des Mono/Flux

```java
StepVerifier.create(service.findById(99L))
    .expectError(ArticleNotFoundException.class)
    .verify(); // ← OBLIGATOIRE — sans verify() le Publisher n'est jamais souscrit
```

---

## Concepts RxJS (Angular)

### Correspondance Reactor ↔ RxJS

| Spring Reactor | RxJS | Usage |
|---|---|---|
| `Mono<Article>` | `Observable<Article>` | Une valeur |
| `Flux<Article>` | `Observable<Article[]>` | Un tableau |
| `Mono<Void>` | `Observable<void>` | Complétion |
| `.map()` | `.pipe(map())` | Transformation |
| `.flatMap()` | `.pipe(switchMap())` | Async + annulation |

### Pipeline de recherche réactive

```typescript
this.articles$ = this.searchControl.valueChanges.pipe(
  startWith(''),           // déclenche findAll() au chargement
  debounceTime(300),       // attend 300ms après la dernière frappe
  distinctUntilChanged(),  // ignore si valeur identique
  switchMap(keyword =>     // annule la requête précédente
    keyword ? service.search(keyword) : service.findAll()
  )
);
```

`switchMap` est crucial : sans lui, les réponses HTTP peuvent arriver dans le désordre si l'utilisateur tape vite.

### async pipe

```html
<article *ngFor="let a of articles$ | async">{{ a.title }}</article>
```

Le pipe `async` s'abonne à l'Observable et se **désabonne automatiquement** à la destruction du composant. Pas de fuite mémoire, pas de `.unsubscribe()` manuel.

---

## Les trois piliers réactifs

| Outil | Rôle |
|---|---|
| **Spring WebFlux** | Construit l'application serveur réactive (Netty, Mono/Flux) |
| **Spring Data R2DBC** | Accès réactif à PostgreSQL (pas de JDBC, pas de blocage) |
| **WebClient** | Appels HTTP sortants réactifs vers des services externes |

Ces trois outils sont complémentaires et non interchangeables. WebFlux ne remplace pas R2DBC, et R2DBC ne remplace pas WebClient.

---

## Étapes de construction

1. Architecture globale
2. Initialisation Spring Boot (`pom.xml`, classe principale)
3. Configuration PostgreSQL + R2DBC + Flyway
4. Modèle `Article` + DTOs + Mapper
5. `ArticleRepository` (ReactiveCrudRepository)
6. `ArticleService` (Mono/Flux, opérateurs Reactor)
7. `ArticleController` (endpoints WebFlux)
8. Tests (StepVerifier + WebTestClient)
9. Frontend Angular (composants standalone, routing, service HTTP)
10. Connexion Angular ↔ Backend (proxy dev, test bout en bout)
11. WebClient (appel API externe réactif)
12. Gestion globale des erreurs
13. Docker / Docker Compose
14. Tests finaux

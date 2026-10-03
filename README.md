# ReactiveBlog — Spring WebFlux + Angular

Application Full Stack de blog construite pour apprendre **Spring WebFlux** et **Angular** à travers un exemple concret. La chaîne est entièrement non-bloquante de l'interface jusqu'à la base de données, avec un cache Redis réactif.

---

## Stack technique

| Couche | Technologie |
|---|---|
| Frontend | Angular 17 · TypeScript · RxJS · Reactive Forms |
| Backend | Java 21 · Spring Boot 3.3 · Spring WebFlux · Project Reactor |
| Données | Spring Data R2DBC · PostgreSQL 16+ |
| Cache | Redis 7 · Lettuce (client réactif) · Spring Data Redis Reactive |
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
    │  ┌─────────────────────────────────┐
    │  │ Cache Redis (Lettuce)           │
    │  │  HIT  → retour immédiat        │
    │  │  MISS → BDD puis mise en cache │
    │  └─────────────────────────────────┘
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
│       │   ├── R2dbcConfig.java             # @EnableR2dbcRepositories + @EnableR2dbcAuditing
│       │   └── RedisConfig.java             # ReactiveRedisTemplate<String, ArticleResponseDto>
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
│       │   └── ArticleService.java           # Logique métier + cache Redis
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
├── docker-compose.yml                        # postgres + redis + backend + frontend
├── docker-compose.dev.yml                    # postgres + redis (dev local)
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

### 1 — Infrastructure (PostgreSQL + Redis)

```bash
docker compose -f docker-compose.dev.yml up -d
```

Démarre :
- **PostgreSQL** sur le port `5432` — Flyway exécute `V1__create_articles_table.sql` automatiquement au premier démarrage du backend
- **Redis** sur le port `6379`

### 2 — Backend

```bash
cd backend
mvn spring-boot:run
# API disponible sur http://localhost:8080
```

Variables d'environnement (valeurs par défaut) :

| Variable | Défaut | Description |
|---|---|---|
| `DB_HOST` | `localhost` | Hôte PostgreSQL |
| `DB_PORT` | `5432` | Port PostgreSQL |
| `DB_NAME` | `reactiveblog` | Nom de la base |
| `DB_USER` | `postgres` | Utilisateur |
| `DB_PASSWORD` | `postgres` | Mot de passe |
| `REDIS_HOST` | `localhost` | Hôte Redis |
| `REDIS_PORT` | `6379` | Port Redis |

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
# redis     → port 6379
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

## Cache Redis

### Pourquoi Redis ?

Sans cache, chaque requête `GET /api/articles` génère un aller-retour PostgreSQL. Redis stocke les résultats en mémoire avec un TTL : les lectures suivantes retournent directement depuis Redis sans toucher la base.

```
Sans cache :  GET → PostgreSQL → 10-50 ms
Avec cache :  GET → Redis HIT  → < 1 ms
```

### Stratégie de cache

| Opération | Clé Redis | Type | TTL | Action |
|---|---|---|---|---|
| `findAll` | `articles:all` | List | 10 min | HIT → `LRANGE 0 -1` / MISS → BDD + `RPUSH` |
| `findById(id)` | `article:{id}` | String | 10 min | HIT → `GET` / MISS → BDD + `SET` |
| `create` | `articles:all` | — | — | `DEL articles:all` |
| `update(id)` | `articles:all`, `article:{id}` | — | — | `DEL` des deux clés |
| `delete(id)` | `articles:all`, `article:{id}` | — | — | `DEL` des deux clés |
| `search` | — | — | — | Non caché (résultats trop dynamiques) |

### Fonctionnement détaillé

**findAll — cache MISS (premier appel) :**
```
redis.hasKey("articles:all") → false
  → repository.findAllByOrderByCreatedAtDesc()
  → collectList()
  → redis RPUSH "articles:all" [article1, article2, ...]
  → redis EXPIRE "articles:all" 600
  → retourne les articles
```

**findAll — cache HIT (appels suivants) :**
```
redis.hasKey("articles:all") → true
  → redis LRANGE "articles:all" 0 -1
  → retourne directement (pas de BDD)
```

**findById — cache MISS puis HIT :**
```
# MISS
redis.opsForValue().get("article:1") → vide
  → repository.findById(1)
  → redis SET "article:1" "{...}" PX 600000
  → retourne le DTO

# HIT (appel suivant)
redis.opsForValue().get("article:1") → ArticleResponseDto
  → retourne directement
```

**Invalidation sur écriture :**
```
# create → invalide la liste
redis DEL "articles:all"

# update(1) → invalide liste + article individuel
redis DEL "articles:all" "article:1"

# delete(1) → invalide liste + article individuel
redis DEL "articles:all" "article:1"
```

### Sérialisation

Les objets `ArticleResponseDto` sont sérialisés en JSON via `Jackson2JsonRedisSerializer` configuré avec l'`ObjectMapper` Spring Boot (JavaTimeModule inclus → `Instant` sérialisé en ISO-8601).

```
redis-cli get "article:1"
→ {"id":1,"title":"Spring WebFlux","content":"...","author":"Alice",
   "createdAt":"2026-10-03T20:00:00Z","updatedAt":"2026-10-03T20:00:00Z"}
```

### Observer Redis en temps réel

```bash
# Via Docker (pas besoin d'installer redis-cli)
docker exec -it reactiveblog-redis-dev redis-cli monitor
```

Sortie observée lors des requêtes :

```
# GET /api/articles/1 — MISS puis SET
"GET" "article:1"
"SET" "article:1" "{\"id\":1,...}" "PX" "600000"

# GET /api/articles/1 — HIT (GET seul, pas de BDD)
"GET" "article:1"

# GET /api/articles — MISS
"EXISTS" "articles:all"
"RPUSH" "articles:all" "{\"id\":1,...}" "{\"id\":2,...}"
"EXPIRE" "articles:all" "600"

# POST /api/articles — invalide la liste
"DEL" "articles:all"

# DELETE /api/articles/1 — invalide les deux clés
"DEL" "articles:all" "article:1"
```

**Autres commandes utiles :**

```bash
# Lister toutes les clés en cache
docker exec -it reactiveblog-redis-dev redis-cli keys "*"

# Lire un article en cache
docker exec -it reactiveblog-redis-dev redis-cli get "article:1"

# Voir le TTL restant (secondes)
docker exec -it reactiveblog-redis-dev redis-cli ttl "article:1"

# Lire la liste articles:all
docker exec -it reactiveblog-redis-dev redis-cli lrange "articles:all" 0 -1

# Vider le cache manuellement
docker exec -it reactiveblog-redis-dev redis-cli flushall
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
| `flatMap` | Enchaîner un I/O (save, delete, redis) |
| `flatMapMany` | `Mono<List>` → `Flux` (cache findAll) |
| `switchIfEmpty` | Transformer un vide en erreur ou déclencher un fallback |
| `filter` | Rejeter si condition non remplie |
| `then` | Ignorer la valeur, retourner `Mono<Void>` |
| `thenReturn` | `then()` + émettre une valeur fixe |
| `thenMany` | `then()` + souscrire à un `Flux` |
| `doOnNext` | Logger sans altérer le flux |
| `collectList` | `Flux<T>` → `Mono<List<T>>` (pour RPUSH Redis) |

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

## Les piliers réactifs

| Outil | Rôle |
|---|---|
| **Spring WebFlux** | Construit l'application serveur réactive (Netty, Mono/Flux) |
| **Spring Data R2DBC** | Accès réactif à PostgreSQL (pas de JDBC, pas de blocage) |
| **Spring Data Redis Reactive** | Cache réactif via Lettuce (Mono/Flux, pas de blocage) |
| **WebClient** | Appels HTTP sortants réactifs vers des services externes |

Ces outils sont complémentaires et non interchangeables. Lettuce (client Redis réactif) respecte le modèle non-bloquant de Netty — contrairement à Jedis qui bloquerait l'event loop.

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
15. **Cache Redis réactif** (Lettuce, ReactiveRedisTemplate, stratégie HIT/MISS/invalidation)

# Guide de test

## Vue d'ensemble

```
Tests unitaires          → Mockito + StepVerifier     (pas de serveur, pas de BDD)
Tests de slice           → @WebFluxTest + WebTestClient (pas de BDD)
Tests d'intégration *    → Testcontainers MongoDB      (BDD réelle, serveur réel)
Tests manuels            → curl / httpie               (stack complète via Docker)

* À implémenter (voir section dédiée)
```

---

## Prérequis

| Outil | Version | Usage |
|---|---|---|
| Java | 21 | Backend |
| Maven | 3.9+ | Build + tests |
| Docker | 24+ | MongoDB + Redis |
| `curl` + `jq` | any | Tests manuels |

---

## 1. Tests automatisés

### Lancer tous les tests unitaires

```bash
cd backend
mvn test --no-transfer-progress
```

Résultat attendu : **24 tests, 0 failures, 0 errors**.

### Ce qui est couvert

**`ArticleServiceTest`** — tests unitaires purs (`@ExtendWith(MockitoExtension.class)`)

| Classe | Test | Scénario |
|---|---|---|
| `FindAll` | `shouldReturnAllArticles` | Cache MISS → BDD retourne 1 article |
| `FindAll` | `shouldReturnEmptyFlux` | Cache MISS → BDD vide |
| `FindById` | `shouldReturnArticleWhenFound` | Cache MISS → article trouvé |
| `FindById` | `shouldReturnErrorWhenNotFound` | Cache MISS → `ArticleNotFoundException` |
| `Create` | `shouldCreateArticle` | Titre unique → article créé |
| `Create` | `shouldRejectDuplicateTitle` | Titre existant → `ArticleAlreadyExistsException` |
| `Update` | `shouldUpdateArticle` | Article existant → mis à jour |
| `Update` | `shouldReturnErrorWhenNotFound` | Id inconnu → `ArticleNotFoundException` |
| `Delete` | `shouldDeleteArticle` | Article existant → supprimé |
| `Delete` | `shouldReturnErrorWhenNotFound` | Id inconnu → `ArticleNotFoundException` |
| `Search` | `shouldReturnMatchingArticles` | Keyword match → résultats |
| `Search` | `shouldReturnEmptyWhenNoMatch` | Keyword sans résultat → Flux vide |

Redis est stubbé en **cache MISS** par défaut dans le `@BeforeEach` — les tests vérifient la logique BDD sans avoir besoin d'un Redis réel.

**`ArticleControllerTest`** — tests de slice (`@WebFluxTest`)

| Classe | Test | HTTP attendu |
|---|---|---|
| `GetAll` | `shouldReturn200WithArticles` | 200 + liste de 2 |
| `GetAll` | `shouldReturn200WithEmptyList` | 200 + liste vide |
| `GetAll` | `shouldDelegateToSearchWhenQueryPresent` | 200 + résultat filtré |
| `GetById` | `shouldReturn200WhenFound` | 200 + article |
| `GetById` | `shouldReturn404WhenNotFound` | 404 |
| `Create` | `shouldReturn201WhenCreated` | 201 + article créé |
| `Create` | `shouldReturn400WhenInvalidBody` | 400 (titre vide) |
| `Update` | `shouldReturn200WhenUpdated` | 200 + article modifié |
| `Update` | `shouldReturn404WhenNotFound` | 404 |
| `Delete` | `shouldReturn204WhenDeleted` | 204 No Content |
| `Delete` | `shouldReturn404WhenNotFound` | 404 |

### Rapport de couverture JaCoCo

```bash
mvn verify --no-transfer-progress
# Rapport HTML : backend/target/site/jacoco/index.html
open target/site/jacoco/index.html
```

---

## 2. Tests manuels (stack complète)

### Démarrer l'infrastructure

```bash
# MongoDB + Redis
docker compose -f docker-compose.dev.yml up -d

# Vérifier
docker compose -f docker-compose.dev.yml ps
```

### Démarrer le backend

```bash
cd backend
mvn spring-boot:run
```

L'application est disponible sur `http://localhost:8080`.

---

### Scénarios de test

#### Créer un article

```bash
curl -s -X POST http://localhost:8080/api/articles \
  -H "Content-Type: application/json" \
  -d '{"title":"Spring WebFlux","content":"Le modèle réactif de Spring","author":"Alice"}' | jq
```

Réponse attendue (HTTP 201) :
```json
{
  "id": "6701ab3c4f1e2d3a9b8c7d6e",
  "title": "Spring WebFlux",
  "content": "Le modèle réactif de Spring",
  "author": "Alice",
  "createdAt": "2026-10-04T15:30:00Z",
  "updatedAt": "2026-10-04T15:30:00Z"
}
```

> L'`id` est un **ObjectId MongoDB** (24 caractères hexadécimaux), pas un entier.

#### Lister les articles

```bash
curl -s http://localhost:8080/api/articles | jq
```

Au deuxième appel : cache **HIT Redis** (vérifiable dans les logs `Cache HIT : articles:all`).

#### Récupérer un article par id

```bash
# Remplacer <id> par l'ObjectId retourné à la création
curl -s http://localhost:8080/api/articles/<id> | jq
```

#### Rechercher par mot-clé

```bash
curl -s "http://localhost:8080/api/articles?search=WebFlux" | jq
```

#### Mettre à jour un article

```bash
curl -s -X PUT http://localhost:8080/api/articles/<id> \
  -H "Content-Type: application/json" \
  -d '{"title":"Spring WebFlux (mis à jour)","content":"Contenu modifié","author":"Alice"}' | jq
```

#### Supprimer un article

```bash
curl -s -X DELETE http://localhost:8080/api/articles/<id> -v
# HTTP 204 No Content attendu
```

#### Tester la validation (400)

```bash
# Titre vide → 400
curl -s -X POST http://localhost:8080/api/articles \
  -H "Content-Type: application/json" \
  -d '{"title":"","content":"contenu","author":"Bob"}' | jq

# Titre trop court (< 3 chars) → 400
curl -s -X POST http://localhost:8080/api/articles \
  -H "Content-Type: application/json" \
  -d '{"title":"AB","content":"contenu","author":"Bob"}' | jq
```

#### Tester le doublon (409)

```bash
# Créer le même titre deux fois
curl -s -X POST http://localhost:8080/api/articles \
  -H "Content-Type: application/json" \
  -d '{"title":"Titre unique","content":"contenu","author":"Bob"}' | jq

curl -s -X POST http://localhost:8080/api/articles \
  -H "Content-Type: application/json" \
  -d '{"title":"Titre unique","content":"autre contenu","author":"Charlie"}' | jq
# HTTP 409 Conflict attendu
```

---

### Inspecter MongoDB directement

```bash
# Lister tous les documents
docker exec -it reactiveblog-mongo-dev mongosh reactiveblog \
  --eval "db.articles.find().pretty()"

# Compter les documents
docker exec -it reactiveblog-mongo-dev mongosh reactiveblog \
  --eval "db.articles.countDocuments()"

# Chercher par auteur
docker exec -it reactiveblog-mongo-dev mongosh reactiveblog \
  --eval "db.articles.find({ author: 'Alice' }).pretty()"
```

### Inspecter le cache Redis

```bash
# Lister les clés en cache
docker exec -it reactiveblog-redis-dev redis-cli keys "*"

# Vérifier le cache de la liste
docker exec -it reactiveblog-redis-dev redis-cli lrange "articles:all" 0 -1

# Vérifier le TTL d'une clé (en secondes)
docker exec -it reactiveblog-redis-dev redis-cli ttl "articles:all"

# Vider le cache manuellement
docker exec -it reactiveblog-redis-dev redis-cli flushall
```

---

## 3. Tests d'intégration (à implémenter)

Les tests d'intégration (`*IT.java`) sont exécutés par Maven Failsafe dans la CI.
Ils nécessitent une base MongoDB réelle — Testcontainers est la solution recommandée.

### Dépendance à ajouter dans `pom.xml`

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mongodb</artifactId>
    <scope>test</scope>
</dependency>
```

### Structure d'un test d'intégration

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ArticleIT {

    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
    }

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void shouldCreateAndRetrieveArticle() {
        // POST → 201
        // GET  → 200 avec l'article créé
    }
}
```

### Lancer uniquement les tests d'intégration

```bash
mvn failsafe:integration-test failsafe:verify --no-transfer-progress
```

---

## 4. Arrêter l'infrastructure

```bash
docker compose -f docker-compose.dev.yml down

# Supprimer aussi les volumes (données MongoDB + Redis)
docker compose -f docker-compose.dev.yml down -v
```

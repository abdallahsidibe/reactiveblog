# Choix de base de données

## Stack actuelle : PostgreSQL + R2DBC + Flyway

Le backend est entièrement non-bloquant, sauf au démarrage pour les migrations SQL.

```
WebFlux (Netty)
    │
    ├── R2DBC → PostgreSQL      (réactif, runtime)
    └── JDBC  → PostgreSQL      (bloquant, startup uniquement — Flyway)
```

Deux connexions vers la même base, deux usages distincts :

| Connexion | Protocole | Utilisée par | Moment |
|---|---|---|---|
| `r2dbc:postgresql://...` | R2DBC | `ArticleRepository`, `R2dbcConfig` | Runtime |
| `jdbc:postgresql://...` | JDBC  | Flyway uniquement | Démarrage |

### Pourquoi Flyway dans un projet réactif ?

PostgreSQL est une base relationnelle et schématisée. La table `articles` avec ses colonnes, types et contraintes doit exister **avant** le premier accès applicatif. Flyway exécute ces DDL au démarrage (`V1__create_articles_table.sql`), avant que Netty accepte des requêtes.

Flyway ne supporte pas R2DBC — il a besoin d'une connexion JDBC classique. C'est le seul endroit dans le projet où du code bloquant est utilisé intentionnellement.

### Ce que Flyway garantit

- Historique versionné des évolutions de schéma (V1, V2, V3…)
- Reproductibilité : même schéma en dev, CI, staging, production
- Discipline d'équipe : tout changement de structure passe par une migration auditée en git

---

## Alternative analysée : MongoDB Reactive

### Ce qui change structurellement

```
WebFlux (Netty)
    │
    └── ReactiveMongoDriver → MongoDB   (réactif natif, runtime)
```

Flyway disparaît entièrement. MongoDB est **schemaless** : aucune DDL nécessaire avant d'écrire un document. La collection `articles` est créée au premier `save()`.

### Impacts techniques

**`pom.xml`**

| Action | Dépendance |
|---|---|
| Supprimer | `spring-boot-starter-data-r2dbc` |
| Supprimer | `r2dbc-postgresql` |
| Supprimer | `flyway-core`, `flyway-database-postgresql` |
| Supprimer | `postgresql` (JDBC, plus nécessaire) |
| Ajouter | `spring-boot-starter-data-mongodb-reactive` |
| Ajouter (tests) | Testcontainers MongoDB ou `de.flapdoodle.embed.mongo` |

**Modèle `Article`**

| Avant | Après |
|---|---|
| `@Table("articles")` | `@Document("articles")` |
| `id: Long` (BIGSERIAL PostgreSQL) | `id: String` (ObjectId MongoDB, 24 chars hex) |
| `@EnableR2dbcAuditing` | `@EnableMongoAuditing` |

Le changement de type d'ID (`Long` → `String`) est une **rupture d'API** : les routes Angular (`/api/articles/1` → `/api/articles/507f1f77bcf86cd799439011`), les DTOs, les clés Redis et tous les tests sont impactés.

**Repository**

| Avant | Après |
|---|---|
| `ReactiveCrudRepository<Article, Long>` | `ReactiveMongoRepository<Article, String>` |
| `@Query` SQL avec `ILIKE` | `@Query` MongoDB avec `$regex` |
| `findByAuthorIgnoreCase` | Fonctionne nativement |

**`application.yml`**

Supprimer `spring.r2dbc` et `spring.flyway`.
Ajouter :
```yaml
spring:
  data:
    mongodb:
      uri: mongodb://${MONGO_HOST:localhost}:${MONGO_PORT:27017}/${MONGO_DB:reactiveblog}
```

**Redis** : inchangé. Le cache est orthogonal au choix de base de données.

**`docker-compose.yml`** : remplacer `postgres:16-alpine` par `mongo:7`.

---

## Comparaison pour ce projet

| Critère | PostgreSQL + R2DBC | MongoDB Reactive |
|---|---|---|
| **Modèle de données** | Tabulaire, schéma strict | Document, schemaless |
| **Migrations** | Flyway — versionné, auditable | Aucune — liberté et risque |
| **Réactivité** | R2DBC mature, pool configuré | Driver Reactive natif, très complet |
| **Recherche texte** | `ILIKE` simple, index possible | `$text` + index texte, plus puissant |
| **Transactions** | ACID native | Disponible sur replica set uniquement |
| **Relations futures** | FK, JOIN natifs | Embedding ou référence manuelle |
| **Type d'ID** | `Long` auto-incrémenté | `String` (ObjectId) |
| **Complexité opérationnelle** | Double connexion (JDBC + R2DBC) | Connexion unique |

---

## Décision

**PostgreSQL conservé** pour ce projet.

Justification : le modèle de données a vocation à évoluer (commentaires, tags, utilisateurs). PostgreSQL gère ces relations nativement avec des clés étrangères et des jointures. MongoDB nécessiterait soit de l'embedding (dénormalisation à maintenir) soit des références manuelles sans garantie d'intégrité.

Flyway est accepté malgré la connexion JDBC bloquante car :
1. Il s'exécute une seule fois au démarrage, hors du chemin réactif
2. Il apporte une garantie de schéma indispensable en équipe
3. L'alternative (migrations manuelles) est plus risquée

**MongoDB serait pertinent** si le projet reste centré sur une entité unique (articles) sans relations, ou si la flexibilité de schéma est une exigence (contenu hétérogène, champs variables par type d'article).

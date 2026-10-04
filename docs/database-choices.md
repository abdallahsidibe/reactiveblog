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

**MongoDB adopté** (`feature/003-add-mongodb`).

Le projet est actuellement centré sur une entité unique (`Article`). MongoDB simplifie la stack : plus de double connexion JDBC/R2DBC, plus de Flyway, un seul driver réactif de bout en bout.

### Ce que Flyway disparaît signifie concrètement

Flyway n'existait que pour une raison : PostgreSQL est schématisé. Avant le premier accès, la table `articles` devait être créée via une DDL. Flyway exécutait ce DDL au démarrage via une connexion JDBC bloquante — le seul point de blocage dans un projet 100 % réactif.

MongoDB est **schemaless**. La collection `articles` est créée au premier `save()`. Il n'y a rien à migrer, donc Flyway n'a plus de rôle. La disparition de Flyway est une conséquence directe du passage à MongoDB, pas une décision indépendante.

### Ce qui change dans la stack

| Avant (PostgreSQL) | Après (MongoDB) |
|---|---|
| `spring-boot-starter-data-r2dbc` | `spring-boot-starter-data-mongodb-reactive` |
| `r2dbc-postgresql` (driver réactif) | — (inclus dans le starter) |
| `flyway-core` + `flyway-database-postgresql` | — |
| `postgresql` (JDBC pour Flyway) | — |
| `R2dbcConfig` (`@EnableR2dbcAuditing`) | `MongoConfig` (`@EnableMongoAuditing`) |
| `id: Long` (BIGSERIAL PostgreSQL) | `id: String` (ObjectId MongoDB, 24 hex) |
| `@Table` + `@Column` | `@Document` |
| `@Query` SQL (`ILIKE`) | `@Query` JSON (`$regex`) |
| `@Transactional` | Retiré (replica set requis) |
| Connexions : JDBC (Flyway) + R2DBC (runtime) | Connexion unique : driver Reactive MongoDB |

### Point de vigilance

Si le projet évolue vers plusieurs entités liées (articles + commentaires + utilisateurs), MongoDB demandera soit de l'**embedding** (dénormalisation à maintenir manuellement) soit des **références sans garantie d'intégrité**. PostgreSQL redeviendrait le meilleur choix à ce stade.

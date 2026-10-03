package com.reactiveblog.repository;

import com.reactiveblog.model.Article;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/*
 * ArticleRepository — accès réactif à PostgreSQL via R2DBC.
 *
 * On étend ReactiveCrudRepository<Article, Long> :
 *   - Article : type de l'entité
 *   - Long    : type de la clé primaire
 *
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  JpaRepository (bloquant)   │  ReactiveCrudRepository (réactif) │
 * ├─────────────────────────────┼───────────────────────────────────┤
 * │  List<Article> findAll()    │  Flux<Article> findAll()          │
 * │  Optional<Article> findById │  Mono<Article> findById()         │
 * │  Article save()             │  Mono<Article> save()             │
 * │  void deleteById()          │  Mono<Void> deleteById()          │
 * │  long count()               │  Mono<Long> count()               │
 * └─────────────────────────────┴───────────────────────────────────┘
 *
 * Spring Data génère l'implémentation au démarrage — on n'écrit aucune
 * requête SQL pour les opérations CRUD de base.
 *
 * Pour les requêtes personnalisées, deux options :
 *   1. Convention de nommage : findByXxx → Spring génère le SQL
 *   2. @Query : SQL explicite (R2DBC utilise des paramètres $1, $2, ...)
 */
@Repository
public interface ArticleRepository extends ReactiveCrudRepository<Article, Long> {

    /*
     * Recherche par auteur (exact).
     * Spring Data génère : SELECT * FROM articles WHERE author = $1
     *
     * Retourne Flux<Article> car il peut y avoir 0, 1 ou N articles.
     */
    Flux<Article> findByAuthor(String author);

    /*
     * Recherche par auteur (insensible à la casse).
     * Spring Data génère : SELECT * FROM articles WHERE author ILIKE $1
     */
    Flux<Article> findByAuthorIgnoreCase(String author);

    /*
     * Recherche full-text dans le titre (ILIKE pour PostgreSQL).
     *
     * On utilise @Query car la convention de nommage ne couvre pas ILIKE
     * avec wildcards. Le paramètre :keyword sera passé avec les % depuis
     * le service.
     *
     * Note R2DBC : les paramètres sont nommés (:keyword) ou positionnels ($1).
     * On préfère les paramètres nommés pour la lisibilité.
     */
    @Query("SELECT * FROM articles WHERE title ILIKE :keyword OR content ILIKE :keyword ORDER BY created_at DESC")
    Flux<Article> searchByKeyword(String keyword);

    /*
     * Tous les articles triés par date de création décroissante.
     * Spring Data génère : SELECT * FROM articles ORDER BY created_at DESC
     */
    Flux<Article> findAllByOrderByCreatedAtDesc();

    /*
     * Vérifie si un article avec ce titre existe déjà.
     * Utile pour la validation métier dans le Service.
     * Mono<Boolean> : émet true ou false, puis complete.
     */
    Mono<Boolean> existsByTitleIgnoreCase(String title);
}

package com.reactiveblog.repository;

import com.reactiveblog.model.Article;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/*
 * ArticleRepository — accès réactif à MongoDB via ReactiveMongoRepository.
 *
 * On étend ReactiveMongoRepository<Article, String> :
 *   - Article : type du document
 *   - String  : type de l'_id (ObjectId MongoDB représenté en String)
 *
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │  ReactiveCrudRepository (R2DBC) │  ReactiveMongoRepository (MongoDB) │
 * ├─────────────────────────────────┼────────────────────────────────────┤
 * │  id: Long                       │  id: String (ObjectId)             │
 * │  @Query SQL                     │  @Query JSON MongoDB               │
 * │  Paramètre $1 / :name           │  Paramètre ?0 (positionnel)        │
 * └─────────────────────────────────┴────────────────────────────────────┘
 *
 * Spring Data génère l'implémentation au démarrage — on n'écrit aucune
 * requête pour les opérations CRUD de base.
 *
 * Pour les requêtes personnalisées :
 *   1. Convention de nommage : findByXxx → Spring génère la requête MongoDB
 *   2. @Query : filtre JSON MongoDB explicite
 */
@Repository
public interface ArticleRepository extends ReactiveMongoRepository<Article, String> {

    /*
     * Recherche par auteur (exact).
     * Spring Data génère : { "author": "..." }
     */
    Flux<Article> findByAuthor(String author);

    /*
     * Recherche par auteur (insensible à la casse).
     * Spring Data génère : { "author": { "$regex": "...", "$options": "i" } }
     */
    Flux<Article> findByAuthorIgnoreCase(String author);

    /*
     * Recherche full-text dans le titre ou le contenu.
     *
     * @Query MongoDB : filtre JSON avec $regex et option "i" (case-insensitive).
     * Le paramètre ?0 est le mot-clé passé directement (sans wildcards —
     * le $regex fait le matching partiel nativement).
     *
     * sort = "{ 'createdAt': -1 }" : tri décroissant intégré à la requête.
     */
    @Query(value = "{ '$or': [{ 'title': { '$regex': ?0, '$options': 'i' }}, { 'content': { '$regex': ?0, '$options': 'i' }}] }",
           sort  = "{ 'createdAt': -1 }")
    Flux<Article> searchByKeyword(String keyword);

    /*
     * Tous les articles triés par date de création décroissante.
     * Spring Data génère : find().sort({ createdAt: -1 })
     */
    Flux<Article> findAllByOrderByCreatedAtDesc();

    /*
     * Vérifie si un article avec ce titre existe déjà.
     * Spring Data génère : { "title": { "$regex": "...", "$options": "i" } }
     */
    Mono<Boolean> existsByTitleIgnoreCase(String title);
}

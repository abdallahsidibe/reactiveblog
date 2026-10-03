package com.reactiveblog.service;

import com.reactiveblog.dto.ArticleRequestDto;
import com.reactiveblog.dto.ArticleResponseDto;
import com.reactiveblog.exception.ArticleAlreadyExistsException;
import com.reactiveblog.exception.ArticleNotFoundException;
import com.reactiveblog.mapper.ArticleMapper;
import com.reactiveblog.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/*
 * ArticleService — couche métier réactive.
 *
 * Règle fondamentale : cette classe ne doit JAMAIS appeler .block().
 * Chaque méthode retourne un Mono<T> ou un Flux<T> que le Controller
 * s'abonnera implicitement via le framework WebFlux.
 *
 * Les opérateurs Reactor utilisés ici :
 *
 *  .map()         → transforme chaque élément (synchrone, en mémoire)
 *  .flatMap()     → transforme chaque élément en Publisher (async, I/O)
 *  .switchIfEmpty() → agit si le Mono/Flux est vide (0 élément émis)
 *  .filter()      → ne laisse passer que les éléments qui matchent
 *  .then()        → ignore la valeur, retourne Mono<Void> à la complétion
 *  .doOnNext()    → effet de bord (log) sans altérer le flux
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleService {

    private final ArticleRepository repository;
    private final ArticleMapper mapper;

    // ──────────────────────────────────────────────────────────────
    // READ
    // ──────────────────────────────────────────────────────────────

    /*
     * Retourne tous les articles triés par date décroissante.
     *
     * repository.findAllByOrderByCreatedAtDesc() → Flux<Article>
     *   .map(mapper::toDto)                      → Flux<ArticleResponseDto>
     *
     * .map() est synchrone : la transformation Article→DTO ne fait
     * aucun I/O donc on n'a pas besoin de flatMap.
     */
    public Flux<ArticleResponseDto> findAll() {
        log.info("Récupération de tous les articles");
        return repository.findAllByOrderByCreatedAtDesc()
                .map(mapper::toDto);
    }

    /*
     * Retourne un article par son id.
     *
     * repository.findById(id) → Mono<Article>  (vide si non trouvé)
     *   .switchIfEmpty(...)   → transforme le Mono vide en erreur
     *   .map(mapper::toDto)   → Mono<ArticleResponseDto>
     *
     * switchIfEmpty() est l'équivalent réactif du if (optional.isEmpty()).
     * Mono.error() propage une exception dans le pipeline — le GlobalExceptionHandler
     * (étape 12) la capturera et retournera un 404.
     */
    public Mono<ArticleResponseDto> findById(Long id) {
        log.info("Récupération de l'article id={}", id);
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new ArticleNotFoundException(id)))
                .map(mapper::toDto);
    }

    /*
     * Recherche par mot-clé dans le titre ou le contenu.
     *
     * On entoure le keyword de % pour le ILIKE PostgreSQL.
     * Le .map() en aval transforme chaque Article en DTO.
     */
    public Flux<ArticleResponseDto> search(String keyword) {
        log.info("Recherche d'articles avec keyword='{}'", keyword);
        String pattern = "%" + keyword + "%";
        return repository.searchByKeyword(pattern)
                .map(mapper::toDto);
    }

    // ──────────────────────────────────────────────────────────────
    // CREATE
    // ──────────────────────────────────────────────────────────────

    /*
     * Crée un nouvel article.
     *
     * Étape 1 : vérifier qu'aucun article n'a déjà ce titre.
     *   repository.existsByTitleIgnoreCase() → Mono<Boolean>
     *
     * Étape 2 : si le titre existe → erreur 409.
     *   .filter(exists -> !exists)  → laisse passer seulement si false
     *   .switchIfEmpty(...)         → si filtré (true), on propage l'erreur
     *
     * Étape 3 : convertir le DTO en entité, sauvegarder, convertir en DTO.
     *   .flatMap(...)               → l'opération save() retourne un Mono,
     *                                 on utilise flatMap (pas map) car on
     *                                 "aplatit" un Mono<Mono<Article>> en Mono<Article>
     *
     *   Règle : map   → f(T) → U          (résultat direct)
     *           flatMap → f(T) → Mono<U>  (résultat enveloppé dans un Publisher)
     */
    @Transactional
    public Mono<ArticleResponseDto> create(ArticleRequestDto dto) {
        log.info("Création d'un article : title='{}'", dto.title());
        return repository.existsByTitleIgnoreCase(dto.title())
                .filter(exists -> !exists)
                .switchIfEmpty(Mono.error(new ArticleAlreadyExistsException(dto.title())))
                .flatMap(notExists -> repository.save(mapper.toEntity(dto)))
                .map(mapper::toDto)
                .doOnNext(saved -> log.info("Article créé avec id={}", saved.id()));
    }

    // ──────────────────────────────────────────────────────────────
    // UPDATE
    // ──────────────────────────────────────────────────────────────

    /*
     * Met à jour un article existant.
     *
     * Étape 1 : vérifier que l'article existe (404 sinon).
     * Étape 2 : construire la nouvelle entité avec les données du DTO
     *           en conservant l'id et createdAt de l'entité existante.
     * Étape 3 : sauvegarder → Spring Data R2DBC détecte que l'id est non-null
     *           et génère un UPDATE au lieu d'un INSERT.
     *
     * .flatMap(existing -> ...)  : on est dans un contexte async (on enchaîne
     * sur un Mono), donc flatMap pour retourner un Mono<Article>.
     */
    @Transactional
    public Mono<ArticleResponseDto> update(Long id, ArticleRequestDto dto) {
        log.info("Mise à jour de l'article id={}", id);
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new ArticleNotFoundException(id)))
                .flatMap(existing -> repository.save(mapper.toEntity(dto, existing)))
                .map(mapper::toDto)
                .doOnNext(updated -> log.info("Article id={} mis à jour", updated.id()));
    }

    // ──────────────────────────────────────────────────────────────
    // DELETE
    // ──────────────────────────────────────────────────────────────

    /*
     * Supprime un article par son id.
     *
     * On vérifie d'abord l'existence (404 sinon) puis on supprime.
     *
     * .then() : ignore l'Article émis par findById, retourne Mono<Void>
     *           qui signale la fin de la suppression sans émettre de valeur.
     *
     * Pourquoi ne pas appeler directement deleteById() ?
     * Parce que deleteById() ne retourne pas d'erreur si l'id n'existe pas —
     * il complète silencieusement. En vérifiant d'abord, on retourne un 404 propre.
     */
    @Transactional
    public Mono<Void> delete(Long id) {
        log.info("Suppression de l'article id={}", id);
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new ArticleNotFoundException(id)))
                .flatMap(article -> repository.deleteById(article.getId()))
                .doOnSuccess(v -> log.info("Article id={} supprimé", id));
    }
}

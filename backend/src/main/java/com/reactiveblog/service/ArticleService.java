package com.reactiveblog.service;

import com.reactiveblog.dto.ArticleRequestDto;
import com.reactiveblog.dto.ArticleResponseDto;
import com.reactiveblog.exception.ArticleAlreadyExistsException;
import com.reactiveblog.exception.ArticleNotFoundException;
import com.reactiveblog.mapper.ArticleMapper;
import com.reactiveblog.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/*
 * ArticleService — couche métier réactive avec cache Redis.
 *
 * Règle fondamentale : cette classe ne doit JAMAIS appeler .block().
 * Chaque méthode retourne un Mono<T> ou un Flux<T> que le Controller
 * s'abonnera implicitement via le framework WebFlux.
 *
 * Stratégie de cache :
 *
 *   findAll()      → clé "articles:all" (Redis List)
 *                    HIT  : redis.opsForList().range(0, -1) → Flux<ArticleResponseDto>
 *                    MISS : BDD → collectList → rightPushAll + expire → Flux
 *
 *   findById(id)   → clé "article:{id}" (Redis String / valeur scalaire)
 *                    HIT  : redis.opsForValue().get(key) → Mono<ArticleResponseDto>
 *                    MISS : BDD → opsForValue().set(key, dto, TTL)
 *
 *   create / update / delete → invalidation :
 *                    delete("articles:all") + delete("article:{id}")
 *
 * Les opérateurs Reactor utilisés ici :
 *
 *  .map()           → transforme chaque élément (synchrone, en mémoire)
 *  .flatMap()       → transforme chaque élément en Publisher (async, I/O)
 *  .flatMapMany()   → Mono → Flux (un Publisher qui en émet plusieurs)
 *  .switchIfEmpty() → agit si le Mono/Flux est vide (0 élément émis)
 *  .filter()        → ne laisse passer que les éléments qui matchent
 *  .then()          → ignore la valeur, retourne Mono<Void> à la complétion
 *  .thenReturn()    → then() + émet une valeur fixe
 *  .thenMany()      → then() + souscrit à un Flux
 *  .doOnNext()      → effet de bord (log) sans altérer le flux
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleService {

    private final ArticleRepository repository;
    private final ArticleMapper mapper;
    private final ReactiveRedisTemplate<String, ArticleResponseDto> redis;

    private static final String CACHE_ALL_KEY = "articles:all";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private static String articleKey(String id) {
        return "article:" + id;
    }

    // ──────────────────────────────────────────────────────────────
    // READ
    // ──────────────────────────────────────────────────────────────

    /*
     * Retourne tous les articles triés par date décroissante.
     *
     * On vérifie d'abord si la clé "articles:all" existe en Redis.
     * redis.hasKey()  → Mono<Boolean>
     *   .flatMapMany() → si true  : lire la liste Redis
     *                  → si false : appeler fetchFromDbAndCacheAll()
     *
     * hasKey + range évite l'ambiguïté d'un Flux vide (liste vide ≠ cache absent).
     */
    public Flux<ArticleResponseDto> findAll() {
        log.info("Récupération de tous les articles");
        return redis.hasKey(CACHE_ALL_KEY)
                .flatMapMany(cached -> {
                    if (cached) {
                        log.debug("Cache HIT : {}", CACHE_ALL_KEY);
                        return redis.opsForList().range(CACHE_ALL_KEY, 0, -1);
                    }
                    log.debug("Cache MISS : {}", CACHE_ALL_KEY);
                    return fetchFromDbAndCacheAll();
                });
    }

    /*
     * Charge les articles depuis la BDD, pousse la liste dans Redis
     * puis émet les articles un par un.
     *
     * .collectList()         → Flux<Article> → Mono<List<Article>>
     * .flatMapMany(list ->   → Mono<List> → Flux (aplatissage)
     *   rightPushAll(...)    → RPUSH Redis  → Mono<Long> (taille de la liste)
     *   .then(expire(...))   → TTL sur la clé → Mono<Boolean>
     *   .thenMany(Flux.fromIterable(list)) → émettre chaque DTO
     * )
     *
     * Si la liste est vide (aucun article), on ne pousse rien dans Redis
     * (évite de stocker une liste vide avec TTL qui masquerait les futurs inserts).
     */
    private Flux<ArticleResponseDto> fetchFromDbAndCacheAll() {
        return repository.findAllByOrderByCreatedAtDesc()
                .map(mapper::toDto)
                .collectList()
                .flatMapMany(list -> {
                    if (list.isEmpty()) {
                        return Flux.fromIterable(list);
                    }
                    return redis.opsForList().rightPushAll(CACHE_ALL_KEY, list)
                            .then(redis.expire(CACHE_ALL_KEY, CACHE_TTL))
                            .thenMany(Flux.fromIterable(list));
                });
    }

    /*
     * Retourne un article par son id.
     *
     * redis.opsForValue().get(key) → Mono<ArticleResponseDto>
     *   .switchIfEmpty(...)         → cache MISS : BDD puis mise en cache
     *
     * En cas de MISS on appelle set(key, dto, TTL) qui retourne Mono<Boolean>.
     * On enchaîne .thenReturn(dto) pour ré-émettre le DTO dans le pipeline.
     */
    public Mono<ArticleResponseDto> findById(String id) {
        log.info("Récupération de l'article id={}", id);
        String key = articleKey(id);
        return redis.opsForValue().get(key)
                .doOnNext(dto -> log.debug("Cache HIT : {}", key))
                .switchIfEmpty(
                        repository.findById(id)
                                .switchIfEmpty(Mono.error(new ArticleNotFoundException(id)))
                                .map(mapper::toDto)
                                .flatMap(dto -> redis.opsForValue()
                                        .set(key, dto, CACHE_TTL)
                                        .doOnSuccess(ok -> log.debug("Cache SET : {}", key))
                                        .thenReturn(dto))
                );
    }

    /*
     * Recherche par mot-clé dans le titre ou le contenu.
     * Pas mis en cache : les résultats sont trop dynamiques (dépendent du keyword).
     */
    public Flux<ArticleResponseDto> search(String keyword) {
        log.info("Recherche d'articles avec keyword='{}'", keyword);
        return repository.searchByKeyword(keyword)
                .map(mapper::toDto);
    }

    // ──────────────────────────────────────────────────────────────
    // CREATE
    // ──────────────────────────────────────────────────────────────

    /*
     * Crée un nouvel article, puis invalide le cache "articles:all".
     *
     * redis.delete(CACHE_ALL_KEY) → Mono<Long> (nombre de clés supprimées)
     * .thenReturn(saved)          → ré-émet le DTO sauvegardé
     *
     * On n'invalide pas "article:{id}" car l'article vient d'être créé
     * et n'est pas encore dans le cache.
     */
    public Mono<ArticleResponseDto> create(ArticleRequestDto dto) {
        log.info("Création d'un article : title='{}'", dto.title());
        return repository.existsByTitleIgnoreCase(dto.title())
                .filter(exists -> !exists)
                .switchIfEmpty(Mono.error(new ArticleAlreadyExistsException(dto.title())))
                .flatMap(notExists -> repository.save(mapper.toEntity(dto)))
                .map(mapper::toDto)
                .flatMap(saved -> redis.delete(CACHE_ALL_KEY).thenReturn(saved))
                .doOnNext(saved -> log.info("Article créé avec id={}", saved.id()));
    }

    // ──────────────────────────────────────────────────────────────
    // UPDATE
    // ──────────────────────────────────────────────────────────────

    /*
     * Met à jour un article existant, puis invalide le cache.
     *
     * On invalide deux clés :
     *   - "articles:all"   : la liste complète est périmée
     *   - "article:{id}"   : la version en cache est périmée
     *
     * redis.delete(Publisher<String>) accepte un Flux de clés.
     */
    public Mono<ArticleResponseDto> update(String id, ArticleRequestDto dto) {
        log.info("Mise à jour de l'article id={}", id);
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new ArticleNotFoundException(id)))
                .flatMap(existing -> repository.save(mapper.toEntity(dto, existing)))
                .map(mapper::toDto)
                .flatMap(updated ->
                        redis.delete(CACHE_ALL_KEY, articleKey(id)).thenReturn(updated))
                .doOnNext(updated -> log.info("Article id={} mis à jour", updated.id()));
    }

    // ──────────────────────────────────────────────────────────────
    // DELETE
    // ──────────────────────────────────────────────────────────────

    /*
     * Supprime un article par son id, puis invalide le cache.
     *
     * .then(redis.delete(...).then()) :
     *   Le premier .then() enchaîne après la suppression BDD (ignore Void).
     *   redis.delete() retourne Mono<Long>.
     *   Le second .then() le convertit en Mono<Void> pour respecter la signature.
     */
    public Mono<Void> delete(String id) {
        log.info("Suppression de l'article id={}", id);
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new ArticleNotFoundException(id)))
                .flatMap(article -> repository.deleteById(article.getId()))
                .then(redis.delete(CACHE_ALL_KEY, articleKey(id)).then())
                .doOnSuccess(v -> log.info("Article id={} supprimé", id));
    }
}

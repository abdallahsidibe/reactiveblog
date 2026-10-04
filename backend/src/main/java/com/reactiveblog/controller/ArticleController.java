package com.reactiveblog.controller;

import com.reactiveblog.dto.ArticleRequestDto;
import com.reactiveblog.dto.ArticleResponseDto;
import com.reactiveblog.service.ArticleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/*
 * ArticleController — couche HTTP réactive.
 *
 * Spring WebFlux comprend nativement Mono<T> et Flux<T> comme valeurs
 * de retour : il s'abonne automatiquement et streame la réponse HTTP
 * une fois le Publisher complété. On ne gère jamais la sérialisation
 * ni l'abonnement manuellement.
 *
 * Différence Spring MVC vs Spring WebFlux :
 *
 *  Spring MVC                       │  Spring WebFlux
 * ──────────────────────────────────┼──────────────────────────────────
 *  ResponseEntity<Article>          │  Mono<ResponseEntity<ArticleDto>>
 *  List<Article>                    │  Flux<ArticleResponseDto>
 *  Thread bloqué pendant l'I/O      │  Thread libéré pendant l'I/O
 *  Tomcat (thread-per-request)      │  Netty (event loop)
 *
 * @CrossOrigin : autorise Angular (port 4200) à appeler cette API.
 * En production, on restreindrait aux origines connues.
 */
@Slf4j
@RestController
@RequestMapping("/api/articles")
@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService service;

    // ──────────────────────────────────────────────────────────────
    // GET /api/articles
    // GET /api/articles?search=keyword
    // ──────────────────────────────────────────────────────────────

    /*
     * Si le paramètre "search" est présent → recherche par mot-clé.
     * Sinon → retourne tous les articles.
     *
     * Flux<ArticleResponseDto> : WebFlux sérialise chaque élément en JSON
     * et les envoie dans un tableau JSON une fois le flux terminé.
     * (pour un streaming SSE, on utiliserait MediaType.TEXT_EVENT_STREAM_VALUE)
     */
    @GetMapping
    public Flux<ArticleResponseDto> findAll(
            @RequestParam(required = false) String search) {

        if (search != null && !search.isBlank()) {
            log.info("GET /api/articles?search={}", search);
            return service.search(search);
        }
        log.info("GET /api/articles");
        return service.findAll();
    }

    // ──────────────────────────────────────────────────────────────
    // GET /api/articles/{id}
    // ──────────────────────────────────────────────────────────────

    /*
     * Mono<ResponseEntity<ArticleResponseDto>> :
     *
     * On enveloppe dans ResponseEntity pour contrôler le statut HTTP.
     * WebFlux s'abonne au Mono, attend l'émission, puis envoie la réponse.
     *
     * Si l'article n'existe pas, le service émet ArticleNotFoundException
     * → le GlobalExceptionHandler (étape 12) retourne HTTP 404.
     */
    @GetMapping("/{id}")
    public Mono<ResponseEntity<ArticleResponseDto>> findById(@PathVariable String id) {
        log.info("GET /api/articles/{}", id);
        return service.findById(id)
                .map(ResponseEntity::ok);
    }

    // ──────────────────────────────────────────────────────────────
    // POST /api/articles
    // ──────────────────────────────────────────────────────────────

    /*
     * @Valid : déclenche Bean Validation sur ArticleRequestDto avant
     * d'entrer dans le service. Si la validation échoue, WebFlux retourne
     * automatiquement HTTP 400 avec les messages d'erreur.
     *
     * @RequestBody Mono<ArticleRequestDto> : WebFlux désérialise le body
     * de la requête HTTP de façon non-bloquante.
     *
     * .flatMap(service::create) : on utilise flatMap car service.create()
     * retourne un Mono<ArticleResponseDto> (pas une valeur directe).
     *
     * HTTP 201 Created avec le DTO de l'article créé en body.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ResponseEntity<ArticleResponseDto>> create(
            @Valid @RequestBody Mono<ArticleRequestDto> requestMono) {

        log.info("POST /api/articles");
        return requestMono
                .flatMap(service::create)
                .map(dto -> ResponseEntity.status(HttpStatus.CREATED).body(dto));
    }

    // ──────────────────────────────────────────────────────────────
    // PUT /api/articles/{id}
    // ──────────────────────────────────────────────────────────────

    @PutMapping("/{id}")
    public Mono<ResponseEntity<ArticleResponseDto>> update(
            @PathVariable String id,
            @Valid @RequestBody Mono<ArticleRequestDto> requestMono) {

        log.info("PUT /api/articles/{}", id);
        return requestMono
                .flatMap(dto -> service.update(id, dto))
                .map(ResponseEntity::ok);
    }

    // ──────────────────────────────────────────────────────────────
    // DELETE /api/articles/{id}
    // ──────────────────────────────────────────────────────────────

    /*
     * service.delete() retourne Mono<Void>.
     * .then(...) : ignore le signal de complétion et retourne à la place
     * un Mono<ResponseEntity<Void>> avec statut 204 No Content.
     *
     * HTTP 204 : succès sans body (standard REST pour un DELETE).
     */
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable String id) {
        log.info("DELETE /api/articles/{}", id);
        return service.delete(id)
                .then(Mono.just(ResponseEntity.<Void>noContent().build()));
    }
}

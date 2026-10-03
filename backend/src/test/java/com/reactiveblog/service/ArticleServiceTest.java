package com.reactiveblog.service;

import com.reactiveblog.dto.ArticleRequestDto;
import com.reactiveblog.dto.ArticleResponseDto;
import com.reactiveblog.exception.ArticleAlreadyExistsException;
import com.reactiveblog.exception.ArticleNotFoundException;
import com.reactiveblog.mapper.ArticleMapper;
import com.reactiveblog.model.Article;
import com.reactiveblog.repository.ArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveListOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/*
 * Tests unitaires du service avec StepVerifier.
 *
 * StepVerifier est l'outil de test de Project Reactor.
 * Il permet de vérifier le comportement d'un Mono ou Flux
 * de façon déclarative, étape par étape :
 *
 *   StepVerifier.create(publisher)  → crée le vérificateur
 *     .expectNext(valeur)           → attend un élément précis
 *     .expectNextMatches(predicate) → attend un élément qui satisfait le prédicat
 *     .expectError(ExceptionClass)  → attend une erreur de ce type
 *     .expectComplete()             → attend la complétion sans erreur
 *     .verify()                     → DÉCLENCHE la souscription et lance le test
 *
 * Sans .verify() → rien ne se passe. Le Publisher n'est jamais souscrit.
 * C'est l'erreur classique des débutants en tests Reactor.
 *
 * Stratégie Redis dans les tests :
 *
 *   Tous les stubs Redis sont configurés en cache MISS (hasKey → false,
 *   opsForValue().get() → Mono.empty()) dans le @BeforeEach commun.
 *   Ainsi les tests existants continuent à vérifier la logique BDD sans
 *   avoir à connaître les détails du cache.
 *
 *   Les opérations d'écriture Redis (set, rightPushAll, expire, delete)
 *   sont stubbées en mode lenient() pour ne pas faire échouer les tests
 *   qui ne les vérifient pas explicitement.
 */
@ExtendWith(MockitoExtension.class)
class ArticleServiceTest {

    @Mock
    private ArticleRepository repository;

    @Mock
    private ArticleMapper mapper;

    /*
     * Mocks Redis — @InjectMocks les injecte dans ArticleService via
     * le constructeur @RequiredArgsConstructor (Lombok).
     *
     * valueOps / listOps sont des mocks des opérations Redis :
     * ReactiveRedisTemplate délègue à ces objets pour chaque type de structure.
     */
    @Mock
    private ReactiveRedisTemplate<String, ArticleResponseDto> redis;

    @Mock
    private ReactiveValueOperations<String, ArticleResponseDto> valueOps;

    @Mock
    private ReactiveListOperations<String, ArticleResponseDto> listOps;

    @InjectMocks
    private ArticleService service;

    // ── Fixtures ──────────────────────────────────────────────────

    private Article article;
    private ArticleResponseDto responseDto;
    private ArticleRequestDto requestDto;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();

        article = Article.builder()
                .id(1L)
                .title("Spring WebFlux")
                .content("Contenu de l'article")
                .author("Alice")
                .createdAt(now)
                .updatedAt(now)
                .build();

        responseDto = new ArticleResponseDto(
                1L, "Spring WebFlux", "Contenu de l'article",
                "Alice", now, now
        );

        requestDto = new ArticleRequestDto(
                "Spring WebFlux", "Contenu de l'article", "Alice"
        );

        /*
         * Stubs Redis communs — cache MISS par défaut.
         *
         * lenient() supprime l'avertissement "unnecessary stubbing" pour les
         * stubs qui ne sont pas consommés par tous les tests du @Nested.
         *
         * Cache toujours MISS → les tests passent par la BDD comme avant.
         */
        lenient().when(redis.hasKey(anyString())).thenReturn(Mono.just(false));
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        lenient().when(redis.opsForList()).thenReturn(listOps);
        lenient().when(valueOps.get(anyString())).thenReturn(Mono.empty());
        lenient().when(valueOps.set(anyString(), any(), any(Duration.class)))
                .thenReturn(Mono.just(true));
        lenient().when(listOps.rightPushAll(anyString(), anyList()))
                .thenReturn(Mono.just(0L));
        lenient().when(redis.expire(anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true));
        // delete(K... keys) — varargs : on couvre les appels à 1 et 2 clés
        lenient().when(redis.delete(anyString())).thenReturn(Mono.just(1L));
        lenient().when(redis.delete(anyString(), anyString())).thenReturn(Mono.just(2L));
    }

    // ── findAll ───────────────────────────────────────────────────

    @Nested
    @DisplayName("findAll()")
    class FindAll {

        @Test
        @DisplayName("retourne un Flux avec tous les articles")
        void shouldReturnAllArticles() {
            when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(Flux.just(article));
            when(mapper.toDto(article)).thenReturn(responseDto);

            StepVerifier.create(service.findAll())
                    .expectNext(responseDto)   // attend exactement cet élément
                    .expectComplete()           // puis la complétion
                    .verify();                  // DÉCLENCHE la souscription

            verify(repository).findAllByOrderByCreatedAtDesc();
        }

        @Test
        @DisplayName("retourne un Flux vide si aucun article")
        void shouldReturnEmptyFlux() {
            when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(Flux.empty());

            StepVerifier.create(service.findAll())
                    .expectNextCount(0)  // aucun élément
                    .expectComplete()
                    .verify();
        }
    }

    // ── findById ──────────────────────────────────────────────────

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("retourne l'article si trouvé")
        void shouldReturnArticleWhenFound() {
            when(repository.findById(1L)).thenReturn(Mono.just(article));
            when(mapper.toDto(article)).thenReturn(responseDto);

            StepVerifier.create(service.findById(1L))
                    .expectNext(responseDto)
                    .expectComplete()
                    .verify();
        }

        @Test
        @DisplayName("retourne ArticleNotFoundException si introuvable")
        void shouldReturnErrorWhenNotFound() {
            when(repository.findById(99L)).thenReturn(Mono.empty());

            /*
             * .expectError(ArticleNotFoundException.class) :
             * on attend que le Mono se termine en erreur avec
             * exactement ce type d'exception.
             * Pas de .expectComplete() — l'erreur remplace la complétion.
             */
            StepVerifier.create(service.findById(99L))
                    .expectError(ArticleNotFoundException.class)
                    .verify();
        }
    }

    // ── create ────────────────────────────────────────────────────

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("crée et retourne l'article")
        void shouldCreateArticle() {
            when(repository.existsByTitleIgnoreCase(requestDto.title()))
                    .thenReturn(Mono.just(false));
            when(mapper.toEntity(requestDto)).thenReturn(article);
            when(repository.save(article)).thenReturn(Mono.just(article));
            when(mapper.toDto(article)).thenReturn(responseDto);

            StepVerifier.create(service.create(requestDto))
                    .expectNext(responseDto)
                    .expectComplete()
                    .verify();

            verify(repository).save(article);
        }

        @Test
        @DisplayName("retourne ArticleAlreadyExistsException si titre dupliqué")
        void shouldRejectDuplicateTitle() {
            when(repository.existsByTitleIgnoreCase(requestDto.title()))
                    .thenReturn(Mono.just(true));

            StepVerifier.create(service.create(requestDto))
                    .expectError(ArticleAlreadyExistsException.class)
                    .verify();

            verify(repository, never()).save(any());
        }
    }

    // ── update ────────────────────────────────────────────────────

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("met à jour et retourne l'article modifié")
        void shouldUpdateArticle() {
            ArticleRequestDto updateDto = new ArticleRequestDto(
                    "Titre modifié", "Nouveau contenu", "Alice"
            );
            Article updated = Article.builder()
                    .id(1L).title("Titre modifié").content("Nouveau contenu")
                    .author("Alice").createdAt(article.getCreatedAt())
                    .build();
            ArticleResponseDto updatedDto = new ArticleResponseDto(
                    1L, "Titre modifié", "Nouveau contenu",
                    "Alice", article.getCreatedAt(), Instant.now()
            );

            when(repository.findById(1L)).thenReturn(Mono.just(article));
            when(mapper.toEntity(updateDto, article)).thenReturn(updated);
            when(repository.save(updated)).thenReturn(Mono.just(updated));
            when(mapper.toDto(updated)).thenReturn(updatedDto);

            StepVerifier.create(service.update(1L, updateDto))
                    .expectNextMatches(dto -> dto.title().equals("Titre modifié"))
                    .expectComplete()
                    .verify();
        }

        @Test
        @DisplayName("retourne ArticleNotFoundException si introuvable")
        void shouldReturnErrorWhenNotFound() {
            when(repository.findById(99L)).thenReturn(Mono.empty());

            StepVerifier.create(service.update(99L, requestDto))
                    .expectError(ArticleNotFoundException.class)
                    .verify();
        }
    }

    // ── delete ────────────────────────────────────────────────────

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("supprime l'article et retourne Mono<Void>")
        void shouldDeleteArticle() {
            when(repository.findById(1L)).thenReturn(Mono.just(article));
            when(repository.deleteById(1L)).thenReturn(Mono.empty());

            /*
             * Mono<Void> complète sans émettre de valeur.
             * On n'utilise pas expectNext() — il n'y a rien à attendre.
             */
            StepVerifier.create(service.delete(1L))
                    .expectComplete()
                    .verify();

            verify(repository).deleteById(1L);
        }

        @Test
        @DisplayName("retourne ArticleNotFoundException si introuvable")
        void shouldReturnErrorWhenNotFound() {
            when(repository.findById(99L)).thenReturn(Mono.empty());

            StepVerifier.create(service.delete(99L))
                    .expectError(ArticleNotFoundException.class)
                    .verify();

            verify(repository, never()).deleteById(anyLong());
        }
    }

    // ── search ────────────────────────────────────────────────────

    @Nested
    @DisplayName("search()")
    class Search {

        @Test
        @DisplayName("retourne les articles contenant le mot-clé")
        void shouldReturnMatchingArticles() {
            when(repository.searchByKeyword("%WebFlux%")).thenReturn(Flux.just(article));
            when(mapper.toDto(article)).thenReturn(responseDto);

            StepVerifier.create(service.search("WebFlux"))
                    .expectNext(responseDto)
                    .expectComplete()
                    .verify();
        }

        @Test
        @DisplayName("retourne Flux vide si aucun résultat")
        void shouldReturnEmptyWhenNoMatch() {
            when(repository.searchByKeyword("%xyz123%")).thenReturn(Flux.empty());

            StepVerifier.create(service.search("xyz123"))
                    .expectComplete()
                    .verify();
        }
    }
}

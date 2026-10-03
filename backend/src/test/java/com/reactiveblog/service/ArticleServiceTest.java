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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

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
 */
@ExtendWith(MockitoExtension.class)
class ArticleServiceTest {

    @Mock
    private ArticleRepository repository;

    @Mock
    private ArticleMapper mapper;

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

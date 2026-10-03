package com.reactiveblog.controller;

import com.reactiveblog.dto.ArticleRequestDto;
import com.reactiveblog.dto.ArticleResponseDto;
import com.reactiveblog.exception.ArticleNotFoundException;
import com.reactiveblog.mapper.ArticleMapper;
import com.reactiveblog.service.ArticleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = {ArticleController.class})
class ArticleControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private ArticleService service;

    @MockBean
    private ArticleMapper mapper;

    private ArticleResponseDto article1;
    private ArticleResponseDto article2;
    private ArticleRequestDto validRequest;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();
        article1 = new ArticleResponseDto(1L, "Spring WebFlux", "Contenu 1", "Alice", now, now);
        article2 = new ArticleResponseDto(2L, "R2DBC Guide",   "Contenu 2", "Bob",   now, now);
        validRequest = new ArticleRequestDto("Nouveau titre", "Nouveau contenu", "Charlie");
    }

    @Nested
    @DisplayName("GET /api/articles")
    class GetAll {

        @Test
        @DisplayName("retourne 200 avec la liste des articles")
        void shouldReturn200WithArticles() {
            when(service.findAll()).thenReturn(Flux.just(article1, article2));

            webTestClient.get().uri("/api/articles")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(ArticleResponseDto.class)
                    .hasSize(2)
                    .contains(article1, article2);
        }

        @Test
        @DisplayName("retourne 200 avec liste vide")
        void shouldReturn200WithEmptyList() {
            when(service.findAll()).thenReturn(Flux.empty());

            webTestClient.get().uri("/api/articles")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(ArticleResponseDto.class)
                    .hasSize(0);
        }

        @Test
        @DisplayName("délègue au service de recherche si ?search= est présent")
        void shouldDelegateToSearchWhenQueryPresent() {
            when(service.search("WebFlux")).thenReturn(Flux.just(article1));

            webTestClient.get().uri("/api/articles?search=WebFlux")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(ArticleResponseDto.class)
                    .hasSize(1);
        }
    }

    @Nested
    @DisplayName("GET /api/articles/{id}")
    class GetById {

        @Test
        @DisplayName("retourne 200 avec l'article si trouvé")
        void shouldReturn200WhenFound() {
            when(service.findById(1L)).thenReturn(Mono.just(article1));

            webTestClient.get().uri("/api/articles/1")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(ArticleResponseDto.class)
                    .isEqualTo(article1);
        }

        @Test
        @DisplayName("retourne 404 si non trouvé")
        void shouldReturn404WhenNotFound() {
            when(service.findById(99L))
                    .thenReturn(Mono.error(new ArticleNotFoundException(99L)));

            webTestClient.get().uri("/api/articles/99")
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }

    @Nested
    @DisplayName("POST /api/articles")
    class Create {

        @Test
        @DisplayName("retourne 201 avec l'article créé")
        void shouldReturn201WhenCreated() {
            when(service.create(any())).thenReturn(Mono.just(article1));

            webTestClient.post().uri("/api/articles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(validRequest)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody(ArticleResponseDto.class)
                    .isEqualTo(article1);
        }

        @Test
        @DisplayName("retourne 400 si le body est invalide")
        void shouldReturn400WhenInvalidBody() {
            ArticleRequestDto invalid = new ArticleRequestDto("", "contenu", "auteur");

            webTestClient.post().uri("/api/articles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(invalid)
                    .exchange()
                    .expectStatus().isBadRequest();
        }
    }

    @Nested
    @DisplayName("PUT /api/articles/{id}")
    class Update {

        @Test
        @DisplayName("retourne 200 avec l'article mis à jour")
        void shouldReturn200WhenUpdated() {
            when(service.update(eq(1L), any())).thenReturn(Mono.just(article1));

            webTestClient.put().uri("/api/articles/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(validRequest)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(ArticleResponseDto.class)
                    .isEqualTo(article1);
        }

        @Test
        @DisplayName("retourne 404 si non trouvé")
        void shouldReturn404WhenNotFound() {
            when(service.update(eq(99L), any()))
                    .thenReturn(Mono.error(new ArticleNotFoundException(99L)));

            webTestClient.put().uri("/api/articles/99")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(validRequest)
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }

    @Nested
    @DisplayName("DELETE /api/articles/{id}")
    class Delete {

        @Test
        @DisplayName("retourne 204 No Content si supprimé")
        void shouldReturn204WhenDeleted() {
            when(service.delete(1L)).thenReturn(Mono.empty());

            webTestClient.delete().uri("/api/articles/1")
                    .exchange()
                    .expectStatus().isNoContent();
        }

        @Test
        @DisplayName("retourne 404 si non trouvé")
        void shouldReturn404WhenNotFound() {
            when(service.delete(99L))
                    .thenReturn(Mono.error(new ArticleNotFoundException(99L)));

            webTestClient.delete().uri("/api/articles/99")
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }
}

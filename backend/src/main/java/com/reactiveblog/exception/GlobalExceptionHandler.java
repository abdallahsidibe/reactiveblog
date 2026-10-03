package com.reactiveblog.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.stream.Collectors;

/*
 * Gestionnaire global des exceptions pour Spring WebFlux.
 *
 * @RestControllerAdvice intercepte toutes les exceptions non gérées
 * dans les controllers et les transforme en réponses HTTP structurées.
 *
 * Chaque handler retourne Mono<ResponseEntity<...>> : WebFlux attend
 * l'émission pour envoyer la réponse — même la gestion d'erreur est réactive.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 404 — Article non trouvé
    @ExceptionHandler(ArticleNotFoundException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleNotFound(
            ArticleNotFoundException ex) {

        log.warn("404 - {}", ex.getMessage());
        return Mono.just(ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(errorBody(HttpStatus.NOT_FOUND, ex.getMessage())));
    }

    // 409 — Article déjà existant
    @ExceptionHandler(ArticleAlreadyExistsException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleConflict(
            ArticleAlreadyExistsException ex) {

        log.warn("409 - {}", ex.getMessage());
        return Mono.just(ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(errorBody(HttpStatus.CONFLICT, ex.getMessage())));
    }

    // 400 — Erreurs de validation Bean Validation (@Valid)
    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleValidation(
            WebExchangeBindException ex) {

        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " : " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.warn("400 - Validation : {}", details);
        return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorBody(HttpStatus.BAD_REQUEST, details)));
    }

    // 500 — Toute autre exception non prévue
    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleGeneric(Exception ex) {
        log.error("500 - Erreur inattendue", ex);
        return Mono.just(ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne du serveur")));
    }

    private Map<String, Object> errorBody(HttpStatus status, String message) {
        return Map.of(
                "timestamp", OffsetDateTime.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message
        );
    }
}

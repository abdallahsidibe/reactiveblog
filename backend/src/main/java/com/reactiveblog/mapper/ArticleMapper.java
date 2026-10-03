package com.reactiveblog.mapper;

import com.reactiveblog.dto.ArticleRequestDto;
import com.reactiveblog.dto.ArticleResponseDto;
import com.reactiveblog.model.Article;
import org.springframework.stereotype.Component;

/*
 * Mapper manuel Article ↔ DTO.
 *
 * On aurait pu utiliser MapStruct, mais un mapper manuel ici est intentionnel :
 * il rend la conversion explicite et facile à comprendre.
 *
 * Ce composant est un @Component Spring classique (pas de réactif ici) :
 * la conversion d'objet est une opération purement en mémoire, synchrone,
 * instantanée — aucune raison de la rendre réactive.
 *
 * La réactivité intervient au niveau de l'I/O (BDD, HTTP),
 * pas des transformations d'objets en mémoire.
 */
@Component
public class ArticleMapper {

    /*
     * DTO entrant → entité.
     * Utilisé lors d'un POST (création) ou PUT (mise à jour).
     * L'id est null à la création : PostgreSQL (BIGSERIAL) le génère.
     */
    public Article toEntity(ArticleRequestDto dto) {
        return Article.builder()
                .title(dto.title())
                .content(dto.content())
                .author(dto.author())
                .build();
    }

    /*
     * Applique les modifications d'un DTO sur une entité existante.
     * Utilisé lors d'un PUT pour conserver l'id et createdAt originaux.
     */
    public Article toEntity(ArticleRequestDto dto, Article existing) {
        return Article.builder()
                .id(existing.getId())
                .title(dto.title())
                .content(dto.content())
                .author(dto.author())
                .createdAt(existing.getCreatedAt())
                // updatedAt sera mis à jour automatiquement par @LastModifiedDate
                .build();
    }

    /*
     * Entité → DTO sortant.
     * Utilisé dans toutes les réponses HTTP.
     */
    public ArticleResponseDto toDto(Article article) {
        return new ArticleResponseDto(
                article.getId(),
                article.getTitle(),
                article.getContent(),
                article.getAuthor(),
                article.getCreatedAt(),
                article.getUpdatedAt()
        );
    }
}

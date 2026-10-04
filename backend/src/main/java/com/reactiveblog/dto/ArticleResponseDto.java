package com.reactiveblog.dto;

import java.time.Instant;

/*
 * DTO sortant : payload de la réponse HTTP (GET / POST / PUT).
 *
 * On utilise Instant pour les dates : type supporté nativement par
 * Spring Data Auditing, MongoDB et Jackson (sérialisé en ISO-8601).
 *
 * id est un String (ObjectId MongoDB : 24 chars hexadécimaux).
 */
public record ArticleResponseDto(
        String id,
        String title,
        String content,
        String author,
        Instant createdAt,
        Instant updatedAt
) {}

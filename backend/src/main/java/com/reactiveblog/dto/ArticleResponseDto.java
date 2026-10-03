package com.reactiveblog.dto;

import java.time.Instant;

/*
 * DTO sortant : payload de la réponse HTTP (GET / POST / PUT).
 *
 * On utilise Instant pour les dates : type supporté nativement par
 * Spring Data Auditing, R2DBC PostgreSQL et Jackson (sérialisé en ISO-8601).
 */
public record ArticleResponseDto(
        Long id,
        String title,
        String content,
        String author,
        Instant createdAt,
        Instant updatedAt
) {}

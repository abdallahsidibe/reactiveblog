package com.reactiveblog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/*
 * DTO entrant : payload de la requête HTTP (POST / PUT).
 *
 * On utilise un record Java 21 : immutable, concis, pas de Lombok nécessaire.
 *
 * Les annotations Bean Validation (@NotBlank, @Size) sont validées
 * dans le Controller avant que la logique métier soit exécutée.
 *
 * On n'expose PAS id, createdAt, updatedAt : le client ne doit pas
 * pouvoir les définir lui-même.
 */
public record ArticleRequestDto(

        @NotBlank(message = "Le titre est obligatoire")
        @Size(min = 3, max = 255, message = "Le titre doit contenir entre 3 et 255 caractères")
        String title,

        @NotBlank(message = "Le contenu est obligatoire")
        String content,

        @NotBlank(message = "L'auteur est obligatoire")
        @Size(max = 100, message = "Le nom de l'auteur ne peut pas dépasser 100 caractères")
        String author
) {}

package com.reactiveblog.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/*
 * Entité R2DBC.
 *
 * Différences clés avec une entité JPA :
 *
 *  JPA (@Entity)          │  R2DBC (@Table)
 * ────────────────────────┼────────────────────────────────────────
 *  @Entity                │  @Table
 *  @GeneratedValue        │  @Id suffit (BIGSERIAL côté SQL)
 *  Lazy loading           │  Pas de lazy loading : tout est explicite
 *  Session / cache L1     │  Pas de session, pas de cache
 *  @Column (javax)        │  @Column (spring.data.relational)
 *  EntityManager          │  ConnectionFactory (réactif)
 *
 * R2DBC ne gère pas les relations (@OneToMany, etc.).
 * Pour les relations, on charge manuellement via des jointures réactives.
 */
@Table("articles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Article {

    /*
     * @Id : Spring Data R2DBC identifie la clé primaire.
     * Comme la colonne est BIGSERIAL (auto-incrément PostgreSQL),
     * on laisse l'id null à la création — PostgreSQL le génère.
     */
    @Id
    private Long id;

    private String title;

    private String content;

    private String author;

    /*
     * @CreatedDate : rempli automatiquement par @EnableR2dbcAuditing
     * lors du premier INSERT. On ne le définit jamais manuellement.
     */
    @CreatedDate
    @Column("created_at")
    private Instant createdAt;

    /*
     * @LastModifiedDate : mis à jour automatiquement par Spring Data
     * à chaque save(). Pas besoin de trigger SQL.
     */
    @LastModifiedDate
    @Column("updated_at")
    private Instant updatedAt;
}

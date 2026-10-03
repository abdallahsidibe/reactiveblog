-- V1 : création de la table articles
--
-- Flyway exécute ce script une seule fois au démarrage de l'application,
-- via JDBC (bloquant), AVANT que Netty commence à accepter des requêtes.
-- C'est le seul endroit où JDBC est toléré dans ce projet.

CREATE TABLE IF NOT EXISTS articles (
    id          BIGSERIAL       PRIMARY KEY,
    title       VARCHAR(255)    NOT NULL,
    content     TEXT            NOT NULL,
    author      VARCHAR(100)    NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Index sur author pour accélérer la recherche par auteur
CREATE INDEX IF NOT EXISTS idx_articles_author ON articles(author);

-- Index sur created_at pour le tri chronologique
CREATE INDEX IF NOT EXISTS idx_articles_created_at ON articles(created_at DESC);

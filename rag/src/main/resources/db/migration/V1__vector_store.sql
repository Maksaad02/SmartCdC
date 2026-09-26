-- Schema du chatbot : base vectorielle et suivi d'indexation.
--
-- Idempotent (IF NOT EXISTS) : il s'applique aussi sur une base creee autrefois par
-- spring.ai.vectorstore.pgvector.initialize-schema.
--
-- Les extensions exigent un role privilegie sur la premiere creation. Sur un
-- PostgreSQL gere ou le role applicatif ne l'est pas, les activer une fois a la
-- main (voir docs/DEPLOY.md) : IF NOT EXISTS ne demande alors aucun privilege.
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Meme structure que celle creee par Spring AI (PgVectorStore). La dimension 1536 est
-- celle de text-embedding-3-small : la changer impose une migration (recreer la
-- colonne) ET une reindexation complete.
CREATE TABLE IF NOT EXISTS vector_store (
    id        uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    content   text,
    metadata  json,
    embedding vector(1536)
);

-- Index de similarite approximatif HNSW (cosinus) : sans lui chaque question parcourt
-- toute la table. m et ef_construction : valeurs par defaut de pgvector, explicites ici
-- pour pouvoir les ajuster sans dependre de la configuration de l'application.
CREATE INDEX IF NOT EXISTS spring_ai_vector_index
    ON vector_store USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- Retrouver et remplacer les passages d'un document source lors d'une reindexation.
CREATE INDEX IF NOT EXISTS vector_store_source_idx
    ON vector_store ((metadata ->> 'source'));

-- Un document source, l'empreinte de ce qui a ete indexe et quand. Permet de savoir
-- si le document ou le modele d'embedding a change, donc s'il faut reindexer.
CREATE TABLE IF NOT EXISTS rag_ingestion (
    source       varchar(255) PRIMARY KEY,
    content_hash char(64)     NOT NULL,
    chunk_count  integer      NOT NULL,
    ingested_at  timestamptz  NOT NULL DEFAULT now()
);

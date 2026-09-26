-- Execute une seule fois, a la creation du volume (docker-entrypoint-initdb.d).
-- Les extensions exigent un role privilegie : le superutilisateur de l'image
-- convient ; sur un Postgres gere, les activer une fois a la main.
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

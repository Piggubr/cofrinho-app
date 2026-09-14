-- Lugares, filmes e listas de compras. Substitui as abas Lugares, Filmes, Compras
-- e a parte MARCADOR_LUGAR da aba Configuracoes.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE places (
    id             UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    name           VARCHAR(120)   NOT NULL,
    category       VARCHAR(50)    NOT NULL DEFAULT 'Outros',
    location       VARCHAR(200)   NOT NULL DEFAULT '',
    rating         SMALLINT       NOT NULL,
    comment        VARCHAR(500)   NOT NULL DEFAULT '',
    visit_date     DATE           NOT NULL,
    -- Marcadores viravam texto separado por barra vertical na planilha.
    -- Aqui sao um array de verdade, que o banco sabe filtrar.
    tags           TEXT[]         NOT NULL DEFAULT '{}',
    photo_asset_id UUID,
    amount         NUMERIC(12, 2) NOT NULL DEFAULT 0,
    user_email     VARCHAR(320)   NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT places_rating_check CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT places_amount_check CHECK (amount >= 0)
);

CREATE INDEX idx_places_visit_date ON places (visit_date DESC);
CREATE INDEX idx_places_tags ON places USING GIN (tags);

-- Marcadores criados pelo usuario. Os quatro de fabrica ficam no codigo.
CREATE TABLE custom_place_tags (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(50)  NOT NULL UNIQUE,
    created_by VARCHAR(320) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE movies (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tmdb_id     VARCHAR(20)   NOT NULL DEFAULT '',
    title       VARCHAR(200)  NOT NULL,
    year        VARCHAR(4)    NOT NULL DEFAULT '',
    poster      VARCHAR(500)  NOT NULL DEFAULT '',
    tmdb_rating NUMERIC(3, 1) NOT NULL DEFAULT 0,
    synopsis    VARCHAR(1000) NOT NULL DEFAULT '',
    watched     BOOLEAN       NOT NULL DEFAULT FALSE,
    -- Nota de cada pessoa, no formato {"email": 4}. Era uma coluna com JSON em texto.
    ratings     JSONB         NOT NULL DEFAULT '{}'::jsonb,
    user_email  VARCHAR(320)  NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Impede o mesmo filme duas vezes na lista, regra que o Apps Script conferia
-- em memoria a cada gravacao.
CREATE UNIQUE INDEX idx_movies_tmdb ON movies (tmdb_id) WHERE tmdb_id <> '';

CREATE TABLE shopping_items (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    item       VARCHAR(150) NOT NULL,
    quantity   VARCHAR(50)  NOT NULL DEFAULT '',
    list_name  VARCHAR(20)  NOT NULL DEFAULT 'Compras',
    purchased  BOOLEAN      NOT NULL DEFAULT FALSE,
    brand      VARCHAR(100) NOT NULL DEFAULT '',
    image_url  VARCHAR(1000) NOT NULL DEFAULT '',
    barcode    VARCHAR(20)  NOT NULL DEFAULT '',
    user_email VARCHAR(320) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT shopping_items_list_check CHECK (list_name IN ('Compras', 'Desejos'))
);

CREATE INDEX idx_shopping_items_list ON shopping_items (list_name, purchased);

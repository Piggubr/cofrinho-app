-- Fotos do feed mensal e imagens dos lugares. Substitui a aba Feed e a coluna
-- Arquivo_ID da aba Lugares.
--
-- Os bytes continuam no Google Drive, como antes. O que muda e que agora existe
-- um registro proprio de cada arquivo: no Apps Script o id do Drive ficava solto
-- em uma celula, sem tipo, sem tamanho e sem dono.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE assets (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    drive_file_id  VARCHAR(200) NOT NULL,
    content_type   VARCHAR(100) NOT NULL,
    size_bytes     BIGINT       NOT NULL,
    context        VARCHAR(30)  NOT NULL DEFAULT 'GERAL',
    owner_email    VARCHAR(320) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT assets_size_check CHECK (size_bytes > 0)
);

CREATE INDEX idx_assets_owner ON assets (owner_email);
CREATE INDEX idx_assets_context ON assets (context);

CREATE TABLE feed_photos (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    month_key  VARCHAR(7)   NOT NULL,
    asset_id   UUID         NOT NULL REFERENCES assets (id) ON DELETE CASCADE,
    caption    VARCHAR(300) NOT NULL DEFAULT '',
    user_email VARCHAR(320) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT feed_photos_month_check CHECK (month_key ~ '^\d{4}-\d{2}$')
);

CREATE INDEX idx_feed_photos_month ON feed_photos (month_key DESC);

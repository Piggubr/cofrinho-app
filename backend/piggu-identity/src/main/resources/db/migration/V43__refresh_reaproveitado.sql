-- Refresh ja rotacionado de cada sessao (D-193): se um deles voltar depois de 30 s, alguem
-- copiou o refresh, e a sessao daquele aparelho cai. So o hash, como em refresh_sessions.
-- Sai junto com a sessao, e a retencao apaga os de mais de 30 dias.
CREATE TABLE refresh_rotated (
    token_hash VARCHAR(64) PRIMARY KEY,
    session_id UUID        NOT NULL REFERENCES refresh_sessions (id) ON DELETE CASCADE,
    rotated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_rotated_session ON refresh_rotated (session_id);
CREATE INDEX idx_refresh_rotated_at ON refresh_rotated (rotated_at);

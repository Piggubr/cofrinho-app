-- Aparelho de push preso a sessao de login em que foi cadastrado (D-188): sair, sessao
-- revogada, troca ou redefinicao de senha apagam a sessao, e o aparelho sai junto.
-- Os aparelhos de antes ficam sem sessao e continuam recebendo; o app cadastra de novo ao
-- abrir e prende o aparelho. Os que ficarem sem sessao por 30 dias saem na retencao.
ALTER TABLE push_devices
    ADD COLUMN session_id UUID REFERENCES refresh_sessions (id) ON DELETE CASCADE,
    ADD COLUMN seen_at    TIMESTAMPTZ NOT NULL DEFAULT now();
CREATE INDEX idx_push_devices_session ON push_devices (session_id);

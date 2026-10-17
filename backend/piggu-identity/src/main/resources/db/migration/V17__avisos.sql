-- Avisos fora do app (N2): fatura chegando, orcamento em 80/100% e conta fixa vencendo.
-- Cada canal e escolha da pessoa e nasce desligado.
ALTER TABLE users
    ADD COLUMN alerts_by_email BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN alerts_by_push  BOOLEAN NOT NULL DEFAULT FALSE;

-- Aparelho que recebe push. O token e do sistema (APNs) e muda quando ele quer: o app
-- registra de novo a cada abertura, e token recusado pela Apple e apagado.
CREATE TABLE push_devices (
    token       VARCHAR(200) PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    platform    VARCHAR(10)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT push_devices_platform_check CHECK (platform IN ('IOS'))
);
CREATE INDEX idx_push_devices_user ON push_devices (user_id);

-- Avisos ja mandados, pela chave que o financeiro da a cada um: o mesmo aviso nunca
-- chega duas vezes. Sai sozinho depois de 120 dias.
CREATE TABLE alerts_sent (
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    alert_key  VARCHAR(200) NOT NULL,
    sent_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, alert_key)
);

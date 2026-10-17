-- Avisos de compra nas lojas ja processados. O intermediario (RevenueCat) reenvia
-- quando nao recebe 200; o id do aviso garante que cada um conta uma vez so.
CREATE TABLE billing_events (
    event_id    VARCHAR(100) PRIMARY KEY,
    provider    VARCHAR(20)  NOT NULL,
    received_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

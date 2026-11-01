-- D-155: a ordem dos eventos da App Store vale por conta (trocar de familia nao reabre uma
-- transacao de antes do reembolso), e a familia guarda o token do Google Play que deu o
-- Premium (a revogacao dele derruba; o fim de outro token, nao).
ALTER TABLE users ADD COLUMN app_store_event_at TIMESTAMPTZ;
ALTER TABLE households ADD COLUMN play_purchase_token VARCHAR(4000);

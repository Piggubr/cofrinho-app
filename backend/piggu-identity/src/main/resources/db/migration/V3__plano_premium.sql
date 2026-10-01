-- Plano da conta. Premium vale enquanto premium_until estiver no futuro: vencer nao
-- precisa de job nenhum, e um webhook perdido no maximo atrasa a renovacao.
ALTER TABLE users
    ADD COLUMN premium_until      TIMESTAMPTZ,
    -- Onde a assinatura foi feita: WEB (Stripe), APP_STORE ou PLAY_STORE.
    ADD COLUMN plan_source        VARCHAR(20),
    ADD COLUMN stripe_customer_id VARCHAR(255) UNIQUE,
    -- Momento do ultimo evento aplicado; evento mais antigo que chega atrasado e ignorado.
    ADD COLUMN billing_event_at   TIMESTAMPTZ,
    ADD CONSTRAINT users_plan_source_check CHECK (plan_source IN ('WEB', 'APP_STORE', 'PLAY_STORE'));

-- Teste gratis do Premium: uma vez por conta. Marcado quando a Stripe avisa que a
-- assinatura entrou em teste (status trialing), nao ao abrir o pagamento: quem desiste
-- no meio do caminho continua com o teste disponivel.
ALTER TABLE users ADD COLUMN trial_used_at TIMESTAMPTZ;

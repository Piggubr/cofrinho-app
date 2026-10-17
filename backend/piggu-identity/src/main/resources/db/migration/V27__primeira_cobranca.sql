-- Arrependimento (CDC art. 49) conta da primeira cobranca paga, nao do inicio da
-- assinatura: o teste gratis nao gasta os 7 dias. Preenchida pelo webhook da Stripe
-- quando a assinatura fica ativa (fim do teste, ou o inicio quando nao houve teste).
ALTER TABLE households ADD COLUMN first_charge_at TIMESTAMPTZ;

-- Quem ja assinava antes disto segue com a regra antiga (inicio da assinatura).
UPDATE households SET first_charge_at = premium_since WHERE plan_source = 'WEB' AND premium_since IS NOT NULL;

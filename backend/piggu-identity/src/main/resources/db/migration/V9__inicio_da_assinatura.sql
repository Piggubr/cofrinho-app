-- Direito de arrependimento (CDC art. 49): para saber se a assinatura tem menos de 7
-- dias, guarda quando ela comecou (start_date da Stripe).
ALTER TABLE households ADD COLUMN premium_since TIMESTAMPTZ;

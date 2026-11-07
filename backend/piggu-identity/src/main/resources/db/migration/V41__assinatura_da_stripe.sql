-- A familia guarda o id da assinatura da Stripe que deu o Premium: o fim de outra
-- assinatura do mesmo cliente nao derruba a que vale (como o token do Google Play, V39).
ALTER TABLE households ADD COLUMN stripe_subscription_id VARCHAR(255);

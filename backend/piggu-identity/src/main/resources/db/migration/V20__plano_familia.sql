-- Plano individual x familia (Eduardo, 2026-10-11; V20 porque o V18 ficou com o CPF). O individual libera o Premium so para
-- quem assinou; o familia libera a casa inteira, que fica limitada a 5 pessoas.
ALTER TABLE households
    ADD COLUMN plan_kind     VARCHAR(20) NOT NULL DEFAULT 'INDIVIDUAL',
    ADD COLUMN subscriber_id UUID,
    ADD CONSTRAINT households_plan_kind_check CHECK (plan_kind IN ('INDIVIDUAL', 'FAMILIA'));

-- "Muda na hora": a assinatura que ja existia vira individual de quem pagou (a conta com
-- cliente na Stripe) ou, sem isso, do titular mais antigo da casa.
UPDATE households h SET subscriber_id = COALESCE(
    (SELECT u.id FROM users u WHERE u.household_id = h.id AND u.stripe_customer_id IS NOT NULL
     ORDER BY u.created_at LIMIT 1),
    (SELECT u.id FROM users u WHERE u.household_id = h.id AND u.role IN ('TITULAR', 'ADMIN')
     ORDER BY u.created_at LIMIT 1))
WHERE h.premium_until IS NOT NULL;

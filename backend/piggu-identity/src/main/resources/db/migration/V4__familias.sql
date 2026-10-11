-- Multiusuario por familia (household). Cada pessoa tem conta propria; a familia tem
-- um titular e membros, e todo dado de dominio (gastos, lugares, fotos...) e dela.
-- O id da familia vai no token (claim familia) e os outros servicos filtram por ele.

CREATE TABLE households (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name             VARCHAR(120) NOT NULL DEFAULT '',
    -- O Premium e da familia: o titular assina e todos usam.
    premium_until    TIMESTAMPTZ,
    plan_source      VARCHAR(20),
    billing_event_at TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT households_plan_source_check CHECK (plan_source IN ('WEB', 'APP_STORE', 'PLAY_STORE'))
);

-- Familia inicial: recebe as contas e os dados que ja existiam. O mesmo id fixo e usado
-- pela V2 de cada servico de dominio, ja que os bancos sao separados.
INSERT INTO households (id, name) VALUES ('00000000-0000-0000-0000-000000000001', 'Familia Piggu');

ALTER TABLE users ADD COLUMN household_id UUID REFERENCES households (id);
UPDATE users SET household_id = '00000000-0000-0000-0000-000000000001';
ALTER TABLE users ALTER COLUMN household_id SET NOT NULL;
CREATE INDEX idx_users_household ON users (household_id);

-- O Premium que alguem ja tinha passa a valer para a familia inteira.
UPDATE households h SET
    premium_until    = u.premium_until,
    plan_source      = u.plan_source,
    billing_event_at = u.billing_event_at
FROM (SELECT premium_until, plan_source, billing_event_at FROM users
      WHERE premium_until IS NOT NULL ORDER BY premium_until DESC LIMIT 1) u
WHERE h.id = '00000000-0000-0000-0000-000000000001';

ALTER TABLE users
    DROP CONSTRAINT users_plan_source_check,
    DROP COLUMN premium_until,
    DROP COLUMN plan_source,
    DROP COLUMN billing_event_at;

-- Papeis fixos viram papeis da familia: BEATRIZ e a titular, FAMILIAR vira membro.
ALTER TABLE users DROP CONSTRAINT users_role_check;
UPDATE users SET role = CASE role WHEN 'BEATRIZ' THEN 'TITULAR' WHEN 'FAMILIAR' THEN 'MEMBRO' ELSE role END;
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('ADMIN', 'TITULAR', 'MEMBRO'));

-- Convite do titular. Quem entra pela primeira vez com um e-mail convidado cai na
-- familia como membro; os demais ganham uma familia nova.
CREATE TABLE household_invites (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id UUID         NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    email        VARCHAR(320) NOT NULL,
    invited_by   UUID         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT household_invites_unique UNIQUE (household_id, email)
);

CREATE INDEX idx_household_invites_email ON household_invites (email);

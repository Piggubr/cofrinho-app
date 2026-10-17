-- Metas do mes e Girls Math: dois cantinhos pessoais. Cada registro e de uma pessoa
-- so (user_id) dentro da familia; a API nunca mostra o de outra pessoa, nem ao titular.

-- Metas do mes: "ler 3 livros", "ir 8 vezes a academia". Valem para o mes em que foram
-- criadas; quando o mes vira elas saem da lista, mas ficam guardadas.
CREATE TABLE personal_goals (
    household_id UUID         NOT NULL,
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL,
    -- Sempre o dia 1 do mes da meta.
    month        DATE         NOT NULL,
    title        VARCHAR(80)  NOT NULL,
    target       INTEGER      NOT NULL,
    current      INTEGER      NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT personal_goals_month_check CHECK (EXTRACT(DAY FROM month) = 1),
    CONSTRAINT personal_goals_target_check CHECK (target BETWEEN 1 AND 1000000),
    CONSTRAINT personal_goals_current_check CHECK (current BETWEEN 0 AND 1000000)
);

CREATE INDEX idx_personal_goals_household ON personal_goals (household_id, user_id, month);

-- Girls Math: o que a pessoa decidiu nao comprar. A soma e quanto ela deixou de gastar.
CREATE TABLE girls_math_items (
    household_id   UUID           NOT NULL,
    id             UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID           NOT NULL,
    product        VARCHAR(120)   NOT NULL,
    store          VARCHAR(120)   NOT NULL DEFAULT '',
    amount         NUMERIC(12, 2) NOT NULL,
    item_date      DATE           NOT NULL,
    -- Foto no servico de media; nula quando nao tem.
    photo_asset_id UUID,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT girls_math_items_amount_check CHECK (amount > 0)
);

CREATE INDEX idx_girls_math_items_household ON girls_math_items (household_id, user_id, item_date DESC);

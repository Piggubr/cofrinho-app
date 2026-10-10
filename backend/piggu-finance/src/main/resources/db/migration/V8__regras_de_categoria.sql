-- Regras de categoria: "sempre que o item ou o estabelecimento tiver <termo>, use <categoria>".
-- O termo e guardado ja normalizado (sem acento, minusculo), como a chave da memoria de precos.
CREATE TABLE category_rules (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id UUID         NOT NULL,
    term         VARCHAR(100) NOT NULL,
    category     VARCHAR(50)  NOT NULL,
    user_email   VARCHAR(320) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT category_rules_household_term_key UNIQUE (household_id, term)
);

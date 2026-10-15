-- Teste gratis ja usado por CPF de conta excluida: so o HMAC do CPF e a data, nenhum
-- outro dado pessoal. Sem isto, excluir a conta e criar outra devolvia o teste.
-- Prevencao a fraude (LGPD art. 7, IX e art. 16); apagado 5 anos depois do uso.
CREATE TABLE trial_cpf_history (
    cpf_indice VARCHAR(64) PRIMARY KEY,
    used_at    TIMESTAMPTZ NOT NULL
);

-- Campanhas que liberam o teste gratis, inclusive para quem ja usou (a excecao do
-- "teste uma vez por pessoa"). Criadas direto no banco; a pessoa chega por /plano?promo=CODIGO.
CREATE TABLE trial_campaigns (
    code                     VARCHAR(40) PRIMARY KEY,
    trial_days               INT         NOT NULL CHECK (trial_days > 0),
    starts_at                TIMESTAMPTZ NOT NULL,
    ends_at                  TIMESTAMPTZ NOT NULL,
    max_uses                 INT,
    uses                     INT         NOT NULL DEFAULT 0,
    for_previous_trial_users BOOLEAN     NOT NULL DEFAULT FALSE
);

-- Qual campanha cada conta usou: a mesma nao vale duas vezes. Marcado quando a Stripe
-- avisa que o teste comecou, como users.trial_used_at.
CREATE TABLE trial_campaign_uses (
    user_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code    VARCHAR(40) NOT NULL REFERENCES trial_campaigns (code),
    used_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, code)
);

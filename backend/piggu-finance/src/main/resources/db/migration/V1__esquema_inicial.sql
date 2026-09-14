-- Nucleo financeiro: gastos, metas mensais, cofrinho, notas, memoria de precos
-- e categorias personalizadas. Substitui as abas Gastos, Metas, Cofrinho, Notas,
-- Produtos e a parte CATEGORIA da aba Configuracoes.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Aba "Gastos". Cada linha era um item do recibo, nao o recibo inteiro:
-- receipt_id agrupa os itens lidos de uma mesma foto.
CREATE TABLE expenses (
    id           UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    expense_date DATE           NOT NULL,
    receipt_id   UUID           NOT NULL,
    merchant     VARCHAR(200)   NOT NULL DEFAULT '',
    item         VARCHAR(200)   NOT NULL,
    category     VARCHAR(50)    NOT NULL,
    amount       NUMERIC(12, 2) NOT NULL,
    kind         VARCHAR(30)    NOT NULL DEFAULT 'Variavel',
    source       VARCHAR(30)    NOT NULL DEFAULT 'Manual',
    user_email   VARCHAR(320)   NOT NULL,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT expenses_amount_check CHECK (amount >= 0 AND amount <= 1000000)
);

CREATE INDEX idx_expenses_date ON expenses (expense_date DESC);
CREATE INDEX idx_expenses_receipt ON expenses (receipt_id);
CREATE INDEX idx_expenses_created ON expenses (created_at);
CREATE INDEX idx_expenses_category ON expenses (category);

-- Aba "Metas": limite de gasto por mes. A chave era o texto AAAA-MM.
CREATE TABLE monthly_goals (
    reference_month VARCHAR(7)     PRIMARY KEY,
    limit_amount    NUMERIC(12, 2) NOT NULL,
    user_email      VARCHAR(320)   NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT monthly_goals_month_check CHECK (reference_month ~ '^\d{4}-\d{2}$'),
    CONSTRAINT monthly_goals_limit_check CHECK (limit_amount > 0 AND limit_amount <= 1000000)
);

-- Aba "Cofrinho": depositos. O saldo e' calculado, nunca guardado.
CREATE TABLE piggy_deposits (
    id           UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    deposit_date DATE           NOT NULL,
    amount       NUMERIC(12, 2) NOT NULL,
    user_email   VARCHAR(320)   NOT NULL,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT piggy_deposits_amount_check CHECK (amount > 0 AND amount <= 10000000)
);

CREATE INDEX idx_piggy_deposits_user ON piggy_deposits (user_email);
CREATE INDEX idx_piggy_deposits_created ON piggy_deposits (created_at);

-- Aba "Notas". Quando a nota tem valor, ela cria um gasto e guarda o vinculo;
-- apagar a nota apaga o gasto junto (ON DELETE SET NULL evita registro orfao
-- caso o gasto seja removido primeiro pela tela de gastos).
CREATE TABLE notes (
    id          UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    title       VARCHAR(150)   NOT NULL,
    body        VARCHAR(1500)  NOT NULL DEFAULT '',
    note_date   DATE,
    amount      NUMERIC(12, 2) NOT NULL DEFAULT 0,
    category    VARCHAR(50)    NOT NULL DEFAULT '',
    expense_id  UUID           REFERENCES expenses (id) ON DELETE SET NULL,
    user_email  VARCHAR(320)   NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT notes_amount_check CHECK (amount >= 0 AND amount <= 1000000),
    CONSTRAINT notes_paid_needs_date CHECK (amount = 0 OR note_date IS NOT NULL)
);

CREATE INDEX idx_notes_date ON notes (note_date);

-- Aba "Produtos": memoria de precos alimentada a cada gasto salvo.
-- A chave e' o nome normalizado (sem acento, sem unidade, sem artigos).
CREATE TABLE product_memory (
    product_key    VARCHAR(200)   PRIMARY KEY,
    name           VARCHAR(200)   NOT NULL,
    category       VARCHAR(50)    NOT NULL DEFAULT 'Outros',
    last_price     NUMERIC(12, 2) NOT NULL DEFAULT 0,
    min_price      NUMERIC(12, 2) NOT NULL DEFAULT 0,
    max_price      NUMERIC(12, 2) NOT NULL DEFAULT 0,
    avg_price      NUMERIC(12, 2) NOT NULL DEFAULT 0,
    purchases      INTEGER        NOT NULL DEFAULT 0,
    last_purchase  DATE,
    last_variation NUMERIC(12, 2) NOT NULL DEFAULT 0,
    user_email     VARCHAR(320)   NOT NULL,
    updated_at     TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_product_memory_purchases ON product_memory (purchases DESC);

-- Categorias criadas pelo usuario. As oito originais continuam no codigo,
-- como constantes, porque fazem parte das regras de validacao.
CREATE TABLE custom_categories (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(50)  NOT NULL UNIQUE,
    created_by VARCHAR(320) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Guarda-chuva para valores pequenos e duraveis do servico. Hoje so o ultimo
-- cambio conhecido, que o Apps Script mantinha em ULTIMA_COTACAO_EUR_BRL nas
-- propriedades do script e servia de rede de seguranca quando a API de cambio caia.
CREATE TABLE app_settings (
    setting_key   VARCHAR(100) PRIMARY KEY,
    setting_value TEXT         NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

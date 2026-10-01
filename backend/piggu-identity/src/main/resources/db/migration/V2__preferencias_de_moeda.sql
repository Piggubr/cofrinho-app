-- Moeda em que cada pessoa ve os valores, e a moeda da cotacao no topo do app.
-- Os valores gravados nao mudam: trocar a moeda troca so como aparecem.
ALTER TABLE users
    ADD COLUMN currency            VARCHAR(3) NOT NULL DEFAULT 'EUR',
    ADD COLUMN conversion_currency VARCHAR(3) NOT NULL DEFAULT 'BRL',
    ADD COLUMN show_exchange_rate  BOOLEAN    NOT NULL DEFAULT TRUE,
    ADD CONSTRAINT users_currency_check CHECK (currency ~ '^[A-Z]{3}$'),
    ADD CONSTRAINT users_conversion_currency_check CHECK (conversion_currency ~ '^[A-Z]{3}$');

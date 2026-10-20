-- A cotacao do topo passa a ter o proprio par de moedas, separado da moeda do app:
-- trocar a moeda em que os valores aparecem nao muda mais a cotacao. Quem ja tinha
-- conta segue vendo o mesmo par (a moeda do app contra a de conversao).
ALTER TABLE users
    ADD COLUMN quote_currency VARCHAR(3);
UPDATE users SET quote_currency = currency;
ALTER TABLE users
    ALTER COLUMN quote_currency SET NOT NULL,
    ALTER COLUMN quote_currency SET DEFAULT 'BRL',
    ADD CONSTRAINT users_quote_currency_check CHECK (quote_currency ~ '^[A-Z]{3}$');

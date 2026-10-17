-- Global com foco no Brasil: conta nova nasce em reais, no horario de Brasilia e em
-- portugues do Brasil. As contas que ja existiam continuam em euro, no fuso de Lisboa.
ALTER TABLE users
    ADD COLUMN timezone VARCHAR(50) NOT NULL DEFAULT 'Europe/Lisbon',
    ADD COLUMN locale   VARCHAR(10) NOT NULL DEFAULT 'pt-BR';
ALTER TABLE users
    ALTER COLUMN timezone SET DEFAULT 'America/Sao_Paulo',
    ALTER COLUMN currency SET DEFAULT 'BRL',
    ALTER COLUMN conversion_currency SET DEFAULT 'USD';

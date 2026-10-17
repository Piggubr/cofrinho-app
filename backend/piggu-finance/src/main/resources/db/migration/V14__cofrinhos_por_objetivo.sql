-- Cofrinho por objetivo: nome, quanto juntar e, se quiser, ate quando. O deposito
-- pode ir para um objetivo; sem objetivo, fica no cofrinho geral como sempre.
-- Apagar o objetivo devolve os depositos ao cofrinho geral: o dinheiro nao some.
CREATE TABLE savings_goals (
    id            UUID           PRIMARY KEY,
    household_id  UUID           NOT NULL,
    name          VARCHAR(60)    NOT NULL,
    target_amount NUMERIC(12, 2) NOT NULL,
    deadline      DATE,
    user_id       UUID,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT savings_goals_target_check CHECK (target_amount > 0 AND target_amount <= 10000000)
);

CREATE INDEX idx_savings_goals_household ON savings_goals (household_id);

ALTER TABLE piggy_deposits ADD COLUMN goal_id UUID REFERENCES savings_goals (id) ON DELETE SET NULL;
CREATE INDEX idx_piggy_deposits_goal ON piggy_deposits (goal_id);

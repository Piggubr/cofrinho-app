-- Quem o finance ja viu em cada familia: toda chamada com token grava a pessoa (PessoasDaFamilia).
-- O dividirCom so aceita quem esta aqui (BUG-022, D-192): antes, um id de outra familia ou
-- inventado virava devedor no acerto. Ja nasce com quem tem algum dado na familia.
CREATE TABLE household_people (
    household_id  UUID        NOT NULL,
    user_id       UUID        NOT NULL,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (household_id, user_id)
);

INSERT INTO household_people (household_id, user_id)
SELECT household_id, user_id FROM expenses WHERE user_id IS NOT NULL
UNION SELECT household_id, member_id FROM expense_shares WHERE member_id IS NOT NULL
UNION SELECT household_id, user_id FROM incomes WHERE user_id IS NOT NULL
UNION SELECT household_id, user_id FROM piggy_deposits WHERE user_id IS NOT NULL
UNION SELECT household_id, user_id FROM notes WHERE user_id IS NOT NULL
UNION SELECT household_id, user_id FROM recurring_bills WHERE user_id IS NOT NULL
ON CONFLICT DO NOTHING;

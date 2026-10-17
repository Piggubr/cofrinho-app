-- CPF obrigatorio so para quem mora no Brasil (Eduardo, 2026-10-13). Quem mora fora
-- nao tem CPF: a conta segue sem ele e o app nao pede. As contas que ja existiam ficam
-- como no Brasil, e o app segue pedindo o CPF a quem ainda nao informou.
ALTER TABLE users
    ADD COLUMN lives_in_brazil BOOLEAN NOT NULL DEFAULT TRUE;

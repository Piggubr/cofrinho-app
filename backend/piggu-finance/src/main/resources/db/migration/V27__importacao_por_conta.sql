-- O mesmo id de extrato (FITID, coluna id, hash da linha) em dois cartoes sao dois gastos:
-- a conta entra na chave. Sem COALESCE: apagar um cartao deixa account_id nulo (ON DELETE
-- SET NULL), e as linhas de dois cartoes apagados colidiriam. Nulos sao distintos, entao o
-- "sem conta" e deduplicado na aplicacao (ExpenseService.importar).
DROP INDEX expenses_household_user_external_id_key;
CREATE UNIQUE INDEX expenses_household_user_account_external_id_key
    ON expenses (household_id, user_id, account_id, external_id)
    WHERE external_id IS NOT NULL;

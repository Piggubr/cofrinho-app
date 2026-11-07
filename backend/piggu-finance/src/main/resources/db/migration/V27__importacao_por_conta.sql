-- O mesmo id de extrato (FITID, coluna id, hash da linha) em dois cartoes sao dois gastos:
-- a conta entra na chave. Gasto sem conta conta como uma conta so.
DROP INDEX expenses_household_user_external_id_key;
CREATE UNIQUE INDEX expenses_household_user_account_external_id_key
    ON expenses (household_id, user_id, COALESCE(account_id, '00000000-0000-0000-0000-000000000000'::uuid),
                 external_id)
    WHERE external_id IS NOT NULL;

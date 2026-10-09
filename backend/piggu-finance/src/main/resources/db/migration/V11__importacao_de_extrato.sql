-- Gasto importado de extrato (OFX/CSV): o id do lancamento no banco evita importar duas vezes.
ALTER TABLE expenses ADD COLUMN external_id VARCHAR(120);

CREATE UNIQUE INDEX expenses_household_external_id_key ON expenses (household_id, external_id)
    WHERE external_id IS NOT NULL;

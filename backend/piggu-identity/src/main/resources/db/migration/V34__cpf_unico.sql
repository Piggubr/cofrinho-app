-- Um CPF por conta de novo (Eduardo, 2026-10-22; desfaz a V25). Se ja houver CPF repetido,
-- a migration para e lista as contas de cada CPF: nada e apagado sozinho. Deixe o CPF em
-- uma conta so (as outras ficam com cpf_cifrado/cpf_indice nulos) e suba de novo.
DO $$
DECLARE
    repetidos TEXT;
BEGIN
    SELECT string_agg(contas, '; ')
      INTO repetidos
      FROM (SELECT string_agg(id::text, ', ' ORDER BY created_at) AS contas
              FROM users
             WHERE cpf_indice IS NOT NULL
             GROUP BY cpf_indice
            HAVING count(*) > 1) AS r;
    IF repetidos IS NOT NULL THEN
        RAISE EXCEPTION 'CPF repetido entre contas (a mais antiga primeiro): %', repetidos;
    END IF;
END $$;

DROP INDEX users_cpf_indice;
CREATE UNIQUE INDEX users_cpf_indice_unico ON users (cpf_indice) WHERE cpf_indice IS NOT NULL;

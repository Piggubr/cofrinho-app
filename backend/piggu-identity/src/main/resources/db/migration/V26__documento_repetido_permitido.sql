-- Documento de outros paises tambem pode repetir entre contas, como o CPF desde a V25
-- (Eduardo, 2026-10-13). O indice fica, sem ser unico, para investigar abuso.
DROP INDEX users_id_doc_indice_unico;
CREATE INDEX users_id_doc_indice ON users (id_doc_indice) WHERE id_doc_indice IS NOT NULL;

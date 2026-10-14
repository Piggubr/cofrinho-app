-- O pais onde a pessoa mora (ISO 3166, duas letras) no lugar do "mora no Brasil", e o
-- documento de identidade de quem mora fora (Eduardo, 2026-10-13). No Brasil continua o
-- CPF (colunas cpf_*); nos paises do catalogo (DocumentosDeIdentidade) vai o documento
-- de la, cifrado como o CPF e com o mesmo tipo de indice unico. Quem tinha dito "fora do
-- Brasil" fica sem pais (nulo) e segue sem documento.
ALTER TABLE users
    ADD COLUMN country              VARCHAR(2),
    ADD COLUMN id_doc_type          VARCHAR(20),
    ADD COLUMN id_doc_cifrado       VARCHAR(200),
    ADD COLUMN id_doc_indice        VARCHAR(64),
    ADD COLUMN id_doc_informado_em  TIMESTAMPTZ;

UPDATE users SET country = 'BR' WHERE lives_in_brazil;

ALTER TABLE users ALTER COLUMN country SET DEFAULT 'BR';
ALTER TABLE users DROP COLUMN lives_in_brazil;

CREATE UNIQUE INDEX users_id_doc_indice_unico ON users (id_doc_indice) WHERE id_doc_indice IS NOT NULL;

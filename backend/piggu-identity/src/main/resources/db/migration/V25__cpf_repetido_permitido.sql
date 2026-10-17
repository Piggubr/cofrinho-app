-- CPF repetido nao barra mais o cadastro (Eduardo, 2026-10-13). O indice continua, sem
-- ser unico, para achar contas com o mesmo CPF se for preciso investigar abuso.
DROP INDEX users_cpf_indice_unico;
CREATE INDEX users_cpf_indice ON users (cpf_indice) WHERE cpf_indice IS NOT NULL;

-- CPF da conta (S10). Um CPF por conta: impede a mesma pessoa de abrir mais de uma.
-- O numero fica cifrado (AES-GCM, chave fora do banco) e o indice unico e um HMAC,
-- entao um dump do banco sozinho nao entrega o CPF de ninguem. Contas antigas ficam
-- sem CPF ate a proxima entrada, quando o app pede antes de liberar o resto.
ALTER TABLE users
    ADD COLUMN cpf_cifrado      VARCHAR(200),
    ADD COLUMN cpf_indice       VARCHAR(64),
    ADD COLUMN cpf_informado_em TIMESTAMPTZ;

CREATE UNIQUE INDEX users_cpf_indice_unico ON users (cpf_indice) WHERE cpf_indice IS NOT NULL;

-- PARCEIRO: lanca e edita gastos, receitas, contas e orcamentos como o titular,
-- mas nao mexe no plano, nos convites nem tira pessoas da familia.
ALTER TABLE users DROP CONSTRAINT users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('ADMIN', 'TITULAR', 'PARCEIRO', 'MEMBRO'));

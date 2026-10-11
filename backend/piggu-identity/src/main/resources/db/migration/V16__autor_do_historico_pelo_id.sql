-- O historico da familia marca o autor pelo id da conta, como os outros servicos.
-- Aqui as contas estao no mesmo banco, entao o de-para e a propria tabela users. O
-- 'sistema' vira o id zero (TrilhaDeAuditoria.SISTEMA); e-mail sem conta (excluida antes
-- desta versao) fica nulo: sem autor.
ALTER TABLE eventos_de_auditoria ADD COLUMN autor_id UUID;
UPDATE eventos_de_auditoria e SET autor_id = u.id FROM users u WHERE lower(e.autor_email) = lower(u.email);
UPDATE eventos_de_auditoria SET autor_id = '00000000-0000-0000-0000-000000000000' WHERE autor_email = 'sistema';
ALTER TABLE eventos_de_auditoria DROP COLUMN autor_email;

-- D-154: o que a pessoa mudou na propria conta (pais, CPF, foto) sai com ela. O que fica sem
-- autor (entrou, mudou de papel) e da familia e continua.
DELETE FROM eventos_de_auditoria WHERE autor_id IS NULL AND entidade = 'conta';

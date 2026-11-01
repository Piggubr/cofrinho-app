-- D-154: o historico vai com a pessoa. Evento sem autor e de quem ja saiu da familia ou
-- excluiu a conta; com a regra nova eles nao ficam mais para a familia ver.
DELETE FROM eventos_de_auditoria WHERE autor_id IS NULL;

-- Push no Android (DATA_MODEL, "Coisas que achei", item 3). O app Android registra o
-- token do Firebase (FCM), que tem letras maiusculas, ':' e '-' e passa de 150 caracteres:
-- a coluna cresce para caber com folga e a restricao passa a aceitar ANDROID. So acrescenta
-- (D-030): a versao anterior continua gravando IOS como antes.
ALTER TABLE push_devices ALTER COLUMN token TYPE VARCHAR(512);
ALTER TABLE push_devices DROP CONSTRAINT push_devices_platform_check;
ALTER TABLE push_devices ADD CONSTRAINT push_devices_platform_check CHECK (platform IN ('IOS', 'ANDROID'));

-- Chave estrangeira sem indice: apagar uma campanha varria a tabela de usos.
CREATE INDEX idx_trial_campaign_uses_code ON trial_campaign_uses (code);

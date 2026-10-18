-- Virar parceiro depende de a pessoa aceitar: o titular convida, ela aceita no app. Ate
-- la o papel continua MEMBRO e ninguem ve os lancamentos de ninguem.
ALTER TABLE users ADD COLUMN partner_invited_at timestamptz;

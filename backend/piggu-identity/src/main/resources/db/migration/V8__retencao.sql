-- Retencao (LGPD art. 15 e 16): para saber ha quanto tempo uma conta esta desativada.
ALTER TABLE users ADD COLUMN deactivated_at TIMESTAMPTZ;
UPDATE users SET deactivated_at = updated_at WHERE active = FALSE;
CREATE INDEX idx_users_last_login ON users (last_login_at);

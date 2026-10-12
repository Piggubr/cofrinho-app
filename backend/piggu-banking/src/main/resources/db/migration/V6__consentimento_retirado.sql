-- Consentimento retirado (LGPD art. 8, par. 5, e art. 18, IX). A linha fica, com a data
-- da retirada: e a prova de que houve consentimento e de quando ele deixou de valer.
-- Depois disso o servico volta a pedir a autorizacao antes de enviar.
ALTER TABLE consents ADD COLUMN revoked_at TIMESTAMPTZ;

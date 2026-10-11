-- S7: o app so mostra os 4 ultimos digitos da conta; guardar o numero inteiro e dado
-- a mais para vazar. Corta o que ja esta gravado e impede que volte a caber.
UPDATE bank_accounts SET number = right(regexp_replace(number, '[^0-9A-Za-z]', '', 'g'), 4);
ALTER TABLE bank_accounts ALTER COLUMN number TYPE VARCHAR(4);

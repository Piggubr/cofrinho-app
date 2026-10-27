-- Fatura do cartao marcada como paga (D-085): a ultima paga, no formato AAAA-MM. As
-- anteriores contam como pagas tambem. So tira a fatura do "a pagar"; os gastos nao mudam.
ALTER TABLE payment_accounts ADD COLUMN last_paid_month VARCHAR(7);

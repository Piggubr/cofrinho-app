-- Indices que faltavam nas chaves estrangeiras (DATA_MODEL, "Coisas que achei", item 7):
-- sem eles, apagar um premio ou um lancamento varre os resgates.
CREATE INDEX idx_redemptions_prize ON redemptions (prize_id);
CREATE INDEX idx_redemptions_ledger ON redemptions (ledger_id);

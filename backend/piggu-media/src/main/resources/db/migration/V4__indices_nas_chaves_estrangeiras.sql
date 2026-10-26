-- Indice que faltava na chave estrangeira (DATA_MODEL, "Coisas que achei", item 7): apagar
-- um arquivo e a retencao (NOT EXISTS em feed_photos.asset_id) varriam a tabela.
CREATE INDEX idx_feed_photos_asset ON feed_photos (asset_id);

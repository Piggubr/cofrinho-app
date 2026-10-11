-- Modulos de estilo de vida (compras, lugares, filmes, fotos, premios) que aparecem no menu.
-- NULL = todos ligados: e como ficam as contas que ja existiam, para ninguem perder tela.
-- Conta nova nasce com '' (nenhum) e liga no perfil o que quiser.
ALTER TABLE users ADD COLUMN lifestyle_modules VARCHAR(200);

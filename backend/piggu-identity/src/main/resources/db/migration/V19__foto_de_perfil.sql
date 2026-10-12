-- Foto de perfil escolhida pela pessoa (S11). Fica aqui mesmo, como data URL de uma
-- imagem pequena ja reduzida e regravada pelo servidor (sem EXIF, sem localizacao):
-- uma tag img mostra direto, sem token, e apagar a conta apaga a foto junto.
-- sem_foto: a pessoa tirou a foto e prefere as iniciais, nem a do Google aparece.
ALTER TABLE users
    ADD COLUMN foto_propria TEXT,
    ADD COLUMN sem_foto     BOOLEAN NOT NULL DEFAULT FALSE;

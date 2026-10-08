-- A lista de e-mails liberados morreu com o cadastro aberto (V4), e a V1 semeou nela
-- e-mails reais de pessoas. A V1 nao pode ser editada (o Flyway confere o checksum),
-- entao a limpeza e esta: a tabela sai com as linhas. Quem opera a instalacao vira
-- ADMIN pela variavel PIGGU_ADMIN_EMAILS, sem e-mail nenhum no repositorio.
DROP TABLE authorized_emails;

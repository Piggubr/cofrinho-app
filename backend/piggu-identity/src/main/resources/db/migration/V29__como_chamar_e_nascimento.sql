-- Data de nascimento: decide quem e menor de idade (o titular ve os dados de quem tem
-- 13 a 17 anos) e barra menores de 13. Nula em conta antiga ate a pessoa informar.
ALTER TABLE users ADD COLUMN birth_date date;

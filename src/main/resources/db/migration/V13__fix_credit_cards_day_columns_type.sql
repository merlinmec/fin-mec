-- Corrige mismatch de schema x entidade detectado ao subir o backend com
-- ddl-auto: validate (perfil real): a V10 criou closing_day/due_day como
-- SMALLINT, mas com.mecfin.creditcard.domain.CreditCard sempre mapeou os
-- dois como "int" (Hibernate espera INTEGER). O CHECK (BETWEEN 1 AND 31) ja
-- existente nao referencia o tipo da coluna e continua valendo sem recriacao;
-- SMALLINT -> INTEGER e um widening com cast implicito, entao nao precisa de
-- USING.
ALTER TABLE credit_cards
    ALTER COLUMN closing_day TYPE INTEGER,
    ALTER COLUMN due_day TYPE INTEGER;

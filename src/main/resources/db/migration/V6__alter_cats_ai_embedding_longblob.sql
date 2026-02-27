-- Hibernate @Lob byte[] expects LONGBLOB on MySQL; fix schema validation.
ALTER TABLE cats MODIFY COLUMN ai_embedding LONGBLOB;

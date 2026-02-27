-- MySQL57Dialect maps Boolean to BIT(1); migrations used TINYINT, causing schema validation to fail.
ALTER TABLE posts MODIFY COLUMN pinned BIT(1) NOT NULL DEFAULT 0;
ALTER TABLE messages MODIFY COLUMN is_read BIT(1) NOT NULL DEFAULT 0;

ALTER TABLE user_account ADD COLUMN password_hash VARCHAR;

UPDATE user_account SET password_hash = '6692a40c9e151cb2917b1cdadfb25f98a4aca31f9b020b4ff320d04a99ccccf0' WHERE password_hash is null;

ALTER TABLE user_account ALTER COLUMN password_hash set NOT NULL;

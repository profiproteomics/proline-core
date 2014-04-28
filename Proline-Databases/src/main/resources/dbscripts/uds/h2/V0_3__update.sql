ALTER TABLE user_account ADD COLUMN password_hash VARCHAR;

UPDATE user_account SET password_hash = login WHERE password_hash is null;

ALTER TABLE user_account ALTER COLUMN password_hash set NOT NULL;

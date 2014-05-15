ALTER TABLE user_account ADD COLUMN password_hash varchar;
COMMENT ON COLUMN public.user_account.password_hash IS 'hash of user password, using sha-256.';

UPDATE user_account SET password_hash = '6692a40c9e151cb2917b1cdadfb25f98a4aca31f9b020b4ff320d04a99ccccf0' WHERE password_hash is null;

ALTER TABLE user_account ALTER COLUMN password_hash set NOT NULL;

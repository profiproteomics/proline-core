ALTER TABLE user_account ADD COLUMN password_hash varchar;
COMMENT ON COLUMN public.user_account.password_hash IS 'hash of user password, using sha-256.';

UPDATE user_account SET password_hash = encode(digest(login,'SHA256'),'hex') WHERE password_hash is null;

ALTER TABLE user_account ALTER COLUMN password_hash set NOT NULL;

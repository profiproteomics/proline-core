ALTER TABLE object_tree ALTER COLUMN serialized_data RENAME TO clob_data;

ALTER TABLE object_tree ADD COLUMN blob_data LONGVARBINARY;

ALTER TABLE object_tree_schema ADD COLUMN is_binary_mode BOOLEAN NOT NULL;

ALTER TABLE object_tree_schema ALTER COLUMN type VARCHAR(50) NOT NULL;
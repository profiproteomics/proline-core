ALTER TABLE public.object_tree RENAME COLUMN serialized_data TO clob_data;

ALTER TABLE public.object_tree ADD COLUMN blob_data BYTEA;

ALTER TABLE public.object_tree_schema ADD COLUMN is_binary_mode BOOLEAN NOT NULL;

ALTER TABLE ONLY public.object_tree_schema ALTER COLUMN type TYPE VARCHAR(50), ALTER COLUMN type SET NOT NULL;
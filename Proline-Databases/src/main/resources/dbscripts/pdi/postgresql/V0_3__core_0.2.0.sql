
ALTER TABLE public.seq_db_entry_object_tree_map DROP CONSTRAINT seq_db_entry_object_tree_map_pk;

ALTER TABLE public.seq_db_entry_object_tree_map ADD CONSTRAINT seq_db_entry_object_tree_map_pk PRIMARY KEY (seq_db_entry_id,schema_name);

ALTER TABLE public.object_tree_schema ADD COLUMN is_binary_mode VARCHAR NOT NULL;

ALTER TABLE ONLY public.object_tree_schema ALTER COLUMN type TYPE VARCHAR(50), ALTER COLUMN type SET NOT NULL;

ALTER TABLE public.object_tree ADD COLUMN blob_data BYTEA;

ALTER TABLE public.object_tree RENAME COLUMN serialized_data TO clob_data;
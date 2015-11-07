
ALTER TABLE public.seq_db_entry_object_tree_map DROP CONSTRAINT seq_db_entry_object_tree_map_pk;
ALTER TABLE public.seq_db_entry_object_tree_map DROP CONSTRAINT seq_db_entry_seq_db_entry_object_tree_map_fk;

ALTER TABLE public.seq_db_entry_object_tree_map ADD CONSTRAINT seq_db_entry_object_tree_map_pk PRIMARY KEY (seq_db_entry_id,schema_name);

ALTER TABLE public.seq_db_entry_object_tree_map ADD CONSTRAINT seq_db_entry_seq_db_entry_object_tree_map_fk
FOREIGN KEY (seq_db_entry_id)
REFERENCES public.seq_db_entry (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.object_tree_schema ADD COLUMN is_binary_mode VARCHAR NOT NULL;

ALTER TABLE public.object_tree_schema ALTER COLUMN type VARCHAR(50) NOT NULL;

ALTER TABLE public.object_tree ADD COLUMN blob_data LONGVARBINARY;

ALTER TABLE public.object_tree ALTER COLUMN serialized_data RENAME TO clob_data;
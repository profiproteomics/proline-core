
ALTER TABLE ONLY public.bio_sequence ALTER COLUMN length TYPE INTEGER, ALTER COLUMN length SET NOT NULL;

ALTER TABLE ONLY public.seq_db_instance ALTER COLUMN sequence_count TYPE INTEGER, ALTER COLUMN sequence_count SET NOT NULL;
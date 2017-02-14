
ALTER TABLE public.bio_sequence ALTER COLUMN length INTEGER NOT NULL;
COMMENT ON COLUMN public.bio_sequence.length IS 'The length of the sequence.';

ALTER TABLE public.seq_db_instance ALTER COLUMN sequence_count INTEGER NOT NULL;
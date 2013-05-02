ALTER TABLE ONLY public.bio_sequence ALTER COLUMN mass TYPE INTEGER, ALTER COLUMN mass SET NOT NULL;
COMMENT ON COLUMN public.bio_sequence.mass IS 'The approximated molecular mass of the protein or of the nucleic acid strand.';

ALTER TABLE public.taxon ADD COLUMN is_active BOOLEAN NOT NULL;
COMMENT ON TABLE public.taxon IS 'Describes the NCBI taxononmy.';

ALTER TABLE public.gene DROP COLUMN orf_names;
ALTER TABLE public.gene ADD COLUMN name_type VARCHAR(20) NOT NULL;
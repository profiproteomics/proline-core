
ALTER TABLE ONLY public.peptide_ptm ALTER COLUMN average_mass TYPE DOUBLE PRECISION, ALTER COLUMN average_mass SET NOT NULL;
COMMENT ON COLUMN public.peptide_ptm.average_mass IS 'The average mass of the corresponding PTM.

TODO: convert to REAL (float) ?';

ALTER TABLE ONLY public.peptide_ptm ALTER COLUMN mono_mass TYPE DOUBLE PRECISION, ALTER COLUMN mono_mass SET NOT NULL;
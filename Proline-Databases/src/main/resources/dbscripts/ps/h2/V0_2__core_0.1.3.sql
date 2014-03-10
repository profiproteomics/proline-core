
ALTER TABLE public.peptide_ptm ALTER COLUMN average_mass DOUBLE NOT NULL;
COMMENT ON COLUMN public.peptide_ptm.average_mass IS 'The average mass of the corresponding PTM.

TODO: convert to REAL (float) ?';

ALTER TABLE public.peptide_ptm ALTER COLUMN mono_mass DOUBLE NOT NULL;
COMMENT ON COLUMN public.peptide_ptm.mono_mass IS 'The monoisotopic mass of the corresponding PTM.';

DROP INDEX public.ptm_full_name_idx;

CREATE INDEX public.ptm_full_name_idx ON public.ptm ( full_name );
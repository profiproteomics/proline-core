
ALTER TABLE public.result_summary ALTER COLUMN is_quantified BOOLEAN NOT NULL;

ALTER TABLE public.protein_match ALTER COLUMN peptide_match_count INTEGER NOT NULL;
COMMENT ON COLUMN public.protein_match.peptide_match_count IS 'The number of peptide matches which are related to this protein match.';

/*
MANUALLY COMMENTED BY DBO BECAUSE IT IS CAUSING AN ISSUE WITH H2 DATABASE
ALTER TABLE public.peptide_readable_ptm_string DROP CONSTRAINT id;
*/

ALTER TABLE public.peptide_readable_ptm_string DROP COLUMN id;

ALTER TABLE public.peptide_readable_ptm_string ADD PRIMARY KEY (peptide_id,result_set_id);

ALTER TABLE public.protein_set_protein_match_item ADD COLUMN is_in_subset BOOLEAN NOT NULL;

ALTER TABLE public.peptide_set_peptide_instance_item ALTER COLUMN is_best_peptide_set BOOLEAN NOT NULL;

ALTER TABLE public.master_quant_peptide_ion ALTER COLUMN lcms_feature_id RENAME TO lcms_master_feature_id;

ALTER TABLE public.master_quant_peptide_ion ADD COLUMN peptide_match_count INTEGER NOT NULL;

ALTER TABLE public.master_quant_peptide_ion ALTER COLUMN charge INTEGER NOT NULL;
COMMENT ON COLUMN public.master_quant_peptide_ion.charge IS 'The charge of the quantified item (example : 2+, 3+, etc...)';

ALTER TABLE public.bio_sequence ALTER COLUMN pi REAL;
COMMENT ON COLUMN public.bio_sequence.pi IS 'The isoelectric point of the protein. Only for protein sequences (alphabet=aa).';

ALTER TABLE public.bio_sequence ALTER COLUMN mass INTEGER NOT NULL;
COMMENT ON COLUMN public.bio_sequence.mass IS 'The approximated molecular mass of the protein or of the nucleic acid strand.';

ALTER TABLE public.peptide_set ALTER COLUMN is_subset BOOLEAN NOT NULL;
COMMENT ON COLUMN public.peptide_set.is_subset IS 'Indicates if the peptide set is a subset or not.';

ALTER TABLE public.protein_set ADD COLUMN is_decoy BOOLEAN NOT NULL;
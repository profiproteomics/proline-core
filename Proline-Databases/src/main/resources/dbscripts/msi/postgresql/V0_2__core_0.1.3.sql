
ALTER TABLE ONLY public.result_summary ALTER COLUMN is_quantified TYPE BOOLEAN, ALTER COLUMN is_quantified SET NOT NULL;

ALTER TABLE ONLY public.protein_match ALTER COLUMN peptide_match_count TYPE INTEGER, ALTER COLUMN peptide_match_count SET NOT NULL;

/*
MANUALLY COMMENTED BY DBO BECAUSE IT IS CAUSING AN ISSUE WITH H2 DATABASE
ALTER TABLE public.peptide_readable_ptm_string DROP CONSTRAINT id;
*/

ALTER TABLE public.peptide_readable_ptm_string DROP COLUMN id;

ALTER TABLE public.peptide_readable_ptm_string ADD PRIMARY KEY (peptide_id,result_set_id);

ALTER TABLE public.protein_set_protein_match_item ADD COLUMN is_in_subset BOOLEAN NOT NULL;

ALTER TABLE ONLY public.peptide_set_peptide_instance_item ALTER COLUMN is_best_peptide_set TYPE BOOLEAN, ALTER COLUMN is_best_peptide_set SET NOT NULL;

ALTER TABLE public.master_quant_peptide_ion RENAME COLUMN lcms_feature_id TO lcms_master_feature_id;

ALTER TABLE public.master_quant_peptide_ion ADD COLUMN peptide_match_count INTEGER NOT NULL;

ALTER TABLE ONLY public.master_quant_peptide_ion ALTER COLUMN charge TYPE INTEGER, ALTER COLUMN charge SET NOT NULL;

ALTER TABLE ONLY public.bio_sequence ALTER COLUMN pi TYPE REAL, ALTER COLUMN pi DROP NOT NULL;
COMMENT ON COLUMN public.bio_sequence.pi IS 'The isoelectric point of the protein. Only for protein sequences (alphabet=aa).';

ALTER TABLE ONLY public.bio_sequence ALTER COLUMN mass TYPE INTEGER, ALTER COLUMN mass SET NOT NULL;
COMMENT ON COLUMN public.bio_sequence.mass IS 'The approximated molecular mass of the protein or of the nucleic acid strand.';

ALTER TABLE ONLY public.peptide_set ALTER COLUMN is_subset TYPE BOOLEAN, ALTER COLUMN is_subset SET NOT NULL;

ALTER TABLE public.protein_set ADD COLUMN is_decoy BOOLEAN NOT NULL;
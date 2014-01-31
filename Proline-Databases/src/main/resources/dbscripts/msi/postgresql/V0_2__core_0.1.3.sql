
/* Prevent alter column set not null error if some column are null */
UPDATE public.result_summary SET is_quantified = false where is_quantified is null;
UPDATE public.protein_match SET peptide_match_count = peptide_count where peptide_match_count is null;

ALTER TABLE ONLY public.result_summary ALTER COLUMN is_quantified SET NOT NULL;

ALTER TABLE ONLY public.protein_match ALTER COLUMN peptide_match_count SET NOT NULL;

/*
MANUALLY COMMENTED BY DBO BECAUSE IT IS CAUSING AN ISSUE WITH H2 DATABASE
ALTER TABLE public.peptide_readable_ptm_string DROP CONSTRAINT id;
*/

/* Change peptide_readable_ptm_string Primary KEY */
ALTER TABLE public.peptide_readable_ptm_string DROP COLUMN id;
ALTER TABLE public.peptide_readable_ptm_string ADD PRIMARY KEY (peptide_id,result_set_id);


/* Add and Update is_in_subset values in protein_set_protein_match_item */  
ALTER TABLE public.protein_set_protein_match_item ADD COLUMN is_in_subset BOOLEAN;

UPDATE protein_set_protein_match_item SET is_in_subset = FALSE 
WHERE (protein_set_protein_match_item.protein_set_id, protein_set_protein_match_item.protein_match_id) IN 
(
SELECT 
  protein_set_protein_match_item.protein_set_id, 
  protein_set_protein_match_item.protein_match_id
FROM 
  public.peptide_set, 
  public.protein_set, 
  public.protein_match, 
  public.peptide_set_protein_match_map, 
  public.protein_set_protein_match_item
WHERE 
  peptide_set.protein_set_id = protein_set.id AND
  peptide_set_protein_match_map.peptide_set_id = peptide_set.id AND
  peptide_set_protein_match_map.protein_match_id = protein_match.id AND
  protein_set_protein_match_item.protein_set_id = protein_set.id AND	
  protein_set_protein_match_item.protein_match_id = protein_match.id);

UPDATE protein_set_protein_match_item SET is_in_subset = TRUE WHERE is_in_subset is null; 

ALTER TABLE protein_set_protein_match_item ALTER COLUMN is_in_subset SET NOT NULL;


/* Set values and ADD peptide_set_peptide_instance_item.is_best_peptide_set NOT NULL*/

UPDATE peptide_set_peptide_instance_item SET is_best_peptide_set = TRUE 
WHERE (peptide_set_peptide_instance_item.peptide_instance_id, peptide_set_peptide_instance_item.peptide_set_id) IN 
(
  select pspii.peptide_instance_id, max(pspii.peptide_set_id ) 
  from peptide_set_peptide_instance_item pspii,
  (
   SELECT 
     peptide_set_peptide_instance_item.peptide_instance_id as pepInsID,
     MAX(public.peptide_set.score) as pepSetCore
   FROM 
     public.peptide_set_peptide_instance_item, 
     public.peptide_set
   WHERE 
     peptide_set_peptide_instance_item.peptide_set_id = peptide_set.id 
   Group BY peptide_set_peptide_instance_item.peptide_instance_id 
  ) pepInstBestScore,
  peptide_set
  WHERE 
   pspii.peptide_instance_id = pepInstBestScore.pepInsID
   AND peptide_set.score = pepInstBestScore.pepSetCore
   AND pspii.peptide_set_id = peptide_set.id
   GROUP BY pspii.peptide_instance_id
);

UPDATE peptide_set_peptide_instance_item SET is_best_peptide_set = FALSE WHERE is_best_peptide_set is null;

ALTER TABLE ONLY public.peptide_set_peptide_instance_item ALTER COLUMN is_best_peptide_set SET NOT NULL;

/*****/

ALTER TABLE public.master_quant_peptide_ion RENAME COLUMN lcms_feature_id TO lcms_master_feature_id;

/***** Set new column value to 0... INTEGER NOT NULL ****/

ALTER TABLE public.master_quant_peptide_ion ADD COLUMN peptide_match_count INTEGER;

UPDATE public.master_quant_peptide_ion SET peptide_match_count = 0;

ALTER TABLE public.master_quant_peptide_ion ALTER COLUMN peptide_match_count SET NOT NULL;

/*** Set Charge value if necessary ***/
UPDATE public.master_quant_peptide_ion set charge = peptide_match.charge FROM peptide_match
WHERE public.master_quant_peptide_ion.charge is null
AND peptide_match.id = master_quant_peptide_ion.best_peptide_match_id;

ALTER TABLE ONLY public.master_quant_peptide_ion ALTER COLUMN charge SET NOT NULL;


ALTER TABLE ONLY public.bio_sequence ALTER COLUMN pi DROP NOT NULL;
COMMENT ON COLUMN public.bio_sequence.pi IS 'The isoelectric point of the protein. Only for protein sequences (alphabet=aa).';

ALTER TABLE ONLY public.bio_sequence ALTER COLUMN mass TYPE INTEGER, ALTER COLUMN mass SET NOT NULL;
COMMENT ON COLUMN public.bio_sequence.mass IS 'The approximated molecular mass of the protein or of the nucleic acid strand.';

ALTER TABLE ONLY public.peptide_set ALTER COLUMN is_subset SET NOT NULL;


/*** Add Colum is_decoy and update it before set it to NOT NULL ***/
ALTER TABLE public.protein_set ADD COLUMN is_decoy BOOLEAN;

UPDATE public.protein_set SET is_decoy = TRUE 
FROM  result_summary, result_set
WHERE protein_set.result_summary_id = result_summary.id
AND result_summary.result_set_id = result_set.id
and result_set.type = 'DECOY_SEARCH';

UPDATE public.protein_set SET is_decoy = TRUE
WHERE  is_decoy is null;

ALTER TABLE public.protein_set ALTER COLUMN is_decoy SET NOT NULL;
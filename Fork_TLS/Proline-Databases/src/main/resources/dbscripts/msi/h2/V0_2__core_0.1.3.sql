/* Prevent alter column set not null error if some column are null */
UPDATE public.result_summary SET is_quantified = false where is_quantified is null;
UPDATE public.protein_match SET peptide_match_count = peptide_count where peptide_match_count is null;

ALTER TABLE public.result_summary ALTER COLUMN is_quantified BOOLEAN NOT NULL;

ALTER TABLE public.protein_match ALTER COLUMN peptide_match_count INTEGER NOT NULL;
COMMENT ON COLUMN public.protein_match.peptide_match_count IS 'The number of peptide matches which are related to this protein match.';

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
  (protein_set_protein_match_item.protein_set_id, 
  protein_set_protein_match_item.protein_match_id)
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

ALTER TABLE public.protein_set_protein_match_item ALTER COLUMN is_in_subset BOOLEAN NOT NULL;


/* Set values and ADD peptide_set_peptide_instance_item.is_best_peptide_set NOT NULL*/

UPDATE public.peptide_set_peptide_instance_item SET is_best_peptide_set = TRUE 
WHERE (peptide_set_peptide_instance_item.peptide_instance_id, peptide_set_peptide_instance_item.peptide_set_id) IN 
(
  select (pspii.peptide_instance_id, max(pspii.peptide_set_id )) 
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

ALTER TABLE public.peptide_set_peptide_instance_item ALTER COLUMN is_best_peptide_set BOOLEAN NOT NULL;

/*****/

ALTER TABLE public.master_quant_peptide_ion ALTER COLUMN lcms_feature_id RENAME TO lcms_master_feature_id;

/***** Set new column value to 0... INTEGER NOT NULL ****/

ALTER TABLE public.master_quant_peptide_ion ADD COLUMN peptide_match_count INTEGER;

UPDATE public.master_quant_peptide_ion SET peptide_match_count = 0;

ALTER TABLE public.master_quant_peptide_ion ALTER COLUMN peptide_match_count INTEGER NOT NULL;

/*** Set Charge value if necessary ***/
UPDATE public.master_quant_peptide_ion 
SET charge = (SELECT charge FROM peptide_match WHERE peptide_match.id = master_quant_peptide_ion.best_peptide_match_id) 
WHERE  public.master_quant_peptide_ion.charge is null;

ALTER TABLE public.master_quant_peptide_ion ALTER COLUMN charge INTEGER NOT NULL;

COMMENT ON COLUMN public.master_quant_peptide_ion.charge IS 'The charge of the quantified item (example : 2+, 3+, etc...)';

ALTER TABLE public.bio_sequence ALTER COLUMN pi REAL;
COMMENT ON COLUMN public.bio_sequence.pi IS 'The isoelectric point of the protein. Only for protein sequences (alphabet=aa).';

ALTER TABLE public.bio_sequence ALTER COLUMN mass INTEGER NOT NULL;
COMMENT ON COLUMN public.bio_sequence.mass IS 'The approximated molecular mass of the protein or of the nucleic acid strand.';

ALTER TABLE public.peptide_set ALTER COLUMN is_subset BOOLEAN NOT NULL;
COMMENT ON COLUMN public.peptide_set.is_subset IS 'Indicates if the peptide set is a subset or not.';

/*** Add Colum is_decoy and update it before set it to NOT NULL ***/

ALTER TABLE public.protein_set ADD COLUMN is_decoy BOOLEAN;

UPDATE protein_set SET is_decoy = (select   (case WHEN result_set.type = 'DECOY_SEARCH' THEN true ELSE false END) 
FROM result_summary, result_set WHERE protein_set.result_summary_id = result_summary.id and result_summary.result_set_id = result_set.id);
   

ALTER TABLE public.protein_set ALTER COLUMN is_decoy BOOLEAN NOT NULL;
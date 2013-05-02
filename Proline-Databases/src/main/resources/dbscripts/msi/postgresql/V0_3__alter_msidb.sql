ALTER TABLE public.result_summary ADD COLUMN creation_log TEXT;

ALTER TABLE public.result_set ADD COLUMN creation_log TEXT;

ALTER TABLE public.peptide_set ADD COLUMN scoring_id INTEGER NOT NULL;

ALTER TABLE public.peptide_set ADD CONSTRAINT scoring_peptide_set_fk
FOREIGN KEY (scoring_id)
REFERENCES public.scoring (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.protein_set DROP CONSTRAINT scoring_protein_set_fk;

ALTER TABLE public.protein_set DROP COLUMN score;

ALTER TABLE public.protein_set DROP COLUMN scoring_id;

ALTER TABLE public.protein_set_cluster_item DROP CONSTRAINT protein_set_cluster_protein_set_cluster_item_fk;

ALTER TABLE public.protein_set_cluster DROP CONSTRAINT result_summary_protein_set_cluster_fk;

ALTER TABLE public.protein_set_cluster_item DROP CONSTRAINT result_summary_protein_cluster_item_fk;

ALTER TABLE public.protein_set_cluster DROP CONSTRAINT protein_set_protein_set_cluster_fk;

ALTER TABLE public.protein_set_cluster_item DROP CONSTRAINT protein_set_protein_cluster_item_fk;

ALTER TABLE public.used_ptm DROP COLUMN type;

DROP TABLE public.protein_set_cluster;

ALTER TABLE public.enzyme ADD COLUMN serialized_properties TEXT;

ALTER TABLE public.peptide_instance ADD COLUMN validated_protein_set_count INTEGER NOT NULL;
DROP TABLE public.protein_set_cluster_item;

ALTER TABLE ONLY public.peptide_set_peptide_instance_item ALTER COLUMN selection_level TYPE INTEGER, ALTER COLUMN selection_level SET NOT NULL;
COMMENT ON COLUMN public.peptide_set_peptide_instance_item.selection_level IS 'An integer coding for the selection of this peptide instance in the context of this peptide set:
0 = manual deselection
1 = automatic deselection
2 = automatic selection
3 = manual selection';

ALTER TABLE public.master_quant_peptide_ion ADD COLUMN master_quant_peptide_id INTEGER NOT NULL;

ALTER TABLE ONLY public.bio_sequence ALTER COLUMN alphabet TYPE VARCHAR(3), ALTER COLUMN alphabet SET NOT NULL;

ALTER TABLE public.master_quant_peptide_ion ADD CONSTRAINT master_quant_peptide_master_quant_peptide_ion_fk
FOREIGN KEY (master_quant_peptide_id)
REFERENCES public.master_quant_component (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;
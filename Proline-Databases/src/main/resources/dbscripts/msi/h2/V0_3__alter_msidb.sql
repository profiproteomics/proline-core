ALTER TABLE result_summary ADD COLUMN creation_log LONGVARCHAR;

ALTER TABLE result_set ADD COLUMN creation_log LONGVARCHAR;

ALTER TABLE peptide_set ADD COLUMN scoring_id INTEGER NOT NULL;

ALTER TABLE peptide_set ADD CONSTRAINT scoring_peptide_set_fk
FOREIGN KEY (scoring_id)
REFERENCES scoring (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE protein_set DROP CONSTRAINT scoring_protein_set_fk;

ALTER TABLE protein_set DROP COLUMN score;

ALTER TABLE protein_set DROP COLUMN scoring_id;

ALTER TABLE protein_set_cluster_item DROP CONSTRAINT protein_set_cluster_protein_set_cluster_item_fk;

ALTER TABLE protein_set_cluster DROP CONSTRAINT result_summary_protein_set_cluster_fk;

ALTER TABLE protein_set_cluster_item DROP CONSTRAINT result_summary_protein_cluster_item_fk;

ALTER TABLE protein_set_cluster DROP CONSTRAINT protein_set_protein_set_cluster_fk;

ALTER TABLE protein_set_cluster_item DROP CONSTRAINT protein_set_protein_cluster_item_fk;

ALTER TABLE used_ptm DROP COLUMN type;

DROP TABLE protein_set_cluster;

ALTER TABLE enzyme ADD COLUMN serialized_properties LONGVARCHAR;

ALTER TABLE peptide_instance ADD COLUMN validated_protein_set_count INTEGER NOT NULL;
DROP TABLE protein_set_cluster_item;

ALTER TABLE peptide_set_peptide_instance_item ALTER COLUMN selection_level INTEGER NOT NULL;
COMMENT ON COLUMN peptide_set_peptide_instance_item.selection_level IS 'An integer coding for the selection of this peptide instance in the context of this peptide set:
0 = manual deselection
1 = automatic deselection
2 = automatic selection
3 = manual selection';

ALTER TABLE master_quant_peptide_ion ADD COLUMN master_quant_peptide_id INTEGER NOT NULL;

ALTER TABLE bio_sequence ALTER COLUMN alphabet VARCHAR(3) NOT NULL;

ALTER TABLE master_quant_peptide_ion ADD CONSTRAINT master_quant_peptide_master_quant_peptide_ion_fk
FOREIGN KEY (master_quant_peptide_id)
REFERENCES master_quant_component (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;
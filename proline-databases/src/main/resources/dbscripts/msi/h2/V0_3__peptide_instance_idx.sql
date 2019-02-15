-- Index on second element of PeptideSetPeptideInstanceItem composite PK
CREATE INDEX pep_set_pep_inst_item_pep_inst_idx
 ON peptide_set_peptide_instance_item (peptide_instance_id);

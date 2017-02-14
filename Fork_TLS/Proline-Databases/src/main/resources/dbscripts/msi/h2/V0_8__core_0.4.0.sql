
/* SCRIPT GENERATED BY POWER ARCHITECT */

ALTER TABLE protein_set DROP CONSTRAINT protein_match_protein_set_fk;

ALTER TABLE protein_match ALTER COLUMN score REAL DEFAULT 0 NOT NULL;
COMMENT ON COLUMN protein_match.score IS 'The identification score of the protein.';

ALTER TABLE protein_set_protein_match_item ADD COLUMN coverage REAL DEFAULT 0 NOT NULL;

ALTER TABLE search_settings DROP COLUMN quantitation;

ALTER TABLE peaklist ALTER COLUMN raw_file_name RENAME TO raw_file_identifier;

ALTER TABLE peptide_set ADD COLUMN sequence_count INTEGER NOT NULL;

ALTER TABLE protein_set ALTER COLUMN typical_protein_match_id RENAME TO representative_protein_match_id;

ALTER TABLE msi_search DROP COLUMN submitted_queries_count;

CREATE INDEX peptide_match_best_child_idx
 ON peptide_match
 ( best_child_id );

CREATE INDEX protein_set_master_quant_component_idx
 ON protein_set
 ( master_quant_component_id );

CREATE INDEX peptide_match_relation_parent_peptide_match_idx
 ON peptide_match_relation
 ( parent_peptide_match_id );

CREATE INDEX peptide_instance_peptide_match_map_peptide_match_idx
 ON peptide_instance_peptide_match_map
 ( peptide_match_id );

CREATE INDEX peptide_instance_master_quant_component_idx
 ON peptide_instance
 ( master_quant_component_id );

CREATE INDEX object_tree_schema_name_idx
 ON object_tree
 ( schema_name );

CREATE INDEX ms_query_spectrum_idx
 ON ms_query
 ( spectrum_id );

CREATE INDEX master_quant_peptide_ion_best_peptide_match_idx
 ON master_quant_peptide_ion
 ( best_peptide_match_id );

CREATE INDEX peptide_instance_best_peptide_match_idx
 ON peptide_instance
 ( best_peptide_match_id );

CREATE INDEX sequence_match_best_peptide_match_idx
 ON sequence_match
 ( best_peptide_match_id );

CREATE INDEX master_quant_peptide_ion_master_quant_component_idx
 ON master_quant_peptide_ion
 ( master_quant_component_id );

CREATE INDEX peptide_match_relation_child_peptide_match_idx
 ON peptide_match_relation
 ( child_peptide_match_id );

ALTER TABLE protein_set ADD CONSTRAINT protein_match_protein_set_fk
FOREIGN KEY (representative_protein_match_id)
REFERENCES protein_match (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

/* END OF SCRIPT GENERATED BY POWER ARCHITECT */

/* ADDITIONAL SQL QUERIES FIXING THE "ON DELETE CASCADE" CONSTRAINTS" */

ALTER TABLE public.peptide_readable_ptm_string DROP CONSTRAINT result_set_peptide_readable_ptm_string_fk;
ALTER TABLE public.peptide_readable_ptm_string ADD CONSTRAINT result_set_peptide_readable_ptm_string_fk
FOREIGN KEY (result_set_id)
REFERENCES public.result_set (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.protein_set DROP CONSTRAINT result_summary_protein_set_fk;
ALTER TABLE public.protein_set ADD CONSTRAINT result_summary_protein_set_fk
FOREIGN KEY (result_summary_id)
REFERENCES public.result_summary (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

/* END ADDITIONAL SQL QUERIES FIXING THE "ON DELETE CASCADE" CONSTRAINTS" */

/* ADDITIONAL SQL QUERIES USED FOR DATA UPDATE */

INSERT INTO scoring(search_engine,name,description) values ('mascot', 'ions score', 'The score provided for each Mascot peptide.'),
  ('mascot', 'standard score', 'The score provided for each Mascot protein hit (it corresponds to the sum of ion scores).'),
  ('mascot', 'mudpit score', 'The score provided for each Mascot protein hit when the number of MS/MS queries is high.'),
  ('mascot', 'modified mudpit score', 'A modified version of the MudPIT score computed by Proline.'),
  ('omssa', 'expect value', 'The -log(E-value) provided by OMSSA for a peptide match.'), 
  ('comet', 'evalue log scaled', 'The -log(expectation value) provided by Comet for a peptide match.'),
  ('msgf', 'evalue log scaled', 'The -log(EValue) provided by MS-GF for a peptide match.'),
  ('sequest', 'expect log scaled', 'The -log(expect) provided by Sequest for a peptide match.'),
  ('xtandem', 'hyperscore', 'The hyperscore provided by X!Tandem for a peptide match.');

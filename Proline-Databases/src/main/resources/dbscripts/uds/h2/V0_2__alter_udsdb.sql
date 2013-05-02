ALTER TABLE quant_channel DROP CONSTRAINT dataset_quant_channel_fk;

COMMENT ON TABLE master_quant_channel IS 'Store the quantitation profiles and ratios. May correspond to a quantitation overview (one unique fraction).';
COMMENT ON TABLE external_db IS 'Contains connexion properties for databases associated to projects. 
Databases allowing multiple instances are necessarily associated to projects.
Singleton databases (PDIdb, PSdb, ePims, ...) are also define through this table without any specific association to any projects';
COMMENT ON TABLE project_db_map IS 'Mapping table between the project and external_db tables.';
COMMENT ON TABLE project_user_account_map IS 'Mappinng table between user_account and project table';
COMMENT ON TABLE quant_channel IS 'A quanti channel represents all quantified peptides from a single replicate of a single fraction of a biological sample. UNIQUE(context_key, quantitation_fraction_id).';

ALTER TABLE quant_channel ALTER COLUMN name VARCHAR(100);
COMMENT ON COLUMN quant_channel.name IS 'A name for this quant channel as defined by the user.';

ALTER TABLE quant_channel DROP COLUMN quant_result_summary_id;

ALTER TABLE quant_channel ALTER COLUMN dataset_id RENAME TO quantitation_id;

ALTER TABLE enzyme ADD COLUMN serialized_properties LONGVARCHAR;
COMMENT ON TABLE run_identification IS 'The identification of a run.';
COMMENT ON TABLE biological_group IS 'A group of related biological sample. A group is a generic concept that can be used to represents physiological conditions, pool or sample preparation conditions.';

ALTER TABLE biological_group ADD COLUMN quantitation_id INTEGER NOT NULL;
COMMENT ON TABLE quant_method IS 'The quantificatin method description.';
COMMENT ON TABLE spec_title_parsing_rule IS 'Describe rules used to parse the content of the MS2 spectrum description. Note: using the attribute names of  the spectrum table enables an easier implementation.';
COMMENT ON TABLE instrument_config IS 'The description of a mass spectrometer instrument configuration.';

ALTER TABLE data_set ALTER COLUMN fraction_count RENAME TO children_count;
COMMENT ON TABLE quant_label IS 'TODO: rename to quantitative_labels or quant_labels ? (same semantic than quantitation_method ???)';

COMMENT ON TABLE instrument IS 'The identification of a Mass Spectrometer. Properties (name,source) must be unique.';
COMMENT ON TABLE project IS 'A project contains multiple experiments relative to the same study or topic. Files associated to a project are stored in the repository in ''''/root/project_${project_id}''''.';
COMMENT ON TABLE fragmentation_rule IS 'Each instrument can have one or more of  fragment ion / rules. This rules describes ion fragment series that can be observed on an instrument and that are used by serach engine to generate theoritical spectrum and for scoring spectrum_peptide match';

ALTER TABLE fragmentation_rule ALTER COLUMN theoretical_fragment_id RENAME TO fragment_series_id;
COMMENT ON TABLE instrument_config_fragmentation_rule_map IS 'The set of fragmentation rules associated with this instrument configuration';

ALTER TABLE quant_channel ADD CONSTRAINT dataset_quant_channel_fk
FOREIGN KEY (quantitation_id)
REFERENCES data_set (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE biological_group ADD CONSTRAINT data_set_biological_group_fk
FOREIGN KEY (quantitation_id)
REFERENCES data_set (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE fragmentation_rule DROP CONSTRAINT fragmentation_series_fragmentation_rule_fk;

ALTER TABLE fragmentation_rule DROP CONSTRAINT fragmentation_series_fragmentation_rule_fk1;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE fragmentation_rule ADD CONSTRAINT fragmentation_series_fragmentation_rule_fk
FOREIGN KEY (fragment_series_id)
REFERENCES fragmentation_series (id)
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE public.fragmentation_rule ADD CONSTRAINT required_series_fragmentation_rule_fk
FOREIGN KEY (required_series_id)
REFERENCES public.fragmentation_series (id)
ON UPDATE NO ACTION;
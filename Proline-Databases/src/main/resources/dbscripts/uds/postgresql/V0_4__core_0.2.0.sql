
/* RENAME PROJECTS WITH ID SUFFIX IN ORDER TO CREATE THE UNIQUE INDEX ON (name,owner_id) */

UPDATE project SET name = concat( project.name, '(', project.id, ')' );

/* END OF RENAME PROJECTS WITH ID SUFFIX IN ORDER TO CREATE THE UNIQUE INDEX ON (name,owner_id) */


/* SCRIPT GENERATED BY POWER ARCHITECT */

ALTER TABLE public.spec_title_parsing_rule DROP COLUMN name;

CREATE UNIQUE INDEX project_name_owner_idx
 ON project
 ( name, owner_id );

ALTER TABLE public.object_tree ADD COLUMN blob_data BYTEA;

ALTER TABLE public.object_tree RENAME COLUMN serialized_data TO clob_data;

CREATE TABLE public.data_set_object_tree_map (
                data_set_id BIGINT NOT NULL,
                schema_name VARCHAR(1000) NOT NULL,
                object_tree_id BIGINT NOT NULL,
                CONSTRAINT data_set_object_tree_map_pk PRIMARY KEY (data_set_id, schema_name)
);


ALTER TABLE ONLY public.object_tree_schema ALTER COLUMN type TYPE VARCHAR(50), ALTER COLUMN type SET NOT NULL;

ALTER TABLE public.object_tree_schema ADD COLUMN is_binary_mode BOOLEAN NOT NULL;

ALTER TABLE public.data_set_object_tree_map ADD CONSTRAINT object_tree_data_set_object_tree_map_fk
FOREIGN KEY (object_tree_id)
REFERENCES public.object_tree (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.data_set_object_tree_map ADD CONSTRAINT data_set_data_set_object_tree_map_fk
FOREIGN KEY (data_set_id)
REFERENCES public.data_set (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.data_set_object_tree_map ADD CONSTRAINT object_tree_schema_data_set_object_tree_map_fk
FOREIGN KEY (schema_name)
REFERENCES public.object_tree_schema (name)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

/* END OF SCRIPT GENERATED BY POWER ARCHITECT */


/* ADDITIONAL SQL QUERIES FIXING THE CREATION OF INDICES NOT PERFORMED IN PREVIOUS MIGRATIONS */

DROP INDEX quant_channel_context_idx;

CREATE UNIQUE INDEX quant_channel_context_idx
 ON quant_channel
 ( master_quant_channel_id, context_key, quant_label_id );

CREATE UNIQUE INDEX quant_channel_number_idx
 ON quant_channel
 ( master_quant_channel_id, number );

CREATE UNIQUE INDEX master_quant_channel_number_idx
 ON master_quant_channel
 ( quantitation_id, number );

CREATE UNIQUE INDEX biological_sample_number_idx
 ON biological_sample
 ( quantitation_id, number );

CREATE UNIQUE INDEX group_setup_number_idx
 ON group_setup
 ( quantitation_id, number );

CREATE UNIQUE INDEX biological_group_number_idx
 ON biological_group
 ( quantitation_id, number );

CREATE UNIQUE INDEX ratio_definition_number_idx
 ON ratio_definition
 ( group_setup_id, number );

/* END OF ADDITIONAL SQL QUERIES FIXING THE CREATION OF INDICES NOT PERFORMED IN PREVIOUS MIGRATIONS */

/* ADDITIONAL SQL QUERIES ALLOWING TO PERFORM A DELETE CASCADE OF ENTITIES RELATING TO THE PROJECT TABLE */

ALTER TABLE public.project_user_account_map DROP CONSTRAINT project_project_user_map_fk;
ALTER TABLE public.project_user_account_map ADD CONSTRAINT project_project_user_map_fk
FOREIGN KEY (project_id)
REFERENCES project (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.data_set DROP CONSTRAINT project_dataset_fk;
ALTER TABLE public.data_set ADD CONSTRAINT project_data_set_fk
FOREIGN KEY (project_id)
REFERENCES project (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.raw_file_project_map DROP CONSTRAINT project_raw_file_project_map_fk;
ALTER TABLE public.raw_file_project_map ADD CONSTRAINT project_raw_file_project_map_fk
FOREIGN KEY (project_id)
REFERENCES project (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.master_quant_channel DROP CONSTRAINT dataset_master_quant_channel_fk;
ALTER TABLE public.master_quant_channel ADD CONSTRAINT data_set_master_quant_channel_fk
FOREIGN KEY (quantitation_id)
REFERENCES data_set (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.group_setup DROP CONSTRAINT dataset_group_setup_fk;
ALTER TABLE public.group_setup ADD CONSTRAINT data_set_group_setup_fk
FOREIGN KEY (quantitation_id)
REFERENCES data_set (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.quant_channel DROP CONSTRAINT dataset_quant_channel_fk;
ALTER TABLE public.quant_channel ADD CONSTRAINT data_set_quant_channel_fk
FOREIGN KEY (quantitation_id)
REFERENCES data_set (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.biological_sample DROP CONSTRAINT dataset_biological_sample_fk;
ALTER TABLE public.biological_sample ADD CONSTRAINT data_set_biological_sample_fk
FOREIGN KEY (quantitation_id)
REFERENCES data_set (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.sample_analysis DROP CONSTRAINT dataset_sample_analysis_fk;
ALTER TABLE public.sample_analysis ADD CONSTRAINT data_set_sample_analysis_fk
FOREIGN KEY (quantitation_id)
REFERENCES data_set (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.run_identification DROP CONSTRAINT dataset_run_identification_fk;
ALTER TABLE public.run_identification ADD CONSTRAINT data_set_run_identification_fk
FOREIGN KEY (id)
REFERENCES data_set (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.data_set DROP CONSTRAINT dataset_dataset_fk;
ALTER TABLE public.data_set ADD CONSTRAINT data_set_data_set_fk
FOREIGN KEY (parent_dataset_id)
REFERENCES data_set (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.biological_group DROP CONSTRAINT data_set_biological_group_fk;
ALTER TABLE public.biological_group ADD CONSTRAINT data_set_biological_group_fk
FOREIGN KEY (quantitation_id)
REFERENCES data_set (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.biological_sample_sample_analysis_map DROP CONSTRAINT sample_analysis_biological_sample_sample_analysis_map_fk;
ALTER TABLE public.biological_sample_sample_analysis_map ADD CONSTRAINT sample_analysis_biological_sample_sample_analysis_map_fk
FOREIGN KEY (sample_analysis_id)
REFERENCES sample_analysis (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.quant_channel DROP CONSTRAINT master_quant_channel_quant_channel_fk;
ALTER TABLE public.quant_channel ADD CONSTRAINT master_quant_channel_quant_channel_fk
FOREIGN KEY (master_quant_channel_id)
REFERENCES master_quant_channel (id)
ON DELETE CASCADE
ON UPDATE CASCADE;

ALTER TABLE public.biological_sample_sample_analysis_map DROP CONSTRAINT biological_sample_biological_sample_sample_analysis_map_fk;
ALTER TABLE public.biological_sample_sample_analysis_map ADD CONSTRAINT biological_sample_biological_sample_sample_analysis_map_fk
FOREIGN KEY (biological_sample_id)
REFERENCES biological_sample (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.group_setup_biological_group_map DROP CONSTRAINT group_setup_group_setup_biological_group_map_fk;
ALTER TABLE public.group_setup_biological_group_map ADD CONSTRAINT group_setup_group_setup_biological_group_map_fk
FOREIGN KEY (group_setup_id)
REFERENCES group_setup (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.ratio_definition DROP CONSTRAINT biological_group_ratio_numerator_fk;
ALTER TABLE public.ratio_definition ADD CONSTRAINT biological_group_ratio_numerator_fk
FOREIGN KEY (numerator_id)
REFERENCES biological_group (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.ratio_definition DROP CONSTRAINT biological_group_ratio_denominator_fk;
ALTER TABLE public.ratio_definition ADD CONSTRAINT biological_group_ratio_denominator_fk
FOREIGN KEY (denominator_id)
REFERENCES biological_group (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.group_setup_biological_group_map DROP CONSTRAINT biological_group_group_setup_biological_group_map_fk;
ALTER TABLE public.group_setup_biological_group_map ADD CONSTRAINT biological_group_group_setup_biological_group_map_fk
FOREIGN KEY (biological_group_id)
REFERENCES biological_group (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

/* END OF ADDITIONAL SQL QUERIES ALLOWING TO PERFORM A DELETE CASCADE OF ENTITIES RELATING TO THE PROJECT TABLE */
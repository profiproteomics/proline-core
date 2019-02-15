
/* SCRIPT GENERATED BY POWER ARCHITECT */

ALTER TABLE run DROP CONSTRAINT raw_file_run_fk;

ALTER TABLE run_identification DROP CONSTRAINT raw_file_run_identification_fk;

ALTER TABLE raw_file_project_map DROP CONSTRAINT raw_file_raw_file_project_map_fk;

ALTER TABLE run ALTER COLUMN raw_file_name RENAME TO raw_file_identifier;

ALTER TABLE project_user_account_map ADD COLUMN write_permission BOOLEAN NOT NULL;

ALTER TABLE run_identification ALTER COLUMN raw_file_name RENAME TO raw_file_identifier;

ALTER TABLE spec_title_parsing_rule ALTER COLUMN raw_file_name RENAME TO raw_file_identifier;
COMMENT ON TABLE peaklist_software IS 'Describes software that can be used to generate MS/MS peaklists.
TODO: add UNIQUE( name, version )';

ALTER TABLE raw_file_project_map ALTER COLUMN raw_file_name RENAME TO raw_file_identifier;

ALTER TABLE sample_analysis DROP COLUMN number;

ALTER TABLE raw_file ADD COLUMN mzdb_file_directory VARCHAR(500);

ALTER TABLE raw_file ALTER COLUMN name RENAME TO identifier;

ALTER TABLE raw_file ADD COLUMN raw_file_name VARCHAR(250) NOT NULL;

ALTER TABLE raw_file DROP COLUMN extension;

ALTER TABLE raw_file ALTER COLUMN directory RENAME TO raw_file_directory;

ALTER TABLE raw_file ADD COLUMN sample_name VARCHAR(250);
ALTER TABLE raw_file ADD COLUMN mzdb_file_name VARCHAR(250);
COMMENT ON TABLE instrument IS 'This table lists the user mass spectrometers.
TODO: check the UNIQUE constraint is correctly set in SQL script';

ALTER TABLE project ADD COLUMN lock_expiration_timestamp TIMESTAMP;

ALTER TABLE project ADD COLUMN lock_user_id BIGINT;

ALTER TABLE biological_sample_sample_analysis_map ADD COLUMN sample_analysis_number INTEGER NOT NULL;

CREATE INDEX object_tree_schema_name_idx
 ON object_tree
 ( schema_name );

CREATE UNIQUE INDEX peaklist_software_idx
 ON peaklist_software
 ( name, version );

CREATE UNIQUE INDEX biological_sample_sample_analysis_map_idx
 ON biological_sample_sample_analysis_map
 ( biological_sample_id, sample_analysis_number );

ALTER TABLE project ADD CONSTRAINT user_account_project_lock_user_fk
FOREIGN KEY (lock_user_id)
REFERENCES user_account (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE run ADD CONSTRAINT raw_file_run_fk
FOREIGN KEY (raw_file_identifier)
REFERENCES raw_file (identifier)
ON DELETE CASCADE
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE run_identification ADD CONSTRAINT raw_file_run_identification_fk
FOREIGN KEY (raw_file_identifier)
REFERENCES raw_file (identifier)
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE raw_file_project_map ADD CONSTRAINT raw_file_raw_file_project_map_fk
FOREIGN KEY (raw_file_identifier)
REFERENCES raw_file (identifier)
ON UPDATE NO ACTION;

/* END OF SCRIPT GENERATED BY POWER ARCHITECT */

/* ADDITIONAL SQL QUERIES CORRESPONDING TO SCHEMA MIGRATIONS MISSED BY POWER ARCHITECT */

ALTER TABLE public.project DROP CONSTRAINT user_account_project_fk;
ALTER TABLE public.project ADD CONSTRAINT user_account_project_owner_fk
FOREIGN KEY (owner_id)
REFERENCES public.user_account (id)
ON UPDATE NO ACTION;/* END OF ADDITIONAL SQL QUERIES CORRESPONDING TO SCHEMA MIGRATIONS MISSED BY POWER ARCHITECT */

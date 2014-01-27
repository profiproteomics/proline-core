
ALTER TABLE public.group_setup ADD COLUMN number INTEGER NOT NULL;

CREATE TABLE public.raw_file_project_map (
                raw_file_name VARCHAR(250) NOT NULL,
                project_id BIGINT NOT NULL,
                serialized_properties TEXT,
                CONSTRAINT raw_file_project_map_pk PRIMARY KEY (raw_file_name, project_id)
);
COMMENT ON COLUMN public.raw_file_project_map.raw_file_name IS 'The name of the raw file which serves as its identifier.
It should not contain an extension and be unique across all the database.';
COMMENT ON COLUMN public.raw_file_project_map.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


ALTER TABLE public.raw_file_project_map ADD CONSTRAINT raw_file_raw_file_project_map_fk
FOREIGN KEY (raw_file_name)
REFERENCES public.raw_file (name)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.raw_file_project_map ADD CONSTRAINT project_raw_file_project_map_fk
FOREIGN KEY (project_id)
REFERENCES public.project (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;
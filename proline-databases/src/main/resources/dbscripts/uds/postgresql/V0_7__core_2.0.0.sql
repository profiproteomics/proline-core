
/* SCRIPT GENERATED BY POWER ARCHITECT AND MODIFIED MANUALLY (see Redmine issue #15671) */

-- Remove table "admin_infos" --
DROP TABLE public.admin_infos;

--- Rename column "data_set.children_count" to "data_set.child_count" ---
ALTER TABLE public.data_set RENAME COLUMN children_count TO child_count;

-- Remove "username" & "password" columns from "external_db" table --
ALTER TABLE public.external_db DROP COLUMN username;
ALTER TABLE public.external_db DROP COLUMN password;

-- Remove "instrument_id" FK from "raw_file" table --
ALTER TABLE public.raw_file DROP COLUMN instrument_id;

-- Add "ident_data_set_id" and "ident_result_summary_id" columns to "master_quant_channel" table --
ALTER TABLE public.master_quant_channel ADD COLUMN ident_data_set_id BIGINT;
ALTER TABLE public.master_quant_channel ADD COLUMN ident_result_summary_id BIGINT;

--- Set "quant_channel.name" as NOT NULL --
ALTER TABLE ONLY public.quant_channel ALTER COLUMN name TYPE VARCHAR(100), ALTER COLUMN name SET NOT NULL;

--- Add "number" column to "quant_label" table ---
ALTER TABLE public.quant_label ADD COLUMN number INTEGER;

--- Add constraint "ident_data_set_master_quant_channel_fk" ---
ALTER TABLE public.master_quant_channel ADD CONSTRAINT ident_data_set_master_quant_channel_fk
FOREIGN KEY (ident_data_set_id)
REFERENCES public.data_set (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

--- MANUAL ADDITION: rename constraint "data_set_master_quant_channel_fk" to "quant_data_set_master_quant_channel_fk" ---
ALTER TABLE public.master_quant_channel RENAME CONSTRAINT data_set_master_quant_channel_fk TO quant_data_set_master_quant_channel_fk;

-- Set name of existing quant channels (having an empty name) --
UPDATE quant_channel SET name = context_key WHERE (name = '') IS NOT FALSE;

-- Add UNIQUE constraint for "quant_channel.name" and a given "master_quant_channel" --
CREATE UNIQUE INDEX quant_channel_unique_name_idx
 ON public.quant_channel
 ( master_quant_channel_id, name );

CREATE UNIQUE INDEX quant_label_number_idx
 ON public.quant_label
 ( quant_method_id, number );
 
-- Add UNIQUE constraint for "quant_label.name" and a given "quant_method" --
CREATE UNIQUE INDEX quant_label_unique_name_idx
 ON public.quant_label
 ( quant_method_id, name );


CREATE SEQUENCE public.fragmentation_rule_set_id_seq;

CREATE TABLE public.fragmentation_rule_set (
  id BIGINT NOT NULL DEFAULT nextval('public.fragmentation_rule_set_id_seq'),
  name VARCHAR(200) NOT NULL,
  CONSTRAINT id PRIMARY KEY (id)
);
ALTER SEQUENCE public.fragmentation_rule_set_id_seq OWNED BY public.fragmentation_rule_set.id;

CREATE UNIQUE INDEX fragmentation_rule_set_name_idx
 ON public.fragmentation_rule_set
 ( name );

CREATE TABLE public.fragmentation_rule_set_map (
  fragmentation_rule_id BIGINT NOT NULL,
  fragmentation_rule_set_id BIGINT NOT NULL,
  CONSTRAINT fragmentation_rule_set_map_pk PRIMARY KEY (fragmentation_rule_id, fragmentation_rule_set_id)
);

ALTER TABLE public.fragmentation_rule_set_map ADD CONSTRAINT fragmentation_rule_set_fragmentation_rule_set_map_fk
FOREIGN KEY (fragmentation_rule_set_id)
REFERENCES public.fragmentation_rule_set (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.fragmentation_rule_set_map ADD CONSTRAINT fragmentation_rule_fragmentation_rule_set_map_fk
FOREIGN KEY (fragmentation_rule_id)
REFERENCES public.fragmentation_rule (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;


/* END OF SCRIPT GENERATED BY POWER ARCHITECT AND MODIFIED MANUALLY */


/* ADDITIONAL SQL QUERIES USED FOR DATA UPDATE */

-- Set default value to quant_label.number and set as NOT NULL --
UPDATE quant_label SET number = quant_label.id;
ALTER TABLE quant_label ALTER COLUMN number SET NOT NULL;

-- Rename quant_method.type value 'isobaric_tag' to 'isobaric_tagging' --
UPDATE quant_method SET type = 'isobaric_tagging' WHERE type = 'isobaric_tag'; 

-- Update fragmentation_rule_set and associated tables
INSERT INTO fragmentation_rule_set (name) SELECT DISTINCT (name) FROM instrument_config;

INSERT INTO fragmentation_rule_set_map (fragmentation_rule_set_id, fragmentation_rule_id)
  (
  SELECT fragmentation_rule_set.id, instrFragRuleMap.frid
  FROM fragmentation_rule_set,
    (
    SELECT ic."name" as icname, icfm.fragmentation_rule_id AS frid
    FROM instrument i, instrument_config ic, instrument_config_fragmentation_rule_map icfm
    WHERE i.id = ic.instrument_id and ic.id = icfm.instrument_config_id
    ) instrFragRuleMap
  WHERE name = instrFragRuleMap.icname
  );

-- Remove table "instrument_config_fragmentation_rule_map" --
DROP TABLE public.instrument_config_fragmentation_rule_map;

/* END OF ADDITIONAL SQL QUERIES USED FOR DATA UPDATE */


/* LIST OF OPERATIONS TO BE PERFORMED IN THE NEXT JAVA MIGRATION */
-- Import of PSdb data into all MSI databases
-- Remove PSdb
-- Fix quant label.number value
-- Put MasterQuantChannelProperties IDs in the master_quant_channel table
-- SpectralCountProperties => weightsRefRSMIds should be weightsRefRsmIds to have the correct serialization key
-- Move spectralCountProperties from MasterQuantChannelProperties to dedicated object tree
-- ResultSummaryRelation is not persisted for XIC (redmine issue #14342)

/* END LIST OF OPERATIONS TO BE PERFORMED IN THE NEXT JAVA MIGRATION */
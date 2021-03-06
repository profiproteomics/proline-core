
/* UPDATE PROCESSED_MAP FIELDS */
UPDATE processed_map SET is_locked = false;
UPDATE processed_map SET normalization_factor = 1 WHERE normalization_factor IS NULL;

/* SCRIPT GENERATED BY POWER ARCHITECT AND MODIFIED MANUALLY */

ALTER TABLE public.compound DROP CONSTRAINT best_feature_compound_fk;

ALTER TABLE public.object_tree_schema ADD COLUMN is_binary_mode BOOLEAN NOT NULL;

ALTER TABLE ONLY public.object_tree_schema ALTER COLUMN type TYPE VARCHAR(50), ALTER COLUMN type SET NOT NULL;

CREATE SEQUENCE public.peakel_id_seq;

CREATE TABLE public.peakel (
                id BIGINT NOT NULL DEFAULT nextval('public.peakel_id_seq'),
                moz DOUBLE PRECISION NOT NULL,
                elution_time REAL NOT NULL,
                apex_intensity REAL NOT NULL,
                area REAL NOT NULL,
                duration REAL NOT NULL,
                fwhm REAL,
                is_overlapping BOOLEAN NOT NULL,
                feature_count INTEGER NOT NULL,
                peak_count INTEGER NOT NULL,
                peaks BYTEA NOT NULL,
                serialized_properties TEXT,
                first_scan_id BIGINT NOT NULL,
                last_scan_id BIGINT NOT NULL,
                apex_scan_id BIGINT NOT NULL,
                map_id BIGINT NOT NULL,
                CONSTRAINT peakel_pk PRIMARY KEY (id)
);
COMMENT ON COLUMN public.peakel.moz IS 'A m/z value associated to the peakel. May be determined as the median/mean of peaks. May also be the m/z of the apex.';
COMMENT ON COLUMN public.peakel.apex_intensity IS 'Maximum intensity of this peakel. This intensity may also be a normalized value from a value stored in another map.';
COMMENT ON COLUMN public.peakel.area IS 'Integrated area for this peakel. This area may also be a normalized value from a value stored in another map.';
COMMENT ON COLUMN public.peakel.duration IS 'The elution duration in seconds of this peakel.';


ALTER SEQUENCE public.peakel_id_seq OWNED BY public.peakel.id;

ALTER TABLE public.map_object_tree_mapping DROP CONSTRAINT map_object_tree_mapping_pk;

ALTER TABLE public.map_object_tree_mapping ADD CONSTRAINT map_object_tree_mapping_pk PRIMARY KEY (map_id,schema_name);

ALTER TABLE public.map_set_object_tree_mapping DROP CONSTRAINT map_set_object_tree_mapping_pk;

ALTER TABLE public.map_set_object_tree_mapping ADD CONSTRAINT map_set_object_tree_mapping_pk PRIMARY KEY (map_set_id,schema_name);

ALTER TABLE public.object_tree RENAME COLUMN serialized_data TO clob_data;

ALTER TABLE public.object_tree ADD COLUMN blob_data BYTEA;

ALTER TABLE public.feature_object_tree_mapping DROP CONSTRAINT feature_object_tree_mapping_pk;

ALTER TABLE public.feature_object_tree_mapping ADD CONSTRAINT feature_object_tree_mapping_pk PRIMARY KEY (feature_id,schema_name);

ALTER TABLE ONLY public.map_layer ALTER COLUMN name TYPE VARCHAR(250), ALTER COLUMN name SET NOT NULL;

ALTER TABLE ONLY public.processed_map ALTER COLUMN is_locked TYPE BOOLEAN, ALTER COLUMN is_locked SET DEFAULT false, ALTER COLUMN is_locked SET NOT NULL;

ALTER TABLE ONLY public.processed_map ALTER COLUMN normalization_factor TYPE REAL, ALTER COLUMN normalization_factor SET DEFAULT 1, ALTER COLUMN normalization_factor SET NOT NULL;

ALTER TABLE public.feature ADD COLUMN apex_intensity REAL DEFAULT 0 NOT NULL;

ALTER TABLE public.feature ADD COLUMN duration REAL DEFAULT 0 NOT NULL;

CREATE TABLE public.feature_peakel_item (
                feature_id BIGINT NOT NULL,
                peakel_id BIGINT NOT NULL,
                isotope_index INTEGER NOT NULL,
                serialized_properties TEXT,
                map_id BIGINT NOT NULL,
                CONSTRAINT feature_peakel_item_pk PRIMARY KEY (feature_id, peakel_id)
);

ALTER TABLE public.feature_peakel_item ADD CONSTRAINT peakel_feature_peakel_item_fk
FOREIGN KEY (peakel_id)
REFERENCES public.peakel (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peakel ADD CONSTRAINT apex_scan_peakel_fk
FOREIGN KEY (apex_scan_id)
REFERENCES public.scan (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peakel ADD CONSTRAINT first_scan_peakel_fk
FOREIGN KEY (first_scan_id)
REFERENCES public.scan (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peakel ADD CONSTRAINT last_scan_peakel_fk
FOREIGN KEY (last_scan_id)
REFERENCES public.scan (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peakel ADD CONSTRAINT map_peakel_fk
FOREIGN KEY (map_id)
REFERENCES public.map (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.feature_peakel_item ADD CONSTRAINT map_feature_peakel_item_fk
FOREIGN KEY (map_id)
REFERENCES public.map (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.feature_peakel_item ADD CONSTRAINT feature_feature_peakel_item_fk
FOREIGN KEY (feature_id)
REFERENCES public.feature (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

/* END OF SCRIPT GENERATED BY POWER ARCHITECT AND MODIFIED MANUALLY */


/* ADDITIONAL SQL QUERIES FIXING THE NAME OF FOREIGN KEYS NOT CORRECTLY RENAMED IN PREVIOUS MIGRATIONS */

ALTER TABLE public.cache DROP CONSTRAINT new_table_pk;
ALTER TABLE public.cache ADD CONSTRAINT cache_pk PRIMARY KEY (scope, id, format, byte_order);

ALTER TABLE public.processed_map_raw_map_mapping DROP CONSTRAINT processed_map_run_map_mapping_pk;
ALTER TABLE public.processed_map_raw_map_mapping ADD CONSTRAINT processed_map_run_map_mapping_pk PRIMARY KEY (processed_map_id, raw_map_id);

ALTER INDEX run_map_pk RENAME TO raw_map_pk;

ALTER INDEX run_pk RENAME TO scan_sequence_pk;


ALTER TABLE public.scan_sequence DROP CONSTRAINT instrument_run_fk;
ALTER TABLE public.scan_sequence ADD CONSTRAINT instrument_scan_sequence_fk
FOREIGN KEY (instrument_id)
REFERENCES public.instrument (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.feature_ms2_event DROP CONSTRAINT run_map_feature_ms2_scan_fk;
ALTER TABLE public.feature_ms2_event ADD CONSTRAINT raw_map_feature_ms2_event_fk
FOREIGN KEY (run_map_id)
REFERENCES public.raw_map (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.feature_overlap_mapping DROP CONSTRAINT run_map_feature_overlap_mapping_fk;
ALTER TABLE public.feature_overlap_mapping ADD CONSTRAINT raw_map_feature_overlap_mapping_fk
FOREIGN KEY (map_id)
REFERENCES public.raw_map (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.ms_picture DROP CONSTRAINT run_ms_picture_fk;
ALTER TABLE public.ms_picture ADD CONSTRAINT scan_sequence_ms_picture_fk
FOREIGN KEY (run_id)
REFERENCES public.scan_sequence (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.processed_map_raw_map_mapping DROP CONSTRAINT processed_map_processed_map_run_map_mapping_fk;
ALTER TABLE public.processed_map_raw_map_mapping ADD CONSTRAINT processed_map_processed_map_raw_map_mapping_fk
FOREIGN KEY (processed_map_id)
REFERENCES public.processed_map (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.raw_map DROP CONSTRAINT feature_fitting_model_run_map_fk;
ALTER TABLE public.raw_map ADD CONSTRAINT feature_fitting_model_raw_map_fk
FOREIGN KEY (peakel_fitting_model_id)
REFERENCES public.peakel_fitting_model (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.raw_map DROP CONSTRAINT map_run_map_fk;
ALTER TABLE public.raw_map ADD CONSTRAINT map_raw_map_fk
FOREIGN KEY (id)
REFERENCES public.map (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.raw_map DROP CONSTRAINT peak_picking_software_run_map_fk;
ALTER TABLE public.raw_map ADD CONSTRAINT peak_picking_software_raw_map_fk
FOREIGN KEY (peak_picking_software_id)
REFERENCES public.peak_picking_software (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

/* END OF ADDITIONAL SQL QUERIES FIXING THE NAME OF FOREIGN KEYS NOT CORRECTLY RENAMED IN PREVIOUS MIGRATIONS */
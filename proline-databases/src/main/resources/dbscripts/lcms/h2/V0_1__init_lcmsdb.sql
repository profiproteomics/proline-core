
CREATE TABLE public.feature_scoring (
                id IDENTITY NOT NULL,
                name VARCHAR(100) NOT NULL,
                description VARCHAR(1000),
                serialized_properties LONGVARCHAR,
                CONSTRAINT feature_scoring_pk PRIMARY KEY (id)
);


CREATE TABLE public.map (
                id IDENTITY NOT NULL,
                name VARCHAR(1000) NOT NULL,
                description VARCHAR(10000),
                type INTEGER NOT NULL,
                creation_timestamp TIMESTAMP NOT NULL,
                modification_timestamp TIMESTAMP NOT NULL,
                serialized_properties LONGVARCHAR,
                feature_scoring_id BIGINT NOT NULL,
                CONSTRAINT map_pk PRIMARY KEY (id)
);
COMMENT ON COLUMN public.map.type IS '0 => run_map 1 => processed_map';


CREATE TABLE public.cache (
                scope VARCHAR(250) NOT NULL,
                id BIGINT NOT NULL,
                format VARCHAR(50) NOT NULL,
                byte_order INTEGER NOT NULL,
                data LONGVARBINARY NOT NULL,
                compression VARCHAR(20) NOT NULL,
                timestamp TIMESTAMP NOT NULL,
                serialized_properties LONGVARCHAR,
                CONSTRAINT New_Table_pk PRIMARY KEY (scope, id, format, byte_order)
);
COMMENT ON COLUMN public.cache.scope IS 'e.g. scope=map.features id=1 (map id)';


CREATE TABLE public.instrument (
                id BIGINT NOT NULL,
                name VARCHAR(100) NOT NULL,
                source VARCHAR(100) NOT NULL,
                serialized_properties LONGVARCHAR,
                CONSTRAINT instrument_pk PRIMARY KEY (id)
);


CREATE TABLE public.object_tree_schema (
                name VARCHAR(1000) NOT NULL,
                type VARCHAR(10) NOT NULL,
                version VARCHAR(100) NOT NULL,
                schema LONGVARCHAR NOT NULL,
                description VARCHAR(1000),
                serialized_properties LONGVARCHAR,
                CONSTRAINT object_tree_schema_pk PRIMARY KEY (name)
);
COMMENT ON COLUMN public.object_tree_schema.type IS 'XSD or JSON';
COMMENT ON COLUMN public.object_tree_schema.schema IS 'The document describing the schema used for the serialization of the object_tree.';


CREATE TABLE public.object_tree (
                id IDENTITY NOT NULL,
                serialized_data LONGVARCHAR NOT NULL,
                serialized_properties LONGVARCHAR,
                schema_name VARCHAR(1000) NOT NULL,
                CONSTRAINT object_tree_pk PRIMARY KEY (id)
);
COMMENT ON COLUMN public.object_tree.serialized_data IS 'A object tree serialized in a string using a given format (XML or JSON).';
COMMENT ON COLUMN public.object_tree.serialized_properties IS 'May be used to store the creation timestamp and other meta information.';


CREATE TABLE public.map_object_tree_mapping (
                map_id BIGINT NOT NULL,
                object_tree_id BIGINT NOT NULL,
                schema_name VARCHAR(1000) NOT NULL,
                CONSTRAINT map_object_tree_mapping_pk PRIMARY KEY (map_id, object_tree_id)
);


CREATE TABLE public.peakel_fitting_model (
                id IDENTITY NOT NULL,
                name VARCHAR(100) NOT NULL,
                serialized_properties LONGVARCHAR,
                CONSTRAINT peakel_fitting_model_pk PRIMARY KEY (id)
);


CREATE TABLE public.peak_picking_software (
                id IDENTITY NOT NULL,
                name VARCHAR(100) NOT NULL,
                version VARCHAR(100) NOT NULL,
                algorithm VARCHAR(250),
                serialized_properties LONGVARCHAR,
                CONSTRAINT peak_picking_software_pk PRIMARY KEY (id)
);


CREATE TABLE public.processed_map (
                id BIGINT NOT NULL,
                number INTEGER NOT NULL,
                normalization_factor REAL,
                is_master BOOLEAN NOT NULL,
                is_aln_reference BOOLEAN NOT NULL,
                is_locked BOOLEAN,
                map_set_id BIGINT NOT NULL,
                CONSTRAINT processed_map_pk PRIMARY KEY (id)
);
COMMENT ON COLUMN public.processed_map.is_master IS 'A master map links all aligned MS1 features together in a given map set (using the feature_cluster table).';
COMMENT ON COLUMN public.processed_map.is_aln_reference IS 'Describes the reference map used for elution time alignment.';
COMMENT ON COLUMN public.processed_map.is_locked IS 'A locked map can''t be used in a new workflow step.';


CREATE INDEX public.processed_map_map_set_idx
 ON public.processed_map
 ( map_set_id );

CREATE TABLE public.map_set (
                id IDENTITY NOT NULL,
                name VARCHAR(250) NOT NULL,
                map_count INTEGER NOT NULL,
                creation_timestamp TIMESTAMP NOT NULL,
                serialized_properties LONGVARCHAR,
                master_map_id BIGINT,
                aln_reference_map_id BIGINT,
                CONSTRAINT map_set_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.map_set IS 'Associated maps must be locked.';


CREATE TABLE public.map_set_object_tree_mapping (
                map_set_id BIGINT NOT NULL,
                object_tree_id BIGINT NOT NULL,
                schema_name VARCHAR(1000) NOT NULL,
                CONSTRAINT map_set_object_tree_mapping_pk PRIMARY KEY (map_set_id, object_tree_id)
);


CREATE TABLE public.map_alignment (
                from_map_id BIGINT NOT NULL,
                to_map_id BIGINT NOT NULL,
                mass_start REAL NOT NULL,
                mass_end REAL NOT NULL,
                time_list LONGVARCHAR NOT NULL,
                delta_time_list LONGVARCHAR NOT NULL,
                serialized_properties LONGVARCHAR,
                map_set_id BIGINT NOT NULL,
                CONSTRAINT map_alignment_pk PRIMARY KEY (from_map_id, to_map_id, mass_start, mass_end)
);
COMMENT ON TABLE public.map_alignment IS 'Defines the elution time alignment between a map (from) and another map (to).';
COMMENT ON COLUMN public.map_alignment.time_list IS 'A list of  values separated by spaces. The reference map is "from_map".';
COMMENT ON COLUMN public.map_alignment.delta_time_list IS 'A list of delta times separated by spaces. delta_time = to_map.time - from_map.time => to_map.time = from_map.time + delta';


CREATE INDEX public.map_alignment_map_set_idx
 ON public.map_alignment
 ( map_set_id );

CREATE TABLE public.map_layer (
                id IDENTITY NOT NULL,
                number INTEGER NOT NULL,
                name VARCHAR(250),
                serialized_properties LONGVARCHAR,
                processed_map_id BIGINT NOT NULL,
                map_set_id BIGINT NOT NULL,
                CONSTRAINT map_layer_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.map_layer IS 'Minimum information for labeling strategies';
COMMENT ON COLUMN public.map_layer.number IS 'Each conditon must be numbered (from 1 to n) for each associated map.';
COMMENT ON COLUMN public.map_layer.name IS 'An optional name which describes this condition';


CREATE INDEX public.map_layer_processed_map_idx
 ON public.map_layer
 ( processed_map_id );

CREATE INDEX public.map_layer_map_set_idx
 ON public.map_layer
 ( map_set_id );

CREATE TABLE public.theoretical_feature (
                id IDENTITY NOT NULL,
                moz DOUBLE NOT NULL,
                charge INTEGER NOT NULL,
                elution_time REAL NOT NULL,
                source_type VARCHAR(50) NOT NULL,
                serialized_properties LONGVARCHAR,
                map_layer_id BIGINT,
                map_id BIGINT NOT NULL,
                CONSTRAINT theoretical_feature_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.theoretical_feature IS 'Theoretical features retrieved from an identification database or a feature cross-assignment.';
COMMENT ON COLUMN public.theoretical_feature.elution_time IS 'Time in seconds';
COMMENT ON COLUMN public.theoretical_feature.source_type IS 'AMT, CROSS-ASSIGNMENT';


CREATE INDEX public.theoretical_feature_map_idx
 ON public.theoretical_feature
 ( map_id );

CREATE TABLE public.run (
                id BIGINT NOT NULL,
                raw_file_name VARCHAR(250) NOT NULL,
                min_intensity DOUBLE,
                max_intensity DOUBLE,
                ms1_scan_count INTEGER NOT NULL,
                ms2_scan_count INTEGER NOT NULL,
                serialized_properties LONGVARCHAR,
                instrument_id BIGINT NOT NULL,
                CONSTRAINT run_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.run IS 'TODO: rename to scan_sequence ?';


CREATE TABLE public.run_map (
                id BIGINT NOT NULL,
                run_id BIGINT NOT NULL,
                peak_picking_software_id BIGINT NOT NULL,
                peakel_fitting_model_id BIGINT,
                CONSTRAINT run_map_pk PRIMARY KEY (id)
);


CREATE TABLE public.processed_map_run_map_mapping (
                processed_map_id BIGINT NOT NULL,
                run_map_id BIGINT NOT NULL,
                CONSTRAINT processed_map_run_map_mapping_pk PRIMARY KEY (processed_map_id, run_map_id)
);


CREATE TABLE public.ms_picture (
                id IDENTITY NOT NULL,
                z_index INTEGER NOT NULL,
                moz_resolution REAL NOT NULL,
                time_resolution REAL NOT NULL,
                serialized_properties LONGVARCHAR,
                run_id BIGINT NOT NULL,
                CONSTRAINT ms_picture_pk PRIMARY KEY (id)
);
COMMENT ON COLUMN public.ms_picture.z_index IS '0 -1 -2....';


CREATE TABLE public.tile (
                id IDENTITY NOT NULL,
                x_index INTEGER NOT NULL,
                y_index INTEGER NOT NULL,
                begin_moz DOUBLE NOT NULL,
                end_moz DOUBLE NOT NULL,
                begin_time REAL NOT NULL,
                end_time REAL NOT NULL,
                width INTEGER NOT NULL,
                height INTEGER NOT NULL,
                intensities LONGVARBINARY NOT NULL,
                serialized_properties LONGVARCHAR,
                ms_picture_id BIGINT NOT NULL,
                CONSTRAINT tile_pk PRIMARY KEY (id)
);
COMMENT ON COLUMN public.tile.width IS 'pixel width';
COMMENT ON COLUMN public.tile.height IS 'pixel height';
COMMENT ON COLUMN public.tile.intensities IS 'intensities in binary format';


CREATE TABLE public.scan (
                id IDENTITY NOT NULL,
                initial_id INTEGER NOT NULL,
                cycle INTEGER NOT NULL,
                time REAL NOT NULL,
                ms_level INTEGER NOT NULL,
                tic DOUBLE NOT NULL,
                base_peak_moz DOUBLE NOT NULL,
                base_peak_intensity DOUBLE NOT NULL,
                precursor_moz DOUBLE,
                precursor_charge INTEGER,
                serialized_properties LONGVARCHAR,
                run_id BIGINT NOT NULL,
                CONSTRAINT scan_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.scan IS 'TODO: add polarity ???';
COMMENT ON COLUMN public.scan.time IS 'Scan time in seconds';


CREATE INDEX public.scan_run_idx
 ON public.scan
 ( run_id );

CREATE INDEX public.scan_precursor_moz_idx
 ON public.scan
 ( precursor_moz );

CREATE TABLE public.processed_map_moz_calibration (
                processed_map_id BIGINT NOT NULL,
                scan_id BIGINT NOT NULL,
                moz_list LONGVARCHAR NOT NULL,
                delta_moz_list LONGVARCHAR NOT NULL,
                serialized_properties LONGVARCHAR,
                CONSTRAINT processed_map_moz_calibration_pk PRIMARY KEY (processed_map_id, scan_id)
);
COMMENT ON TABLE public.processed_map_moz_calibration IS 'Defines the moz alignment between a map (from) and another map (to) in function of time.';
COMMENT ON COLUMN public.processed_map_moz_calibration.moz_list IS 'A list of  values separated by spaces.';
COMMENT ON COLUMN public.processed_map_moz_calibration.delta_moz_list IS 'A list of delta times separated by spaces. delta_time = to_map.time - from_map.calue => to_map.time = from_map.time + delta';


CREATE TABLE public.feature (
                id IDENTITY NOT NULL,
                moz DOUBLE NOT NULL,
                intensity REAL NOT NULL,
                charge INTEGER NOT NULL,
                elution_time REAL NOT NULL,
                quality_score REAL,
                ms1_count INTEGER NOT NULL,
                ms2_count INTEGER NOT NULL,
                is_cluster BOOLEAN NOT NULL,
                is_overlapping BOOLEAN NOT NULL,
                serialized_properties LONGVARCHAR,
                first_scan_id BIGINT NOT NULL,
                last_scan_id BIGINT NOT NULL,
                apex_scan_id BIGINT NOT NULL,
                theoretical_feature_id BIGINT,
                compound_id BIGINT,
                map_layer_id BIGINT,
                map_id BIGINT NOT NULL,
                CONSTRAINT feature_pk PRIMARY KEY (id)
);
COMMENT ON COLUMN public.feature.moz IS 'A m/z value associated to the feature. May be determined as the median/mean of the isotopic pattern m/z. May also be the m/z of the apex.';
COMMENT ON COLUMN public.feature.intensity IS 'Computed using the isotopic pattern intensities. The function used to produce this intensity may take only some of the peaks in the isotopic pattern (describe this in the map properties). This intensity may also be a normalized value from a value stored in another map.';
COMMENT ON COLUMN public.feature.quality_score IS 'A score reflecting the quality of the extracted signal for this feature.';
COMMENT ON COLUMN public.feature.map_id IS 'May correspond to a native map or a processed map (i.e. feature clusters, master features).';


CREATE INDEX public.feature_map_idx
 ON public.feature
 ( map_id );

CREATE INDEX public.feature_moz_time_charge_idx
 ON public.feature
 ( moz, elution_time, charge );

CREATE TABLE public.compound (
                id IDENTITY NOT NULL,
                experimental_mass DOUBLE NOT NULL,
                theoretical_mass DOUBLE,
                elution_time REAL NOT NULL,
                formula VARCHAR(1000),
                serialized_properties LONGVARCHAR,
                best_feature_id BIGINT NOT NULL,
                map_layer_id BIGINT,
                map_id BIGINT NOT NULL,
                CONSTRAINT compound_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.compound IS 'Describes molecules that are or may be (theoretical) in the map.';


CREATE INDEX public.compound_map_idx
 ON public.compound
 ( map_id );

CREATE TABLE public.feature_overlap_mapping (
                overlapped_feature_id BIGINT NOT NULL,
                overlapping_feature_id BIGINT NOT NULL,
                map_id BIGINT NOT NULL,
                CONSTRAINT feature_overlap_mapping_pk PRIMARY KEY (overlapped_feature_id, overlapping_feature_id)
);


CREATE INDEX public.feature_overlap_mapping_map_idx
 ON public.feature_overlap_mapping
 ( map_id );

CREATE TABLE public.feature_object_tree_mapping (
                feature_id BIGINT NOT NULL,
                object_tree_id BIGINT NOT NULL,
                schema_name VARCHAR(1000) NOT NULL,
                CONSTRAINT feature_object_tree_mapping_pk PRIMARY KEY (feature_id, object_tree_id)
);


CREATE TABLE public.processed_map_feature_item (
                processed_map_id BIGINT NOT NULL,
                feature_id BIGINT NOT NULL,
                calibrated_moz DOUBLE NOT NULL,
                normalized_intensity REAL NOT NULL,
                corrected_elution_time REAL NOT NULL,
                is_clusterized BOOLEAN NOT NULL,
                selection_level INTEGER NOT NULL,
                serialized_properties LONGVARCHAR,
                CONSTRAINT processed_map_feature_item_pk PRIMARY KEY (processed_map_id, feature_id)
);
COMMENT ON COLUMN public.processed_map_feature_item.is_clusterized IS 'True if this feature is a associated to an intra map cluster. To have the right  list of map features don''t forget to filter on this value (is_clusterized=false).';


CREATE TABLE public.master_feature_item (
                master_feature_id BIGINT NOT NULL,
                child_feature_id BIGINT NOT NULL,
                is_best_child BOOLEAN NOT NULL,
                master_map_id BIGINT NOT NULL,
                CONSTRAINT master_feature_item_pk PRIMARY KEY (master_feature_id, child_feature_id)
);
COMMENT ON COLUMN public.master_feature_item.is_best_child IS 'The best child is the feature of reference for the master feature. Scan ids of the master features should be identical to the scan ids of the best child feature.';


CREATE INDEX public.master_feature_item_master_map_idx
 ON public.master_feature_item
 ( master_map_id );

CREATE TABLE public.feature_ms2_event (
                feature_id BIGINT NOT NULL,
                ms2_event_id BIGINT NOT NULL,
                run_map_id BIGINT NOT NULL,
                CONSTRAINT feature_ms2_event_pk PRIMARY KEY (feature_id, ms2_event_id)
);


CREATE INDEX public.feature_ms2_event_run_map_idx
 ON public.feature_ms2_event
 ( run_map_id );

CREATE TABLE public.feature_cluster_item (
                cluster_feature_id BIGINT NOT NULL,
                sub_feature_id BIGINT NOT NULL,
                processed_map_id BIGINT NOT NULL,
                CONSTRAINT feature_cluster_item_pk PRIMARY KEY (cluster_feature_id, sub_feature_id)
);
COMMENT ON TABLE public.feature_cluster_item IS 'clustering over the same map or across several maps (merge)';


CREATE INDEX public.feature_cluster_item_processed_map_idx
 ON public.feature_cluster_item
 ( processed_map_id );

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE public.map ADD CONSTRAINT feature_scoring_map_fk
FOREIGN KEY (feature_scoring_id)
REFERENCES public.feature_scoring (id)
ON UPDATE NO ACTION;

ALTER TABLE public.feature ADD CONSTRAINT map_feature_fk
FOREIGN KEY (map_id)
REFERENCES public.map (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.compound ADD CONSTRAINT map_compound_fk
FOREIGN KEY (map_id)
REFERENCES public.map (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.processed_map ADD CONSTRAINT map_processed_map_fk
FOREIGN KEY (id)
REFERENCES public.map (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.theoretical_feature ADD CONSTRAINT map_theoretical_feature_fk
FOREIGN KEY (map_id)
REFERENCES public.map (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.run_map ADD CONSTRAINT map_run_map_fk
FOREIGN KEY (id)
REFERENCES public.map (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.map_object_tree_mapping ADD CONSTRAINT map_map_object_tree_mapping_fk
FOREIGN KEY (map_id)
REFERENCES public.map (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE public.run ADD CONSTRAINT instrument_run_fk
FOREIGN KEY (instrument_id)
REFERENCES public.instrument (id)
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE public.object_tree ADD CONSTRAINT object_tree_schema_object_tree_fk
FOREIGN KEY (schema_name)
REFERENCES public.object_tree_schema (name)
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE public.feature_object_tree_mapping ADD CONSTRAINT object_tree_schema_feature_object_tree_mapping_fk
FOREIGN KEY (schema_name)
REFERENCES public.object_tree_schema (name)
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE public.map_set_object_tree_mapping ADD CONSTRAINT object_tree_schema_map_set_object_tree_mapping_fk
FOREIGN KEY (schema_name)
REFERENCES public.object_tree_schema (name)
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE public.map_object_tree_mapping ADD CONSTRAINT object_tree_schema_map_object_tree_mapping_fk
FOREIGN KEY (schema_name)
REFERENCES public.object_tree_schema (name)
ON UPDATE NO ACTION;

ALTER TABLE public.map_set_object_tree_mapping ADD CONSTRAINT object_tree_map_set_object_tree_mapping_fk
FOREIGN KEY (object_tree_id)
REFERENCES public.object_tree (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE public.map_object_tree_mapping ADD CONSTRAINT object_tree_map_object_tree_mapping_fk
FOREIGN KEY (object_tree_id)
REFERENCES public.object_tree (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE public.feature_object_tree_mapping ADD CONSTRAINT object_tree_feature_object_tree_mapping_fk
FOREIGN KEY (object_tree_id)
REFERENCES public.object_tree (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE public.run_map ADD CONSTRAINT feature_fitting_model_run_map_fk
FOREIGN KEY (peakel_fitting_model_id)
REFERENCES public.peakel_fitting_model (id)
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE public.run_map ADD CONSTRAINT peak_picking_software_run_map_fk
FOREIGN KEY (peak_picking_software_id)
REFERENCES public.peak_picking_software (id)
ON UPDATE NO ACTION;

ALTER TABLE public.feature_cluster_item ADD CONSTRAINT processed_map_feature_cluster_item_fk
FOREIGN KEY (processed_map_id)
REFERENCES public.processed_map (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.map_set ADD CONSTRAINT master_map_map_set_fk
FOREIGN KEY (master_map_id)
REFERENCES public.processed_map (id)
ON DELETE SET NULL
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE public.map_set ADD CONSTRAINT aln_reference_map_map_set_fk
FOREIGN KEY (aln_reference_map_id)
REFERENCES public.processed_map (id)
ON UPDATE NO ACTION;

ALTER TABLE public.map_alignment ADD CONSTRAINT from_map_map_alignment_fk
FOREIGN KEY (from_map_id)
REFERENCES public.processed_map (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE public.map_alignment ADD CONSTRAINT to_map_map_alignment_fk
FOREIGN KEY (to_map_id)
REFERENCES public.processed_map (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE public.map_layer ADD CONSTRAINT processed_map_map_layer_fk
FOREIGN KEY (processed_map_id)
REFERENCES public.processed_map (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE public.master_feature_item ADD CONSTRAINT processed_map_master_feature_item_fk
FOREIGN KEY (master_map_id)
REFERENCES public.processed_map (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.processed_map_feature_item ADD CONSTRAINT processed_map_processed_map_feature_item_fk
FOREIGN KEY (processed_map_id)
REFERENCES public.processed_map (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.processed_map_moz_calibration ADD CONSTRAINT processed_map_processed_map_moz_calibration_fk
FOREIGN KEY (processed_map_id)
REFERENCES public.processed_map (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.processed_map_run_map_mapping ADD CONSTRAINT processed_map_processed_map_run_map_mapping_fk
FOREIGN KEY (processed_map_id)
REFERENCES public.processed_map (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.processed_map ADD CONSTRAINT map_set_processed_map_fk
FOREIGN KEY (map_set_id)
REFERENCES public.map_set (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.map_layer ADD CONSTRAINT map_set_map_layer_fk
FOREIGN KEY (map_set_id)
REFERENCES public.map_set (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.map_alignment ADD CONSTRAINT map_set_map_alignment_fk
FOREIGN KEY (map_set_id)
REFERENCES public.map_set (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.map_set_object_tree_mapping ADD CONSTRAINT map_set_map_set_object_tree_mapping_fk
FOREIGN KEY (map_set_id)
REFERENCES public.map_set (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.feature ADD CONSTRAINT map_layer_feature_fk
FOREIGN KEY (map_layer_id)
REFERENCES public.map_layer (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE public.compound ADD CONSTRAINT map_layer_compound_fk
FOREIGN KEY (map_layer_id)
REFERENCES public.map_layer (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE public.theoretical_feature ADD CONSTRAINT map_layer_theoretical_feature_fk
FOREIGN KEY (map_layer_id)
REFERENCES public.map_layer (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE public.feature ADD CONSTRAINT theoretical_feature_feature_fk
FOREIGN KEY (theoretical_feature_id)
REFERENCES public.theoretical_feature (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE public.scan ADD CONSTRAINT run_scan_fk
FOREIGN KEY (run_id)
REFERENCES public.run (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE public.ms_picture ADD CONSTRAINT run_ms_picture_fk
FOREIGN KEY (run_id)
REFERENCES public.run (id)
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE public.run_map ADD CONSTRAINT run_run_map_fk
FOREIGN KEY (run_id)
REFERENCES public.run (id)
ON UPDATE NO ACTION;

ALTER TABLE public.feature_ms2_event ADD CONSTRAINT run_map_feature_ms2_scan_fk
FOREIGN KEY (run_map_id)
REFERENCES public.run_map (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE public.processed_map_run_map_mapping ADD CONSTRAINT run_map_processed_map_run_map_mapping_fk
FOREIGN KEY (run_map_id)
REFERENCES public.run_map (id)
ON UPDATE NO ACTION;

ALTER TABLE public.feature_overlap_mapping ADD CONSTRAINT run_map_feature_overlap_mapping_fk
FOREIGN KEY (map_id)
REFERENCES public.run_map (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.tile ADD CONSTRAINT ms_picture_tile_fk
FOREIGN KEY (ms_picture_id)
REFERENCES public.ms_picture (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.feature ADD CONSTRAINT first_scan_feature_fk
FOREIGN KEY (first_scan_id)
REFERENCES public.scan (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE public.feature ADD CONSTRAINT last_scan_feature_fk
FOREIGN KEY (last_scan_id)
REFERENCES public.scan (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE public.feature_ms2_event ADD CONSTRAINT scan_feature_ms2_event_fk
FOREIGN KEY (ms2_event_id)
REFERENCES public.scan (id)
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE public.processed_map_moz_calibration ADD CONSTRAINT scan_processed_map_moz_calibration_fk
FOREIGN KEY (scan_id)
REFERENCES public.scan (id)
ON UPDATE NO ACTION;

ALTER TABLE public.feature ADD CONSTRAINT apex_scan_feature_fk
FOREIGN KEY (apex_scan_id)
REFERENCES public.scan (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's update action (RESTRICT).
*/
ALTER TABLE public.feature_cluster_item ADD CONSTRAINT cluster_feature_feature_cluster_item_fk
FOREIGN KEY (cluster_feature_id)
REFERENCES public.feature (id)
ON DELETE NO ACTION;

ALTER TABLE public.feature_cluster_item ADD CONSTRAINT sub_feature_feature_cluster_item_fk
FOREIGN KEY (sub_feature_id)
REFERENCES public.feature (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE public.feature_ms2_event ADD CONSTRAINT feature_feature_ms2_event_fk
FOREIGN KEY (feature_id)
REFERENCES public.feature (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE public.master_feature_item ADD CONSTRAINT master_feature_master_feature_item_fk
FOREIGN KEY (master_feature_id)
REFERENCES public.feature (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE public.master_feature_item ADD CONSTRAINT child_feature_master_feature_item_fk
FOREIGN KEY (child_feature_id)
REFERENCES public.feature (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE public.processed_map_feature_item ADD CONSTRAINT feature_processed_map_feature_item_fk
FOREIGN KEY (feature_id)
REFERENCES public.feature (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE public.feature_object_tree_mapping ADD CONSTRAINT feature_feature_object_tree_mapping_fk
FOREIGN KEY (feature_id)
REFERENCES public.feature (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE public.feature_overlap_mapping ADD CONSTRAINT overlapping_feature_feature_overlap_mappping_fk
FOREIGN KEY (overlapping_feature_id)
REFERENCES public.feature (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE public.feature_overlap_mapping ADD CONSTRAINT overlapped_feature_feature_overlap_mappping_fk
FOREIGN KEY (overlapped_feature_id)
REFERENCES public.feature (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE public.compound ADD CONSTRAINT best_feature_compound_fk
FOREIGN KEY (best_feature_id)
REFERENCES public.feature (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE public.feature ADD CONSTRAINT compound_feature_fk
FOREIGN KEY (compound_id)
REFERENCES public.compound (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

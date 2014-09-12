CREATE TABLE cache (
                scope TEXT(250) NOT NULL,
                id INTEGER NOT NULL,
                format TEXT(50) NOT NULL,
                byte_order INTEGER NOT NULL,
                data BLOB NOT NULL,
                compression TEXT(20) NOT NULL,
                timestamp TEXT NOT NULL,
                serialized_properties TEXT,
                PRIMARY KEY (scope, id, format, byte_order)
);

CREATE TABLE compound (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                experimental_mass REAL NOT NULL,
                theoretical_mass REAL,
                elution_time REAL NOT NULL,
                formula TEXT(1000),
                serialized_properties TEXT,
                best_feature_id INTEGER NOT NULL,
                map_layer_id INTEGER,
                map_id INTEGER NOT NULL,
                FOREIGN KEY (map_layer_id) REFERENCES map_layer (id),
                FOREIGN KEY (map_id) REFERENCES map (id)
);

CREATE TABLE feature (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                moz REAL NOT NULL,
                charge INTEGER NOT NULL,
                elution_time REAL NOT NULL,
                apex_intensity REAL NOT NULL,
                area REAL NOT NULL,
                duration REAL NOT NULL,
                quality_score REAL,
                ms1_count INTEGER NOT NULL,
                ms2_count INTEGER NOT NULL,
                is_cluster TEXT NOT NULL,
                is_overlapping TEXT NOT NULL,
                serialized_properties TEXT,
                first_scan_id INTEGER NOT NULL,
                last_scan_id INTEGER NOT NULL,
                apex_scan_id INTEGER NOT NULL,
                theoretical_feature_id INTEGER,
                compound_id INTEGER,
                map_layer_id INTEGER,
                map_id INTEGER NOT NULL,
                FOREIGN KEY (first_scan_id) REFERENCES scan (id),
                FOREIGN KEY (last_scan_id) REFERENCES scan (id),
                FOREIGN KEY (apex_scan_id) REFERENCES scan (id),
                FOREIGN KEY (theoretical_feature_id) REFERENCES theoretical_feature (id),
                FOREIGN KEY (compound_id) REFERENCES compound (id),
                FOREIGN KEY (map_layer_id) REFERENCES map_layer (id),
                FOREIGN KEY (map_id) REFERENCES map (id)
);

CREATE TABLE feature_cluster_item (
                cluster_feature_id INTEGER NOT NULL,
                sub_feature_id INTEGER NOT NULL,
                processed_map_id INTEGER NOT NULL,
                PRIMARY KEY (cluster_feature_id, sub_feature_id),
                FOREIGN KEY (processed_map_id) REFERENCES processed_map (id)
);

CREATE TABLE feature_ms2_event (
                feature_id INTEGER NOT NULL,
                ms2_event_id INTEGER NOT NULL,
                run_map_id INTEGER NOT NULL,
                PRIMARY KEY (feature_id, ms2_event_id),
                FOREIGN KEY (run_map_id) REFERENCES raw_map (id)
);

CREATE TABLE feature_object_tree_mapping (
                feature_id INTEGER NOT NULL,
                schema_name TEXT(1000) NOT NULL,
                object_tree_id INTEGER NOT NULL,
                PRIMARY KEY (feature_id, schema_name),
                FOREIGN KEY (object_tree_id) REFERENCES object_tree (id)
);

CREATE TABLE feature_overlap_mapping (
                overlapped_feature_id INTEGER NOT NULL,
                overlapping_feature_id INTEGER NOT NULL,
                map_id INTEGER NOT NULL,
                PRIMARY KEY (overlapped_feature_id, overlapping_feature_id),
                FOREIGN KEY (map_id) REFERENCES raw_map (id)
);

CREATE TABLE feature_peakel_item (
                feature_id INTEGER NOT NULL,
                peakel_id INTEGER NOT NULL,
                index INTEGER NOT NULL,
                serialized_properties TEXT,
                map_id INTEGER NOT NULL,
                PRIMARY KEY (feature_id, peakel_id),
                FOREIGN KEY (map_id) REFERENCES map (id)
);

CREATE TABLE feature_scoring (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT(100) NOT NULL,
                description TEXT(1000),
                serialized_properties TEXT
);

CREATE TABLE instrument (
                id INTEGER NOT NULL,
                name TEXT(100) NOT NULL,
                source TEXT(100) NOT NULL,
                serialized_properties TEXT,
                PRIMARY KEY (id)
);

CREATE TABLE map (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT(1000) NOT NULL,
                description TEXT(10000),
                type INTEGER NOT NULL,
                creation_timestamp TEXT NOT NULL,
                modification_timestamp TEXT NOT NULL,
                serialized_properties TEXT,
                feature_scoring_id INTEGER NOT NULL,
                FOREIGN KEY (feature_scoring_id) REFERENCES feature_scoring (id)
);

CREATE TABLE map_alignment (
                from_map_id INTEGER NOT NULL,
                to_map_id INTEGER NOT NULL,
                mass_start REAL NOT NULL,
                mass_end REAL NOT NULL,
                time_list TEXT NOT NULL,
                delta_time_list TEXT NOT NULL,
                serialized_properties TEXT,
                map_set_id INTEGER NOT NULL,
                PRIMARY KEY (from_map_id, to_map_id, mass_start, mass_end),
                FOREIGN KEY (map_set_id) REFERENCES map_set (id)
);

CREATE TABLE map_layer (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                number INTEGER NOT NULL,
                name TEXT(250) NOT NULL,
                serialized_properties TEXT,
                processed_map_id INTEGER NOT NULL,
                map_set_id INTEGER NOT NULL,
                FOREIGN KEY (processed_map_id) REFERENCES processed_map (id),
                FOREIGN KEY (map_set_id) REFERENCES map_set (id)
);

CREATE TABLE map_object_tree_mapping (
                map_id INTEGER NOT NULL,
                schema_name TEXT(1000) NOT NULL,
                object_tree_id INTEGER NOT NULL,
                PRIMARY KEY (map_id, schema_name),
                FOREIGN KEY (object_tree_id) REFERENCES object_tree (id)
);

CREATE TABLE map_set (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT(250) NOT NULL,
                map_count INTEGER NOT NULL,
                creation_timestamp TEXT NOT NULL,
                serialized_properties TEXT,
                master_map_id INTEGER,
                aln_reference_map_id INTEGER,
                FOREIGN KEY (master_map_id) REFERENCES processed_map (id),
                FOREIGN KEY (aln_reference_map_id) REFERENCES processed_map (id)
);

CREATE TABLE map_set_object_tree_mapping (
                map_set_id INTEGER NOT NULL,
                schema_name TEXT(1000) NOT NULL,
                object_tree_id INTEGER NOT NULL,
                PRIMARY KEY (map_set_id, schema_name),
                FOREIGN KEY (object_tree_id) REFERENCES object_tree (id)
);

CREATE TABLE master_feature_item (
                master_feature_id INTEGER NOT NULL,
                child_feature_id INTEGER NOT NULL,
                is_best_child TEXT NOT NULL,
                master_map_id INTEGER NOT NULL,
                PRIMARY KEY (master_feature_id, child_feature_id),
                FOREIGN KEY (master_map_id) REFERENCES processed_map (id)
);

CREATE TABLE ms_picture (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                z_index INTEGER NOT NULL,
                moz_resolution REAL NOT NULL,
                time_resolution REAL NOT NULL,
                serialized_properties TEXT,
                run_id INTEGER NOT NULL,
                FOREIGN KEY (run_id) REFERENCES scan_sequence (id)
);

CREATE TABLE object_tree (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                blob_data BLOB,
                clob_data TEXT,
                serialized_properties TEXT,
                schema_name TEXT(1000) NOT NULL,
                FOREIGN KEY (schema_name) REFERENCES object_tree_schema (name)
);

CREATE TABLE object_tree_schema (
                name TEXT(1000) NOT NULL,
                type TEXT(50) NOT NULL,
                is_binary_mode TEXT NOT NULL,
                version TEXT(100) NOT NULL,
                schema TEXT NOT NULL,
                description TEXT(1000),
                serialized_properties TEXT,
                PRIMARY KEY (name)
);

CREATE TABLE peak_picking_software (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT(100) NOT NULL,
                version TEXT(100) NOT NULL,
                algorithm TEXT(250),
                serialized_properties TEXT
);

CREATE TABLE peakel (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                moz REAL NOT NULL,
                elution_time REAL NOT NULL,
                apex_intensity REAL NOT NULL,
                area REAL NOT NULL,
                duration REAL NOT NULL,
                fwhm REAL,
                is_overlapping TEXT NOT NULL,
                peaks_count INTEGER NOT NULL,
                peaks BLOB NOT NULL,
                serialized_properties TEXT,
                first_scan_id INTEGER NOT NULL,
                last_scan_id INTEGER NOT NULL,
                apex_scan_id INTEGER NOT NULL,
                map_id INTEGER NOT NULL,
                FOREIGN KEY (first_scan_id) REFERENCES scan (id),
                FOREIGN KEY (last_scan_id) REFERENCES scan (id),
                FOREIGN KEY (apex_scan_id) REFERENCES scan (id),
                FOREIGN KEY (map_id) REFERENCES map (id)
);

CREATE TABLE peakel_fitting_model (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT(100) NOT NULL,
                serialized_properties TEXT
);

CREATE TABLE processed_map (
                id INTEGER NOT NULL,
                number INTEGER NOT NULL,
                normalization_factor REAL NOT NULL,
                is_master TEXT NOT NULL,
                is_aln_reference TEXT NOT NULL,
                is_locked TEXT NOT NULL,
                map_set_id INTEGER NOT NULL,
                PRIMARY KEY (id),
                FOREIGN KEY (map_set_id) REFERENCES map_set (id)
);

CREATE TABLE processed_map_feature_item (
                processed_map_id INTEGER NOT NULL,
                feature_id INTEGER NOT NULL,
                calibrated_moz REAL NOT NULL,
                normalized_intensity REAL NOT NULL,
                corrected_elution_time REAL NOT NULL,
                is_clusterized TEXT NOT NULL,
                selection_level INTEGER NOT NULL,
                serialized_properties TEXT,
                PRIMARY KEY (processed_map_id, feature_id)
);

CREATE TABLE processed_map_moz_calibration (
                processed_map_id INTEGER NOT NULL,
                scan_id INTEGER NOT NULL,
                moz_list TEXT NOT NULL,
                delta_moz_list TEXT NOT NULL,
                serialized_properties TEXT,
                PRIMARY KEY (processed_map_id, scan_id)
);

CREATE TABLE processed_map_raw_map_mapping (
                processed_map_id INTEGER NOT NULL,
                raw_map_id INTEGER NOT NULL,
                PRIMARY KEY (processed_map_id, raw_map_id)
);

CREATE TABLE raw_map (
                id INTEGER NOT NULL,
                scan_sequence_id INTEGER NOT NULL,
                peak_picking_software_id INTEGER NOT NULL,
                peakel_fitting_model_id INTEGER,
                PRIMARY KEY (id),
                FOREIGN KEY (scan_sequence_id) REFERENCES scan_sequence (id),
                FOREIGN KEY (peak_picking_software_id) REFERENCES peak_picking_software (id),
                FOREIGN KEY (peakel_fitting_model_id) REFERENCES peakel_fitting_model (id)
);

CREATE TABLE scan (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                initial_id INTEGER NOT NULL,
                cycle INTEGER NOT NULL,
                time REAL NOT NULL,
                ms_level INTEGER NOT NULL,
                tic REAL NOT NULL,
                base_peak_moz REAL NOT NULL,
                base_peak_intensity REAL NOT NULL,
                precursor_moz REAL,
                precursor_charge INTEGER,
                serialized_properties TEXT,
                scan_sequence_id INTEGER NOT NULL,
                FOREIGN KEY (scan_sequence_id) REFERENCES scan_sequence (id)
);

CREATE TABLE scan_sequence (
                id INTEGER NOT NULL,
                raw_file_name TEXT(250) NOT NULL,
                min_intensity REAL,
                max_intensity REAL,
                ms1_scan_count INTEGER NOT NULL,
                ms2_scan_count INTEGER NOT NULL,
                serialized_properties TEXT,
                instrument_id INTEGER NOT NULL,
                PRIMARY KEY (id),
                FOREIGN KEY (instrument_id) REFERENCES instrument (id)
);

CREATE TABLE theoretical_feature (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                moz REAL NOT NULL,
                charge INTEGER NOT NULL,
                elution_time REAL NOT NULL,
                source_type TEXT(50) NOT NULL,
                serialized_properties TEXT,
                map_layer_id INTEGER,
                map_id INTEGER NOT NULL,
                FOREIGN KEY (map_layer_id) REFERENCES map_layer (id),
                FOREIGN KEY (map_id) REFERENCES map (id)
);

CREATE TABLE tile (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                x_index INTEGER NOT NULL,
                y_index INTEGER NOT NULL,
                begin_moz REAL NOT NULL,
                end_moz REAL NOT NULL,
                begin_time REAL NOT NULL,
                end_time REAL NOT NULL,
                width INTEGER NOT NULL,
                height INTEGER NOT NULL,
                intensities BLOB NOT NULL,
                serialized_properties TEXT,
                ms_picture_id INTEGER NOT NULL,
                FOREIGN KEY (ms_picture_id) REFERENCES ms_picture (id)
);

CREATE INDEX scan_scan_sequence_idx ON scan (scan_sequence_id);

CREATE INDEX scan_precursor_moz_idx ON scan (precursor_moz);

CREATE INDEX feature_map_idx ON feature (map_id);

CREATE INDEX feature_moz_time_charge_idx ON feature (moz,elution_time,charge);

CREATE INDEX feature_cluster_item_processed_map_idx ON feature_cluster_item (processed_map_id);

CREATE INDEX compound_map_idx ON compound (map_id);

CREATE INDEX processed_map_map_set_idx ON processed_map (map_set_id);

CREATE INDEX map_layer_processed_map_idx ON map_layer (processed_map_id);

CREATE INDEX map_layer_map_set_idx ON map_layer (map_set_id);

CREATE INDEX theoretical_feature_map_idx ON theoretical_feature (map_id);

CREATE INDEX map_alignment_map_set_idx ON map_alignment (map_set_id);

CREATE INDEX feature_ms2_event_run_map_idx ON feature_ms2_event (run_map_id);

CREATE INDEX master_feature_item_master_map_idx ON master_feature_item (master_map_id);

CREATE INDEX feature_overlap_mapping_map_idx ON feature_overlap_mapping (map_id);


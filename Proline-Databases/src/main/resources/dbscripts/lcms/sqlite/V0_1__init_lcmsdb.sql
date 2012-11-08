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
                map_layer_id INTEGER NOT NULL,
                map_id INTEGER NOT NULL,
                FOREIGN KEY (best_feature_id) REFERENCES feature (id),
                FOREIGN KEY (map_layer_id) REFERENCES map_layer (id),
                FOREIGN KEY (map_id) REFERENCES map (id)
);

CREATE TABLE feature (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                moz REAL NOT NULL,
                intensity REAL NOT NULL,
                charge INTEGER NOT NULL,
                elution_time REAL NOT NULL,
                quality_score REAL,
                ms1_count INTEGER NOT NULL,
                ms2_count INTEGER NOT NULL,
                is_cluster TEXT NOT NULL,
                is_overlapping TEXT NOT NULL,
                serialized_properties TEXT,
                first_scan_id INTEGER NOT NULL,
                last_scan_id INTEGER NOT NULL,
                apex_scan_id INTEGER NOT NULL,
                theoretical_feature_id INTEGER NOT NULL,
                compound_id INTEGER NOT NULL,
                map_layer_id INTEGER NOT NULL,
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
                FOREIGN KEY (run_map_id) REFERENCES run_map (id)
);

CREATE TABLE feature_object_tree_mapping (
                feature_id INTEGER NOT NULL,
                object_tree_id INTEGER NOT NULL,
                schema_name TEXT(1000) NOT NULL,
                PRIMARY KEY (feature_id, object_tree_id),
                FOREIGN KEY (schema_name) REFERENCES object_tree_schema (name)
);

CREATE TABLE feature_overlap_mapping (
                overlapped_feature_id INTEGER NOT NULL,
                overlapping_feature_id INTEGER NOT NULL,
                map_id INTEGER NOT NULL,
                PRIMARY KEY (overlapped_feature_id, overlapping_feature_id),
                FOREIGN KEY (map_id) REFERENCES run_map (id)
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
                name TEXT(250),
                serialized_properties TEXT,
                processed_map_id INTEGER NOT NULL,
                map_set_id INTEGER NOT NULL,
                FOREIGN KEY (processed_map_id) REFERENCES processed_map (id),
                FOREIGN KEY (map_set_id) REFERENCES map_set (id)
);

CREATE TABLE map_object_tree_mapping (
                id INTEGER NOT NULL,
                object_tree_id INTEGER NOT NULL,
                schema_name TEXT(1000) NOT NULL,
                PRIMARY KEY (id, object_tree_id),
                FOREIGN KEY (schema_name) REFERENCES object_tree_schema (name)
);

CREATE TABLE map_set (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT(250) NOT NULL,
                map_count INTEGER NOT NULL,
                creation_timestamp TEXT NOT NULL,
                serialized_properties TEXT,
                master_map_id INTEGER NOT NULL,
                aln_reference_map_id INTEGER NOT NULL,
                FOREIGN KEY (master_map_id) REFERENCES processed_map (id),
                FOREIGN KEY (aln_reference_map_id) REFERENCES processed_map (id)
);

CREATE TABLE map_set_object_tree_mapping (
                map_set_id INTEGER NOT NULL,
                object_tree_id INTEGER NOT NULL,
                schema_name TEXT(1000) NOT NULL,
                PRIMARY KEY (map_set_id, object_tree_id),
                FOREIGN KEY (schema_name) REFERENCES object_tree_schema (name)
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
                FOREIGN KEY (run_id) REFERENCES run (id)
);

CREATE TABLE object_tree (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                serialized_data TEXT NOT NULL,
                serialized_properties TEXT,
                schema_name TEXT(1000) NOT NULL,
                FOREIGN KEY (schema_name) REFERENCES object_tree_schema (name)
);

CREATE TABLE object_tree_schema (
                name TEXT(1000) NOT NULL,
                type TEXT(10) NOT NULL,
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

CREATE TABLE peakel_fitting_model (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT(100) NOT NULL,
                serialized_properties TEXT
);

CREATE TABLE processed_map (
                id INTEGER NOT NULL,
                number INTEGER NOT NULL,
                normalization_factor REAL,
                is_master TEXT NOT NULL,
                is_aln_reference TEXT NOT NULL,
                is_locked TEXT,
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

CREATE TABLE processed_map_run_map_mapping (
                processed_map_id INTEGER NOT NULL,
                run_map_id INTEGER NOT NULL,
                PRIMARY KEY (processed_map_id, run_map_id)
);

CREATE TABLE run (
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

CREATE TABLE run_map (
                id INTEGER NOT NULL,
                run_id INTEGER NOT NULL,
                peak_picking_software_id INTEGER NOT NULL,
                peakel_fitting_model_id INTEGER NOT NULL,
                PRIMARY KEY (id),
                FOREIGN KEY (run_id) REFERENCES run (id),
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
                run_id INTEGER NOT NULL,
                FOREIGN KEY (run_id) REFERENCES run (id)
);

CREATE TABLE theoretical_feature (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                moz REAL NOT NULL,
                charge INTEGER NOT NULL,
                elution_time REAL NOT NULL,
                source_type TEXT(50) NOT NULL,
                serialized_properties TEXT,
                map_layer_id INTEGER NOT NULL,
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


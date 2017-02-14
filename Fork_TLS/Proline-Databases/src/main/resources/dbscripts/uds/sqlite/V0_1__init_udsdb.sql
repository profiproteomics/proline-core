/* Last Update V0_6__core_0_4_0_UDS_data_migration (java) */
CREATE TABLE activation (
                type TEXT(100) NOT NULL,
                PRIMARY KEY (type)
);

CREATE TABLE admin_infos (
                model_version TEXT(50) NOT NULL,
                db_creation_date TEXT,
                model_update_date TEXT,
                configuration TEXT NOT NULL,
                PRIMARY KEY (model_version)
);

CREATE TABLE aggregation (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                child_nature TEXT NOT NULL
);

CREATE TABLE biological_group (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                number INTEGER NOT NULL,
                name TEXT(100) NOT NULL,
                serialized_properties TEXT,
                quantitation_id INTEGER NOT NULL,
                FOREIGN KEY (quantitation_id) REFERENCES data_set (id)
);

CREATE TABLE biological_group_biological_sample_item (
                biological_group_id INTEGER NOT NULL,
                biological_sample_id INTEGER NOT NULL,
                PRIMARY KEY (biological_group_id, biological_sample_id)
);

CREATE TABLE biological_sample (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                number INTEGER NOT NULL,
                name TEXT(100) NOT NULL,
                serialized_properties TEXT,
                quantitation_id INTEGER NOT NULL,
                FOREIGN KEY (quantitation_id) REFERENCES data_set (id)
);

CREATE TABLE biological_sample_sample_analysis_map (
                biological_sample_id INTEGER NOT NULL,
                sample_analysis_id INTEGER NOT NULL,
                sample_analysis_number INTEGER NOT NULL,
                PRIMARY KEY (biological_sample_id, sample_analysis_id)
);

CREATE TABLE data_set (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                number INTEGER NOT NULL,
                name TEXT NOT NULL,
                description TEXT(10000),
                type TEXT NOT NULL,
                keywords TEXT,
                creation_timestamp TEXT NOT NULL,
                modification_log TEXT,
                children_count INTEGER NOT NULL,
                serialized_properties TEXT,
                result_set_id INTEGER,
                result_summary_id INTEGER,
                aggregation_id INTEGER,
                fractionation_id INTEGER,
                quant_method_id INTEGER,
                parent_dataset_id INTEGER,
                project_id INTEGER NOT NULL,
                FOREIGN KEY (aggregation_id) REFERENCES aggregation (id),
                FOREIGN KEY (fractionation_id) REFERENCES fractionation (id),
                FOREIGN KEY (quant_method_id) REFERENCES quant_method (id),
                FOREIGN KEY (parent_dataset_id) REFERENCES data_set (id),
                FOREIGN KEY (project_id) REFERENCES project (id)
);

CREATE TABLE data_set_object_tree_map (
                data_set_id INTEGER NOT NULL,
                schema_name TEXT(1000) NOT NULL,
                object_tree_id INTEGER NOT NULL,
                PRIMARY KEY (data_set_id, schema_name),
                FOREIGN KEY (object_tree_id) REFERENCES object_tree (id)
);

CREATE TABLE document (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT(250) NOT NULL,
                description TEXT(1000),
                keywords TEXT(250),
                creation_timestamp TEXT NOT NULL,
                modification_timestamp TEXT,
                creation_log TEXT,
                modification_log TEXT,
                serialized_properties TEXT,
                object_tree_id INTEGER NOT NULL,
                virtual_folder_id INTEGER NOT NULL,
                project_id INTEGER NOT NULL,
                schema_name TEXT(1000) NOT NULL,
                FOREIGN KEY (object_tree_id) REFERENCES object_tree (id),
                FOREIGN KEY (virtual_folder_id) REFERENCES virtual_folder (id),
                FOREIGN KEY (project_id) REFERENCES project (id),
                FOREIGN KEY (schema_name) REFERENCES object_tree_schema (name)
);

CREATE TABLE enzyme (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT(100) NOT NULL,
                cleavage_regexp TEXT(50),
                is_independant TEXT NOT NULL,
                is_semi_specific TEXT NOT NULL,
                serialized_properties TEXT
);

CREATE TABLE enzyme_cleavage (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                site TEXT(6) NOT NULL,
                residues TEXT(20) NOT NULL,
                restrictive_residues TEXT(20),
                enzyme_id INTEGER NOT NULL,
                FOREIGN KEY (enzyme_id) REFERENCES enzyme (id)
);

CREATE TABLE external_db (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT(500) NOT NULL,
                connection_mode TEXT(50) NOT NULL,
                username TEXT(50),
                password TEXT(50),
                host TEXT(100),
                port INTEGER,
                type TEXT(100) NOT NULL,
                version TEXT(50) NOT NULL,
                is_busy TEXT NOT NULL,
                serialized_properties TEXT
);

CREATE TABLE fractionation (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                type TEXT NOT NULL
);

CREATE TABLE fragmentation_rule (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                description TEXT(1000),
                precursor_min_charge INTEGER,
                fragment_charge INTEGER,
                fragment_max_moz REAL,
                fragment_residue_constraint TEXT(20),
                required_series_quality_level TEXT(15),
                serialized_properties TEXT,
                fragment_series_id INTEGER,
                required_series_id INTEGER,
                FOREIGN KEY (fragment_series_id) REFERENCES fragmentation_series (id),
                FOREIGN KEY (required_series_id) REFERENCES fragmentation_series (id)
);

CREATE TABLE fragmentation_series (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT(9),
                neutral_loss TEXT(5),
                serialized_properties TEXT
);

CREATE TABLE group_setup (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                number INTEGER NOT NULL,
                name TEXT(100) NOT NULL,
                serialized_properties TEXT,
                quantitation_id INTEGER NOT NULL,
                FOREIGN KEY (quantitation_id) REFERENCES data_set (id)
);

CREATE TABLE group_setup_biological_group_map (
                group_setup_id INTEGER NOT NULL,
                biological_group_id INTEGER NOT NULL,
                PRIMARY KEY (group_setup_id, biological_group_id)
);

CREATE TABLE instrument (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT(100) NOT NULL,
                source TEXT(100) NOT NULL,
                serialized_properties TEXT
);

CREATE TABLE instrument_config (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT(100) NOT NULL,
                ms1_analyzer TEXT(100) NOT NULL,
                msn_analyzer TEXT(100),
                serialized_properties TEXT,
                instrument_id INTEGER NOT NULL,
                activation_type TEXT(100) NOT NULL,
                FOREIGN KEY (instrument_id) REFERENCES instrument (id),
                FOREIGN KEY (activation_type) REFERENCES activation (type)
);

CREATE TABLE instrument_config_fragmentation_rule_map (
                instrument_config_id INTEGER NOT NULL,
                fragmentation_rule_id INTEGER NOT NULL,
                PRIMARY KEY (instrument_config_id, fragmentation_rule_id)
);

CREATE TABLE master_quant_channel (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                number INTEGER NOT NULL,
                name TEXT(100) NOT NULL,
                serialized_properties TEXT,
                lcms_map_set_id INTEGER,
                quant_result_summary_id INTEGER,
                quantitation_id INTEGER NOT NULL,
                FOREIGN KEY (quantitation_id) REFERENCES data_set (id)
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
                version TEXT(100),
                schema TEXT NOT NULL,
                description TEXT(1000),
                serialized_properties TEXT,
                PRIMARY KEY (name)
);

CREATE TABLE peaklist_software (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT(100) NOT NULL,
                version TEXT(100),
                serialized_properties TEXT,
                spec_title_parsing_rule_id INTEGER NOT NULL,
                FOREIGN KEY (spec_title_parsing_rule_id) REFERENCES spec_title_parsing_rule (id)
);

CREATE TABLE project (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT(250) NOT NULL,
                description TEXT(1000),
                creation_timestamp TEXT NOT NULL,
                lock_expiration_timestamp TEXT,
                serialized_properties TEXT,
                lock_user_id INTEGER,
                owner_id INTEGER NOT NULL,
                FOREIGN KEY (lock_user_id) REFERENCES user_account (id),
                FOREIGN KEY (owner_id) REFERENCES user_account (id)
);

CREATE TABLE project_db_map (
                project_id INTEGER NOT NULL,
                external_db_id INTEGER NOT NULL,
                PRIMARY KEY (project_id, external_db_id)
);

CREATE TABLE project_user_account_map (
                project_id INTEGER NOT NULL,
                user_account_id INTEGER NOT NULL,
                write_permission TEXT NOT NULL,
                serialized_properties TEXT,
                PRIMARY KEY (project_id, user_account_id)
);

CREATE TABLE protein_match_decoy_rule (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT(100) NOT NULL,
                ac_decoy_tag TEXT(100) NOT NULL
);

CREATE TABLE quant_channel (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                number INTEGER NOT NULL,
                name TEXT(100),
                context_key TEXT(100) NOT NULL,
                serialized_properties TEXT,
                lcms_map_id INTEGER,
                ident_result_summary_id INTEGER NOT NULL,
                run_id INTEGER,
                quant_label_id INTEGER,
                sample_analysis_id INTEGER NOT NULL,
                biological_sample_id INTEGER NOT NULL,
                master_quant_channel_id INTEGER NOT NULL,
                quantitation_id INTEGER NOT NULL,
                FOREIGN KEY (run_id) REFERENCES run (id),
                FOREIGN KEY (quant_label_id) REFERENCES quant_label (id),
                FOREIGN KEY (sample_analysis_id) REFERENCES sample_analysis (id),
                FOREIGN KEY (biological_sample_id) REFERENCES biological_sample (id),
                FOREIGN KEY (master_quant_channel_id) REFERENCES master_quant_channel (id),
                FOREIGN KEY (quantitation_id) REFERENCES data_set (id)
);

CREATE TABLE quant_label (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                type TEXT(16) NOT NULL,
                name TEXT(10) NOT NULL,
                serialized_properties TEXT,
                quant_method_id INTEGER NOT NULL,
                FOREIGN KEY (quant_method_id) REFERENCES quant_method (id)
);

CREATE TABLE quant_method (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT(1000) NOT NULL,
                type TEXT(20) NOT NULL,
                abundance_unit TEXT(30) NOT NULL,
                serialized_properties TEXT
);

CREATE TABLE ratio_definition (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                number INTEGER NOT NULL,
                numerator_id INTEGER NOT NULL,
                denominator_id INTEGER NOT NULL,
                group_setup_id INTEGER NOT NULL,
                FOREIGN KEY (numerator_id) REFERENCES biological_group (id),
                FOREIGN KEY (denominator_id) REFERENCES biological_group (id),
                FOREIGN KEY (group_setup_id) REFERENCES group_setup (id)
);

CREATE TABLE raw_file (
                identifier TEXT(250) NOT NULL,
                raw_file_name TEXT(250) NOT NULL,
                raw_file_directory TEXT(500),
                mzdb_file_name TEXT(250),
                mzdb_file_directory TEXT(500),
                sample_name TEXT(250),
                creation_timestamp TEXT,
                serialized_properties TEXT,
                instrument_id INTEGER NOT NULL,
                owner_id INTEGER NOT NULL,
                PRIMARY KEY (identifier),
                FOREIGN KEY (instrument_id) REFERENCES instrument (id),
                FOREIGN KEY (owner_id) REFERENCES user_account (id)
);

CREATE TABLE raw_file_project_map (
                raw_file_identifier TEXT(250) NOT NULL,
                project_id INTEGER NOT NULL,
                serialized_properties TEXT,
                PRIMARY KEY (raw_file_identifier, project_id)
);

CREATE TABLE run (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                number INTEGER NOT NULL,
                run_start REAL NOT NULL,
                run_stop REAL NOT NULL,
                duration REAL NOT NULL,
                lc_method TEXT(250),
                ms_method TEXT(250),
                analyst TEXT(50),
                serialized_properties TEXT,
                raw_file_identifier TEXT(250) NOT NULL,
                FOREIGN KEY (raw_file_identifier) REFERENCES raw_file (identifier)
);

CREATE TABLE run_identification (
                id INTEGER NOT NULL,
                serialized_properties TEXT,
                run_id INTEGER,
                raw_file_identifier TEXT(250),
                PRIMARY KEY (id),
                FOREIGN KEY (run_id) REFERENCES run (id),
                FOREIGN KEY (raw_file_identifier) REFERENCES raw_file (identifier)
);

CREATE TABLE sample_analysis (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                serialized_properties TEXT,
                quantitation_id INTEGER NOT NULL,
                FOREIGN KEY (quantitation_id) REFERENCES data_set (id)
);

CREATE TABLE spec_title_parsing_rule (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                raw_file_identifier TEXT(100),
                first_cycle TEXT(100),
                last_cycle TEXT(100),
                first_scan TEXT(100),
                last_scan TEXT(100),
                first_time TEXT(100),
                last_time TEXT(100)
);

CREATE TABLE user_account (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                login TEXT(50) NOT NULL,
                password_hash TEXT NOT NULL,
                creation_mode TEXT(10) NOT NULL,
                serialized_properties TEXT
);

CREATE TABLE virtual_folder (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT(250) NOT NULL,
                path TEXT(500),
                serialized_properties TEXT,
                parent_virtual_folder_id INTEGER,
                project_id INTEGER NOT NULL,
                FOREIGN KEY (parent_virtual_folder_id) REFERENCES virtual_folder (id),
                FOREIGN KEY (project_id) REFERENCES project (id)
);

CREATE UNIQUE INDEX quant_channel_context_idx ON quant_channel (master_quant_channel_id,context_key,quant_label_id);

CREATE UNIQUE INDEX quant_channel_number_idx ON quant_channel (master_quant_channel_id,number);

CREATE INDEX object_tree_schema_name_idx ON object_tree (schema_name);

CREATE UNIQUE INDEX biological_group_number_idx ON biological_group (quantitation_id,number);

CREATE UNIQUE INDEX group_setup_number_idx ON group_setup (quantitation_id,number);

CREATE UNIQUE INDEX biological_sample_number_idx ON biological_sample (quantitation_id,number);

CREATE UNIQUE INDEX quant_method_name_idx ON quant_method (name);

CREATE UNIQUE INDEX ratio_definition_number_idx ON ratio_definition (group_setup_id,number);

CREATE UNIQUE INDEX master_quant_channel_number_idx ON master_quant_channel (quantitation_id,number);

CREATE UNIQUE INDEX project_name_owner_idx ON project (name,owner_id);

CREATE UNIQUE INDEX user_account_login_idx ON user_account (login);

CREATE UNIQUE INDEX instrument_config_name_idx ON instrument_config (name);

CREATE UNIQUE INDEX instrument_idx ON instrument (name,source);

CREATE UNIQUE INDEX enzyme_name_idx ON enzyme (name);

CREATE UNIQUE INDEX peaklist_software_idx ON peaklist_software (name,version);

CREATE UNIQUE INDEX fractionation_type_idx ON fractionation (type);

CREATE UNIQUE INDEX aggregation_child_nature_idx ON aggregation (child_nature);

CREATE UNIQUE INDEX biological_sample_sample_analysis_map_idx ON biological_sample_sample_analysis_map (biological_sample_id,sample_analysis_number);


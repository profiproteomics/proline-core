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

CREATE TABLE biological_group (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                number INTEGER NOT NULL,
                name TEXT(100) NOT NULL,
                serialized_properties TEXT,
                group_setup_id INTEGER NOT NULL,
                FOREIGN KEY (group_setup_id) REFERENCES group_setup (id)
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
                FOREIGN KEY (quantitation_id) REFERENCES quantitation (id)
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
                is_semi_specific TEXT NOT NULL
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

CREATE TABLE fragmentation_rule (
                id INTEGER NOT NULL,
                description TEXT(1000),
                precursor_min_charge INTEGER,
                fragment_charge INTEGER,
                fragment_max_moz REAL,
                fragment_residue_constraint TEXT(20),
                required_serie_quality_level TEXT(15),
                serialized_properties TEXT,
                theoretical_fragment_id INTEGER,
                required_serie_id INTEGER,
                PRIMARY KEY (id),
                FOREIGN KEY (theoretical_fragment_id) REFERENCES theoretical_fragment (id),
                FOREIGN KEY (required_serie_id) REFERENCES theoretical_fragment (id)
);

CREATE TABLE group_setup (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT(100) NOT NULL,
                serialized_properties TEXT,
                quantitation_id INTEGER NOT NULL,
                FOREIGN KEY (quantitation_id) REFERENCES quantitation (id)
);

CREATE TABLE identification (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                number INTEGER NOT NULL,
                name TEXT(100) NOT NULL,
                description TEXT(10000),
                keywords TEXT(250),
                creation_timestamp TEXT NOT NULL,
                modification_log TEXT,
                fractionation_type TEXT(10),
                fraction_count INTEGER NOT NULL,
                serialized_properties TEXT,
                active_summary_id INTEGER,
                project_id INTEGER NOT NULL,
                FOREIGN KEY (active_summary_id) REFERENCES identification_summary (id),
                FOREIGN KEY (project_id) REFERENCES project (id)
);

CREATE TABLE identification_fraction (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                number INTEGER NOT NULL,
                serialized_properties TEXT,
                result_set_id INTEGER NOT NULL,
                identification_id INTEGER NOT NULL,
                run_id INTEGER NOT NULL,
                raw_file_name TEXT(250) NOT NULL,
                FOREIGN KEY (identification_id) REFERENCES identification (id),
                FOREIGN KEY (run_id) REFERENCES run (id),
                FOREIGN KEY (raw_file_name) REFERENCES raw_file (name)
);

CREATE TABLE identification_fraction_summary (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                serialized_properties TEXT,
                result_summary_id INTEGER NOT NULL,
                identification_fraction_id INTEGER NOT NULL,
                identification_summary_id INTEGER NOT NULL,
                FOREIGN KEY (identification_fraction_id) REFERENCES identification_fraction (id),
                FOREIGN KEY (identification_summary_id) REFERENCES identification_summary (id)
);

CREATE TABLE identification_summary (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                number INTEGER NOT NULL,
                serialized_properties TEXT,
                result_summary_id INTEGER NOT NULL,
                identification_id INTEGER NOT NULL,
                FOREIGN KEY (identification_id) REFERENCES identification (id)
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
                version TEXT(100),
                schema TEXT NOT NULL,
                description TEXT(1000),
                serialized_properties TEXT,
                PRIMARY KEY (name)
);

CREATE TABLE peaklist_software (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT(100) NOT NULL,
                version TEXT(100) NOT NULL,
                serialized_properties TEXT NOT NULL,
                spec_title_parsing_rule_id INTEGER NOT NULL,
                FOREIGN KEY (spec_title_parsing_rule_id) REFERENCES spec_title_parsing_rule (id)
);

CREATE TABLE project (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT(250) NOT NULL,
                description TEXT(1000),
                creation_timestamp TEXT NOT NULL,
                serialized_properties TEXT,
                owner_id INTEGER NOT NULL,
                FOREIGN KEY (owner_id) REFERENCES user_account (id)
);

CREATE TABLE project_db_map (
                external_db_id INTEGER NOT NULL,
                project_id INTEGER NOT NULL,
                PRIMARY KEY (external_db_id, project_id)
);

CREATE TABLE project_user_account_map (
                project_id INTEGER NOT NULL,
                user_account_id INTEGER NOT NULL,
                serialized_properties TEXT,
                PRIMARY KEY (project_id, user_account_id)
);

CREATE TABLE protein_match_decoy_rule (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT(100) NOT NULL,
                ac_decoy_tag TEXT(100) NOT NULL
);

CREATE TABLE quant_channel (
                id INTEGER NOT NULL,
                context_key TEXT(100) NOT NULL,
                name TEXT(100) NOT NULL,
                serialized_properties TEXT,
                lcms_map_id INTEGER,
                ident_result_summary_id INTEGER NOT NULL,
                quant_result_summary_id INTEGER,
                quant_label_id INTEGER,
                sample_analysis_replicate_id INTEGER NOT NULL,
                biological_sample_id INTEGER NOT NULL,
                quantitation_fraction_id INTEGER NOT NULL,
                quantitation_id INTEGER NOT NULL,
                PRIMARY KEY (id),
                FOREIGN KEY (quant_label_id) REFERENCES quant_label (id),
                FOREIGN KEY (sample_analysis_replicate_id) REFERENCES sample_analysis_replicate (id),
                FOREIGN KEY (biological_sample_id) REFERENCES biological_sample (id),
                FOREIGN KEY (quantitation_fraction_id) REFERENCES quantitation_fraction (id),
                FOREIGN KEY (quantitation_id) REFERENCES quantitation (id)
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

CREATE TABLE quantitation (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                number INTEGER NOT NULL,
                name TEXT(100) NOT NULL,
                description TEXT(1000),
                keywords TEXT(250),
                creation_timestamp TEXT NOT NULL,
                modification_log TEXT,
                fraction_count INTEGER NOT NULL,
                fractionation_type TEXT(10),
                serialized_properties TEXT,
                quant_method_id INTEGER NOT NULL,
                project_id INTEGER NOT NULL,
                FOREIGN KEY (quant_method_id) REFERENCES quant_method (id),
                FOREIGN KEY (project_id) REFERENCES project (id)
);

CREATE TABLE quantitation_fraction (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                number INTEGER NOT NULL,
                name TEXT(100) NOT NULL,
                serialized_properties TEXT,
                lcms_map_set_id INTEGER,
                quant_result_summary_id INTEGER,
                quantitation_id INTEGER NOT NULL,
                FOREIGN KEY (quantitation_id) REFERENCES quantitation (id)
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
                name TEXT(250) NOT NULL,
                extension TEXT(10) NOT NULL,
                directory TEXT(500),
                creation_timestamp TEXT,
                instrument_id INTEGER NOT NULL,
                owner_id INTEGER NOT NULL,
                PRIMARY KEY (name),
                FOREIGN KEY (instrument_id) REFERENCES instrument (id),
                FOREIGN KEY (owner_id) REFERENCES user_account (id)
);

CREATE TABLE run (
                id INTEGER NOT NULL,
                number INTEGER NOT NULL,
                run_start REAL NOT NULL,
                run_stop REAL NOT NULL,
                duration REAL NOT NULL,
                lc_method TEXT(250),
                ms_method TEXT(250),
                analyst TEXT(50),
                serialized_properties TEXT,
                raw_file_name TEXT(250) NOT NULL,
                PRIMARY KEY (id),
                FOREIGN KEY (raw_file_name) REFERENCES raw_file (name)
);

CREATE TABLE sample_analysis_replicate (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                number INTEGER NOT NULL,
                serialized_properties TEXT,
                biological_sample_id INTEGER NOT NULL,
                quantitation_id INTEGER NOT NULL,
                FOREIGN KEY (biological_sample_id) REFERENCES biological_sample (id),
                FOREIGN KEY (quantitation_id) REFERENCES quantitation (id)
);

CREATE TABLE spec_title_parsing_rule (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                raw_file_name TEXT(100),
                first_cycle TEXT(100),
                last_cycle TEXT(100),
                first_scan TEXT(100),
                last_scan TEXT(100),
                first_time TEXT(100),
                last_time TEXT(100),
                name TEXT(100) NOT NULL
);

CREATE TABLE theoretical_fragment (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                type TEXT(9),
                neutral_loss TEXT(5),
                serialized_properties TEXT
);

CREATE TABLE user_account (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                login TEXT(50) NOT NULL,
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


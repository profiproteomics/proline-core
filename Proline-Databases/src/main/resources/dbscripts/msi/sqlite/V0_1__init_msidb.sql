CREATE TABLE admin_infos (
                model_version TEXT(1000) NOT NULL,
                db_creation_date TEXT,
                model_update_date TEXT,
                PRIMARY KEY (model_version)
);

CREATE TABLE bio_sequence (
                id INTEGER NOT NULL,
                alphabet TEXT(3) NOT NULL,
                sequence TEXT NOT NULL,
                length INTEGER NOT NULL,
                mass REAL NOT NULL,
                pi REAL NOT NULL,
                crc64 TEXT(32) NOT NULL,
                serialized_properties TEXT,
                PRIMARY KEY (id)
);

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

CREATE TABLE consensus_spectrum (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                precursor_charge INTEGER NOT NULL,
                precursor_calculated_moz REAL NOT NULL,
                normalized_elution_time REAL,
                is_artificial TEXT NOT NULL,
                creation_mode TEXT(10) NOT NULL,
                serialized_properties TEXT,
                spectrum_id INTEGER NOT NULL,
                peptide_id INTEGER NOT NULL,
                FOREIGN KEY (spectrum_id) REFERENCES spectrum (id),
                FOREIGN KEY (peptide_id) REFERENCES peptide (id)
);

CREATE TABLE enzyme (
                id INTEGER NOT NULL,
                name TEXT(100) NOT NULL,
                cleavage_regexp TEXT(50),
                is_independant TEXT NOT NULL,
                is_semi_specific TEXT NOT NULL,
                serialized_properties TEXT,
                PRIMARY KEY (id)
);

CREATE TABLE instrument_config (
                id INTEGER NOT NULL,
                name TEXT(100) NOT NULL,
                ms1_analyzer TEXT(100) NOT NULL,
                msn_analyzer TEXT(100),
                serialized_properties TEXT,
                PRIMARY KEY (id)
);

CREATE TABLE ion_search (
                id INTEGER NOT NULL,
                max_protein_mass REAL,
                min_protein_mass REAL,
                protein_pi REAL,
                PRIMARY KEY (id)
);

CREATE TABLE master_quant_component (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                selection_level INTEGER NOT NULL,
                serialized_properties TEXT,
                object_tree_id INTEGER NOT NULL,
                schema_name TEXT(1000) NOT NULL,
                result_summary_id INTEGER NOT NULL,
                FOREIGN KEY (object_tree_id) REFERENCES object_tree (id),
                FOREIGN KEY (schema_name) REFERENCES object_tree_schema (name),
                FOREIGN KEY (result_summary_id) REFERENCES result_summary (id)
);

CREATE TABLE master_quant_peptide_ion (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                charge INTEGER,
                moz REAL NOT NULL,
                elution_time REAL NOT NULL,
                scan_number INTEGER,
                serialized_properties TEXT,
                lcms_feature_id INTEGER,
                peptide_id INTEGER,
                peptide_instance_id INTEGER,
                master_quant_peptide_id INTEGER NOT NULL,
                master_quant_component_id INTEGER NOT NULL,
                best_peptide_match_id INTEGER,
                unmodified_peptide_ion_id INTEGER,
                result_summary_id INTEGER NOT NULL,
                FOREIGN KEY (peptide_id) REFERENCES peptide (id),
                FOREIGN KEY (peptide_instance_id) REFERENCES peptide_instance (id),
                FOREIGN KEY (master_quant_peptide_id) REFERENCES master_quant_component (id),
                FOREIGN KEY (master_quant_component_id) REFERENCES master_quant_component (id),
                FOREIGN KEY (best_peptide_match_id) REFERENCES peptide_match (id),
                FOREIGN KEY (unmodified_peptide_ion_id) REFERENCES master_quant_peptide_ion (id),
                FOREIGN KEY (result_summary_id) REFERENCES result_summary (id)
);

CREATE TABLE master_quant_reporter_ion (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                serialized_properties TEXT,
                master_quant_component_id INTEGER NOT NULL,
                ms_query_id INTEGER NOT NULL,
                master_quant_peptide_ion_id INTEGER NOT NULL,
                result_summary_id INTEGER NOT NULL,
                FOREIGN KEY (master_quant_component_id) REFERENCES master_quant_component (id),
                FOREIGN KEY (ms_query_id) REFERENCES ms_query (id),
                FOREIGN KEY (master_quant_peptide_ion_id) REFERENCES master_quant_peptide_ion (id),
                FOREIGN KEY (result_summary_id) REFERENCES result_summary (id)
);

CREATE TABLE ms_query (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                initial_id INTEGER NOT NULL,
                charge INTEGER NOT NULL,
                moz REAL NOT NULL,
                serialized_properties TEXT,
                spectrum_id INTEGER,
                msi_search_id INTEGER NOT NULL,
                FOREIGN KEY (spectrum_id) REFERENCES spectrum (id),
                FOREIGN KEY (msi_search_id) REFERENCES msi_search (id)
);

CREATE TABLE msi_search (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT(1000),
                date TEXT,
                result_file_name TEXT(256) NOT NULL,
                result_file_directory TEXT(1000),
                job_number INTEGER,
                user_name TEXT(100),
                user_email TEXT(100),
                queries_count INTEGER,
                submitted_queries_count INTEGER NOT NULL,
                searched_sequences_count INTEGER,
                serialized_properties TEXT,
                search_settings_id INTEGER NOT NULL,
                peaklist_id INTEGER NOT NULL,
                FOREIGN KEY (search_settings_id) REFERENCES search_settings (id),
                FOREIGN KEY (peaklist_id) REFERENCES peaklist (id)
);

CREATE TABLE msi_search_object_tree_map (
                msi_search_id INTEGER NOT NULL,
                object_tree_id INTEGER NOT NULL,
                schema_name TEXT(1000) NOT NULL,
                PRIMARY KEY (msi_search_id, object_tree_id),
                FOREIGN KEY (schema_name) REFERENCES object_tree_schema (name)
);

CREATE TABLE msms_search (
                id INTEGER NOT NULL,
                fragment_charge_states TEXT(100),
                fragment_mass_error_tolerance REAL NOT NULL,
                fragment_mass_error_tolerance_unit TEXT(3) NOT NULL,
                PRIMARY KEY (id)
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

CREATE TABLE peaklist (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                type TEXT(100),
                path TEXT(1000),
                raw_file_name TEXT(250),
                ms_level INTEGER NOT NULL,
                spectrum_data_compression TEXT(20) NOT NULL,
                serialized_properties TEXT,
                peaklist_software_id INTEGER NOT NULL,
                FOREIGN KEY (peaklist_software_id) REFERENCES peaklist_software (id)
);

CREATE TABLE peaklist_relation (
                parent_peaklist_id INTEGER NOT NULL,
                child_peaklist_id INTEGER NOT NULL,
                PRIMARY KEY (parent_peaklist_id, child_peaklist_id)
);

CREATE TABLE peaklist_software (
                id INTEGER NOT NULL,
                name TEXT(100) NOT NULL,
                version TEXT(100),
                serialized_properties TEXT,
                PRIMARY KEY (id)
);

CREATE TABLE peptide (
                id INTEGER NOT NULL,
                sequence TEXT NOT NULL,
                ptm_string TEXT,
                calculated_mass REAL NOT NULL,
                serialized_properties TEXT,
                PRIMARY KEY (id)
);

CREATE TABLE peptide_instance (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                peptide_match_count INTEGER NOT NULL,
                protein_match_count INTEGER NOT NULL,
                protein_set_count INTEGER NOT NULL,
                validated_protein_set_count INTEGER NOT NULL,
                total_leaves_match_count INTEGER NOT NULL,
                selection_level INTEGER NOT NULL,
                elution_time REAL,
                serialized_properties TEXT,
                best_peptide_match_id INTEGER NOT NULL,
                peptide_id INTEGER NOT NULL,
                unmodified_peptide_id INTEGER,
                master_quant_component_id INTEGER,
                result_summary_id INTEGER NOT NULL,
                FOREIGN KEY (best_peptide_match_id) REFERENCES peptide_match (id),
                FOREIGN KEY (peptide_id) REFERENCES peptide (id),
                FOREIGN KEY (unmodified_peptide_id) REFERENCES peptide (id),
                FOREIGN KEY (master_quant_component_id) REFERENCES master_quant_component (id),
                FOREIGN KEY (result_summary_id) REFERENCES result_summary (id)
);

CREATE TABLE peptide_instance_peptide_match_map (
                peptide_instance_id INTEGER NOT NULL,
                peptide_match_id INTEGER NOT NULL,
                serialized_properties TEXT,
                result_summary_id INTEGER NOT NULL,
                PRIMARY KEY (peptide_instance_id, peptide_match_id),
                FOREIGN KEY (result_summary_id) REFERENCES result_summary (id)
);

CREATE TABLE peptide_match (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                charge INTEGER NOT NULL,
                experimental_moz REAL NOT NULL,
                score REAL,
                rank INTEGER,
                delta_moz REAL,
                missed_cleavage INTEGER NOT NULL,
                fragment_match_count INTEGER,
                is_decoy TEXT NOT NULL,
                serialized_properties TEXT,
                peptide_id INTEGER NOT NULL,
                ms_query_id INTEGER NOT NULL,
                best_child_id INTEGER,
                scoring_id INTEGER NOT NULL,
                result_set_id INTEGER NOT NULL,
                FOREIGN KEY (peptide_id) REFERENCES peptide (id),
                FOREIGN KEY (ms_query_id) REFERENCES ms_query (id),
                FOREIGN KEY (best_child_id) REFERENCES peptide_match (id),
                FOREIGN KEY (scoring_id) REFERENCES scoring (id),
                FOREIGN KEY (result_set_id) REFERENCES result_set (id)
);

CREATE TABLE peptide_match_object_tree_map (
                peptide_match_id INTEGER NOT NULL,
                object_tree_id INTEGER NOT NULL,
                schema_name TEXT(1000) NOT NULL,
                PRIMARY KEY (peptide_match_id, object_tree_id),
                FOREIGN KEY (schema_name) REFERENCES object_tree_schema (name)
);

CREATE TABLE peptide_match_relation (
                parent_peptide_match_id INTEGER NOT NULL,
                child_peptide_match_id INTEGER NOT NULL,
                parent_result_set_id INTEGER NOT NULL,
                PRIMARY KEY (parent_peptide_match_id, child_peptide_match_id),
                FOREIGN KEY (parent_result_set_id) REFERENCES result_set (id)
);

CREATE TABLE peptide_set (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                is_subset TEXT,
                score REAL NOT NULL,
                peptide_count INTEGER,
                peptide_match_count INTEGER,
                serialized_properties TEXT,
                protein_set_id INTEGER,
                scoring_id INTEGER NOT NULL,
                result_summary_id INTEGER NOT NULL,
                FOREIGN KEY (protein_set_id) REFERENCES protein_set (id),
                FOREIGN KEY (scoring_id) REFERENCES scoring (id),
                FOREIGN KEY (result_summary_id) REFERENCES result_summary (id)
);

CREATE TABLE peptide_set_peptide_instance_item (
                peptide_set_id INTEGER NOT NULL,
                peptide_instance_id INTEGER NOT NULL,
                is_best_peptide_set TEXT,
                selection_level INTEGER NOT NULL,
                serialized_properties TEXT,
                result_summary_id INTEGER NOT NULL,
                PRIMARY KEY (peptide_set_id, peptide_instance_id),
                FOREIGN KEY (result_summary_id) REFERENCES result_summary (id)
);

CREATE TABLE peptide_set_protein_match_map (
                peptide_set_id INTEGER NOT NULL,
                protein_match_id INTEGER NOT NULL,
                result_summary_id INTEGER NOT NULL,
                PRIMARY KEY (peptide_set_id, protein_match_id),
                FOREIGN KEY (result_summary_id) REFERENCES result_summary (id)
);

CREATE TABLE peptide_set_relation (
                peptide_overset_id INTEGER NOT NULL,
                peptide_subset_id INTEGER NOT NULL,
                is_strict_subset TEXT NOT NULL,
                result_summary_id INTEGER NOT NULL,
                PRIMARY KEY (peptide_overset_id, peptide_subset_id),
                FOREIGN KEY (result_summary_id) REFERENCES result_summary (id)
);

CREATE TABLE protein_match (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                accession TEXT(100) NOT NULL,
                description TEXT(10000),
                gene_name TEXT(100),
                score REAL,
                coverage REAL NOT NULL,
                peptide_count INTEGER NOT NULL,
                peptide_match_count INTEGER,
                is_decoy TEXT NOT NULL,
                is_last_bio_sequence TEXT NOT NULL,
                serialized_properties TEXT,
                taxon_id INTEGER,
                bio_sequence_id INTEGER,
                scoring_id INTEGER NOT NULL,
                result_set_id INTEGER NOT NULL,
                FOREIGN KEY (bio_sequence_id) REFERENCES bio_sequence (id),
                FOREIGN KEY (scoring_id) REFERENCES scoring (id),
                FOREIGN KEY (result_set_id) REFERENCES result_set (id)
);

CREATE TABLE protein_match_seq_database_map (
                protein_match_id INTEGER NOT NULL,
                seq_database_id INTEGER NOT NULL,
                result_set_id INTEGER NOT NULL,
                PRIMARY KEY (protein_match_id, seq_database_id),
                FOREIGN KEY (result_set_id) REFERENCES result_set (id)
);

CREATE TABLE protein_set (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                is_validated TEXT NOT NULL,
                selection_level INTEGER NOT NULL,
                serialized_properties TEXT,
                typical_protein_match_id INTEGER NOT NULL,
                master_quant_component_id INTEGER,
                result_summary_id INTEGER NOT NULL,
                FOREIGN KEY (typical_protein_match_id) REFERENCES protein_match (id),
                FOREIGN KEY (master_quant_component_id) REFERENCES master_quant_component (id),
                FOREIGN KEY (result_summary_id) REFERENCES result_summary (id)
);

CREATE TABLE protein_set_object_tree_map (
                protein_set_id INTEGER NOT NULL,
                object_tree_id INTEGER NOT NULL,
                schema_name TEXT(1000) NOT NULL,
                PRIMARY KEY (protein_set_id, object_tree_id),
                FOREIGN KEY (schema_name) REFERENCES object_tree_schema (name)
);

CREATE TABLE protein_set_protein_match_item (
                protein_set_id INTEGER NOT NULL,
                protein_match_id INTEGER NOT NULL,
                serialized_properties TEXT,
                result_summary_id INTEGER NOT NULL,
                PRIMARY KEY (protein_set_id, protein_match_id),
                FOREIGN KEY (result_summary_id) REFERENCES result_summary (id)
);

CREATE TABLE ptm_specificity (
                id INTEGER NOT NULL,
                location TEXT(14) NOT NULL,
                residue TEXT(1),
                serialized_properties TEXT,
                PRIMARY KEY (id)
);

CREATE TABLE result_set (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT(1000),
                description TEXT(10000),
                type TEXT(50) NOT NULL,
                creation_log TEXT,
                modification_timestamp TEXT NOT NULL,
                serialized_properties TEXT,
                decoy_result_set_id INTEGER,
                msi_search_id INTEGER,
                FOREIGN KEY (decoy_result_set_id) REFERENCES result_set (id),
                FOREIGN KEY (msi_search_id) REFERENCES msi_search (id)
);

CREATE TABLE result_set_object_tree_map (
                result_set_id INTEGER NOT NULL,
                object_tree_id INTEGER NOT NULL,
                schema_name TEXT(1000) NOT NULL,
                PRIMARY KEY (result_set_id, object_tree_id),
                FOREIGN KEY (schema_name) REFERENCES object_tree_schema (name)
);

CREATE TABLE result_set_relation (
                parent_result_set_id INTEGER NOT NULL,
                child_result_set_id INTEGER NOT NULL,
                PRIMARY KEY (parent_result_set_id, child_result_set_id)
);

CREATE TABLE result_summary (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                description TEXT(10000),
                creation_log TEXT,
                modification_timestamp TEXT NOT NULL,
                is_quantified TEXT,
                serialized_properties TEXT,
                decoy_result_summary_id INTEGER,
                result_set_id INTEGER NOT NULL,
                FOREIGN KEY (decoy_result_summary_id) REFERENCES result_summary (id),
                FOREIGN KEY (result_set_id) REFERENCES result_set (id)
);

CREATE TABLE result_summary_object_tree_map (
                result_summary_id INTEGER NOT NULL,
                object_tree_id INTEGER NOT NULL,
                schema_name TEXT(1000) NOT NULL,
                PRIMARY KEY (result_summary_id, object_tree_id),
                FOREIGN KEY (schema_name) REFERENCES object_tree_schema (name)
);

CREATE TABLE result_summary_relation (
                parent_result_summary_id INTEGER NOT NULL,
                child_result_summary_id INTEGER NOT NULL,
                PRIMARY KEY (parent_result_summary_id, child_result_summary_id)
);

CREATE TABLE scoring (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                search_engine TEXT(100) NOT NULL,
                name TEXT(100) NOT NULL,
                description TEXT(1000),
                serialized_properties TEXT
);

CREATE TABLE search_settings (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                software_name TEXT(1000),
                software_version TEXT(1000),
                taxonomy TEXT(1000),
                max_missed_cleavages INTEGER,
                peptide_charge_states TEXT(100),
                peptide_mass_error_tolerance REAL,
                peptide_mass_error_tolerance_unit TEXT(3),
                quantitation TEXT(100),
                is_decoy TEXT NOT NULL,
                serialized_properties TEXT,
                instrument_config_id INTEGER NOT NULL,
                FOREIGN KEY (instrument_config_id) REFERENCES instrument_config (id)
);

CREATE TABLE search_settings_seq_database_map (
                search_settings_id INTEGER NOT NULL,
                seq_database_id INTEGER NOT NULL,
                searched_sequences_count INTEGER NOT NULL,
                serialized_properties TEXT,
                PRIMARY KEY (search_settings_id, seq_database_id)
);

CREATE TABLE seq_database (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT(100) NOT NULL,
                fasta_file_path TEXT(500) NOT NULL,
                version TEXT(100),
                release_date TEXT NOT NULL,
                sequence_count INTEGER,
                serialized_properties TEXT
);

CREATE TABLE sequence_match (
                protein_match_id INTEGER NOT NULL,
                peptide_id INTEGER NOT NULL,
                start INTEGER NOT NULL,
                stop INTEGER NOT NULL,
                residue_before TEXT(1),
                residue_after TEXT(1),
                is_decoy TEXT NOT NULL,
                serialized_properties TEXT,
                best_peptide_match_id INTEGER NOT NULL,
                result_set_id INTEGER NOT NULL,
                PRIMARY KEY (protein_match_id, peptide_id, start, stop),
                FOREIGN KEY (best_peptide_match_id) REFERENCES peptide_match (id),
                FOREIGN KEY (result_set_id) REFERENCES result_set (id)
);

CREATE TABLE spectrum (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT(1024) NOT NULL,
                precursor_moz REAL,
                precursor_intensity REAL,
                precursor_charge INTEGER,
                is_summed TEXT,
                first_cycle INTEGER,
                last_cycle INTEGER,
                first_scan INTEGER,
                last_scan INTEGER,
                first_time REAL,
                last_time REAL,
                moz_list BLOB,
                intensity_list BLOB,
                peak_count INTEGER NOT NULL,
                serialized_properties TEXT,
                peaklist_id INTEGER NOT NULL,
                instrument_config_id INTEGER NOT NULL,
                FOREIGN KEY (peaklist_id) REFERENCES peaklist (id),
                FOREIGN KEY (instrument_config_id) REFERENCES instrument_config (id)
);

CREATE TABLE used_enzyme (
                search_settings_id INTEGER NOT NULL,
                enzyme_id INTEGER NOT NULL,
                PRIMARY KEY (search_settings_id, enzyme_id)
);

CREATE TABLE used_ptm (
                search_settings_id INTEGER NOT NULL,
                ptm_specificity_id INTEGER NOT NULL,
                short_name TEXT(100) NOT NULL,
                is_fixed TEXT NOT NULL,
                PRIMARY KEY (search_settings_id, ptm_specificity_id)
);

CREATE INDEX sequence_match_pep_idx ON sequence_match (peptide_id);

CREATE INDEX sequence_match_prot_match_idx ON sequence_match (protein_match_id);

CREATE INDEX sequence_match_rs_idx ON sequence_match (result_set_id ASC);

CREATE INDEX peptide_match_relation_rs_idx ON peptide_match_relation (parent_result_set_id ASC);

CREATE INDEX peptide_set_rsm_idx ON peptide_set (result_summary_id ASC);

CREATE INDEX bio_sequence_crc_idx ON bio_sequence (crc64);

CREATE INDEX protein_set_rsm_idx ON protein_set (result_summary_id ASC);

CREATE INDEX protein_match_ac_idx ON protein_match (accession);

CREATE INDEX protein_match_seq_idx ON protein_match (bio_sequence_id);

CREATE INDEX protein_match_rs_idx ON protein_match (result_set_id ASC);

CREATE INDEX ms_query_search_idx ON ms_query (msi_search_id ASC);

CREATE INDEX spectrum_pkl_idx ON spectrum (peaklist_id ASC);

CREATE UNIQUE INDEX peptide_seq_ptm_idx ON peptide (sequence,ptm_string);

CREATE INDEX peptide_mass_idx ON peptide (calculated_mass);

CREATE INDEX peptide_match_ms_query_idx ON peptide_match (ms_query_id);

CREATE INDEX peptide_match_peptide_idx ON peptide_match (peptide_id);

CREATE INDEX peptide_match_rs_idx ON peptide_match (result_set_id ASC);

CREATE INDEX peptide_set_relation_rsm_idx ON peptide_set_relation (result_summary_id ASC);

CREATE INDEX cache_scope_idx ON cache (scope);

CREATE INDEX peptide_instance_rsm_idx ON peptide_instance (result_summary_id ASC);

CREATE INDEX master_quant_component_rsm_idx ON master_quant_component (result_summary_id ASC);

CREATE INDEX master_quant_peptide_ion_peptide_idx ON master_quant_peptide_ion (peptide_id);

CREATE INDEX master_quant_peptide_ion_rsm_idx ON master_quant_peptide_ion (result_summary_id ASC);

CREATE INDEX pep_set_pep_inst_item_rsm_idx ON peptide_set_peptide_instance_item (result_summary_id ASC);

CREATE INDEX prot_set_prot_match_item_rsm_idx ON protein_set_protein_match_item (result_summary_id ASC);

CREATE INDEX pep_inst_pep_match_map_rsm_idx ON peptide_instance_peptide_match_map (result_summary_id ASC);

CREATE INDEX pep_set_prot_match_map_rsm_idx ON peptide_set_protein_match_map (result_summary_id ASC);

CREATE INDEX prot_match_seq_db_map_rs_idx ON protein_match_seq_database_map (result_set_id ASC);

CREATE INDEX master_quant_reporter_ion_rsm_idx ON master_quant_reporter_ion (result_summary_id ASC);


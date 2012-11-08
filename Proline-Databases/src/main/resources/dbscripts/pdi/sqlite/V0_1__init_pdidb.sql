CREATE TABLE admin_infos (
                model_version TEXT(1000) NOT NULL,
                db_creation_date TEXT,
                model_update_date TEXT,
                chr_location_update_timestamp TEXT,
                serialized_properties TEXT,
                PRIMARY KEY (model_version)
);

CREATE TABLE bio_sequence (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                alphabet TEXT(3) NOT NULL,
                sequence TEXT NOT NULL,
                length INTEGER,
                mass REAL NOT NULL,
                pi REAL,
                crc64 TEXT(32) NOT NULL,
                serialized_properties TEXT
);

CREATE TABLE bio_sequence_annotation (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                version TEXT(50) NOT NULL,
                serialized_properties TEXT,
                bio_sequence_id INTEGER NOT NULL,
                taxon_id INTEGER NOT NULL,
                object_tree_id INTEGER NOT NULL,
                schema_name TEXT(1000) NOT NULL,
                FOREIGN KEY (bio_sequence_id) REFERENCES bio_sequence (id),
                FOREIGN KEY (taxon_id) REFERENCES taxon (id),
                FOREIGN KEY (object_tree_id) REFERENCES object_tree (id),
                FOREIGN KEY (schema_name) REFERENCES object_tree_schema (name)
);

CREATE TABLE bio_sequence_gene_map (
                bio_sequence_id INTEGER NOT NULL,
                gene_id INTEGER NOT NULL,
                serialized_properties TEXT,
                taxon_id INTEGER NOT NULL,
                PRIMARY KEY (bio_sequence_id, gene_id),
                FOREIGN KEY (taxon_id) REFERENCES taxon (id)
);

CREATE TABLE bio_sequence_relation (
                na_sequence_id INTEGER NOT NULL,
                aa_sequence_id INTEGER NOT NULL,
                frame_number INTEGER NOT NULL,
                PRIMARY KEY (na_sequence_id, aa_sequence_id)
);

CREATE TABLE chromosome_location (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                chromosome_identifier TEXT(10) NOT NULL,
                location TEXT(250),
                serialized_properties TEXT,
                gene_id INTEGER NOT NULL,
                taxon_id INTEGER NOT NULL,
                FOREIGN KEY (gene_id) REFERENCES gene (id),
                FOREIGN KEY (taxon_id) REFERENCES taxon (id)
);

CREATE TABLE fasta_file_entry_index (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                block_start INTEGER NOT NULL,
                block_length INTEGER NOT NULL,
                serialized_properties TEXT,
                bio_sequence_id INTEGER NOT NULL,
                seq_db_entry_id INTEGER NOT NULL,
                seq_db_instance_id INTEGER NOT NULL,
                FOREIGN KEY (bio_sequence_id) REFERENCES bio_sequence (id),
                FOREIGN KEY (seq_db_entry_id) REFERENCES seq_db_entry (id),
                FOREIGN KEY (seq_db_instance_id) REFERENCES seq_db_instance (id)
);

CREATE TABLE fasta_parsing_rule (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                db_type TEXT(100),
                entry_id TEXT(100) NOT NULL,
                entry_ac TEXT(100),
                entry_name TEXT(100) NOT NULL,
                gene_name TEXT(100),
                organism_name TEXT(100),
                taxon_id TEXT(100)
);

CREATE TABLE gene (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT(100) NOT NULL,
                synonyms TEXT,
                orf_names TEXT,
                is_active TEXT NOT NULL,
                serialized_properties TEXT,
                taxon_id INTEGER NOT NULL,
                FOREIGN KEY (taxon_id) REFERENCES taxon (id)
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

CREATE TABLE protein_identifier (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                value TEXT(30) NOT NULL,
                is_ac_number TEXT NOT NULL,
                is_active TEXT NOT NULL,
                serialized_properties TEXT,
                bio_sequence_id INTEGER NOT NULL,
                taxon_id INTEGER NOT NULL,
                seq_db_config_id INTEGER NOT NULL,
                FOREIGN KEY (bio_sequence_id) REFERENCES bio_sequence (id),
                FOREIGN KEY (taxon_id) REFERENCES taxon (id),
                FOREIGN KEY (seq_db_config_id) REFERENCES seq_db_config (id)
);

CREATE TABLE seq_db_config (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT(100) NOT NULL,
                alphabet TEXT(3) NOT NULL,
                ref_entry_format TEXT(10) NOT NULL,
                serialized_properties TEXT,
                fasta_parsing_rule_id INTEGER NOT NULL,
                is_native TEXT NOT NULL,
                FOREIGN KEY (fasta_parsing_rule_id) REFERENCES fasta_parsing_rule (id)
);

CREATE TABLE seq_db_entry (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                identifier TEXT(50) NOT NULL,
                name TEXT(1000) NOT NULL,
                version TEXT(100),
                ref_file_block_start INTEGER,
                ref_file_block_length INTEGER,
                is_active TEXT NOT NULL,
                serialized_properties TEXT,
                bio_sequence_id INTEGER NOT NULL,
                taxon_id INTEGER NOT NULL,
                seq_db_instance_id INTEGER NOT NULL,
                seq_db_config_id INTEGER NOT NULL,
                FOREIGN KEY (bio_sequence_id) REFERENCES bio_sequence (id),
                FOREIGN KEY (taxon_id) REFERENCES taxon (id),
                FOREIGN KEY (seq_db_instance_id) REFERENCES seq_db_instance (id),
                FOREIGN KEY (seq_db_config_id) REFERENCES seq_db_config (id)
);

CREATE TABLE seq_db_entry_gene_map (
                seq_db_entry_id INTEGER NOT NULL,
                gene_id INTEGER NOT NULL,
                seq_db_instance_id INTEGER NOT NULL,
                PRIMARY KEY (seq_db_entry_id, gene_id),
                FOREIGN KEY (seq_db_instance_id) REFERENCES seq_db_instance (id)
);

CREATE TABLE seq_db_entry_object_tree_map (
                seq_db_entry_id INTEGER NOT NULL,
                object_tree_id INTEGER NOT NULL,
                schema_name TEXT(1000) NOT NULL,
                PRIMARY KEY (seq_db_entry_id, object_tree_id),
                FOREIGN KEY (schema_name) REFERENCES object_tree_schema (name)
);

CREATE TABLE seq_db_entry_protein_identifier_map (
                seq_db_entry_id INTEGER NOT NULL,
                protein_identifier_id INTEGER NOT NULL,
                seq_db_instance_id INTEGER NOT NULL,
                PRIMARY KEY (seq_db_entry_id, protein_identifier_id),
                FOREIGN KEY (seq_db_instance_id) REFERENCES seq_db_instance (id)
);

CREATE TABLE seq_db_instance (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                fasta_file_path TEXT(500) NOT NULL,
                ref_file_path TEXT(500),
                is_indexed TEXT NOT NULL,
                is_deleted TEXT NOT NULL,
                revision INTEGER NOT NULL,
                creation_timestamp TEXT NOT NULL,
                sequence_count INTEGER NOT NULL,
                residue_count INTEGER,
                serialized_properties TEXT,
                seq_db_release_id INTEGER,
                seq_db_config_id INTEGER NOT NULL,
                FOREIGN KEY (seq_db_release_id) REFERENCES seq_db_release (id),
                FOREIGN KEY (seq_db_config_id) REFERENCES seq_db_config (id)
);

CREATE TABLE seq_db_release (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date TEXT(50) NOT NULL,
                version TEXT(10),
                serialized_properties TEXT
);

CREATE TABLE taxon (
                id INTEGER NOT NULL,
                scientific_name TEXT(512) NOT NULL,
                rank TEXT(30) NOT NULL,
                serialized_properties TEXT,
                parent_taxon_id INTEGER NOT NULL,
                PRIMARY KEY (id),
                FOREIGN KEY (parent_taxon_id) REFERENCES taxon (id)
);

CREATE TABLE taxon_extra_name (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                class TEXT(256) NOT NULL,
                value TEXT(512) NOT NULL,
                serialized_properties TEXT,
                taxon_id INTEGER NOT NULL,
                FOREIGN KEY (taxon_id) REFERENCES taxon (id)
);


CREATE TABLE admin_infos (
                model_version TEXT(1000) NOT NULL,
                db_creation_date TEXT,
                model_update_date TEXT,
                PRIMARY KEY (model_version)
);

CREATE TABLE atom_label (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT(100) NOT NULL,
                symbol TEXT(2) NOT NULL,
                mono_mass REAL NOT NULL,
                average_mass REAL NOT NULL,
                serialized_properties TEXT
);

CREATE TABLE peptide (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sequence TEXT NOT NULL,
                ptm_string TEXT,
                calculated_mass REAL NOT NULL,
                serialized_properties TEXT,
                atom_label_id INTEGER,
                FOREIGN KEY (atom_label_id) REFERENCES atom_label (id)
);

CREATE TABLE peptide_ptm (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                seq_position INTEGER NOT NULL,
                mono_mass REAL,
                average_mass REAL,
                serialized_properties TEXT,
                peptide_id INTEGER NOT NULL,
                ptm_specificity_id INTEGER NOT NULL,
                atom_label_id INTEGER,
                FOREIGN KEY (peptide_id) REFERENCES peptide (id),
                FOREIGN KEY (ptm_specificity_id) REFERENCES ptm_specificity (id),
                FOREIGN KEY (atom_label_id) REFERENCES atom_label (id)
);

CREATE TABLE peptide_ptm_insert_status (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                is_ok TEXT NOT NULL,
                peptide_id INTEGER NOT NULL,
                FOREIGN KEY (peptide_id) REFERENCES peptide (id)
);

CREATE TABLE ptm (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                unimod_id INTEGER,
                full_name TEXT(1000),
                short_name TEXT(100) NOT NULL,
                serialized_properties TEXT
);

CREATE TABLE ptm_classification (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT(1000) NOT NULL
);

CREATE TABLE ptm_evidence (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                type TEXT(14) NOT NULL,
                is_required TEXT NOT NULL,
                composition TEXT(50) NOT NULL,
                mono_mass REAL NOT NULL,
                average_mass REAL NOT NULL,
                serialized_properties TEXT,
                specificity_id INTEGER,
                ptm_id INTEGER,
                FOREIGN KEY (specificity_id) REFERENCES ptm_specificity (id),
                FOREIGN KEY (ptm_id) REFERENCES ptm (id)
);

CREATE TABLE ptm_specificity (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                location TEXT(14) NOT NULL,
                residue TEXT(1),
                serialized_properties TEXT,
                ptm_id INTEGER NOT NULL,
                classification_id INTEGER NOT NULL,
                FOREIGN KEY (ptm_id) REFERENCES ptm (id),
                FOREIGN KEY (classification_id) REFERENCES ptm_classification (id)
);

CREATE UNIQUE INDEX peptide_sequence_ptm_idx ON peptide (sequence,ptm_string);

CREATE INDEX peptide_mass_idx ON peptide (calculated_mass);

CREATE INDEX peptide_ptm_peptide_idx ON peptide_ptm (peptide_id);
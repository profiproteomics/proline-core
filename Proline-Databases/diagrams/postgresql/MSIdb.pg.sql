
CREATE SEQUENCE public.scoring_scoring_id_seq;

CREATE TABLE public.scoring (
                scoring_id INTEGER NOT NULL DEFAULT nextval('public.scoring_scoring_id_seq'),
                search_engine VARCHAR(100) NOT NULL,
                name VARCHAR(100) NOT NULL,
                description VARCHAR(1000),
                serialized_properties OTHER,
                CONSTRAINT scoring_pk PRIMARY KEY (scoring_id)
);
COMMENT ON TABLE public.scoring IS 'UNIQUE(search_engine,name)';
COMMENT ON COLUMN public.scoring.search_engine IS 'mascot, omssa, x!tandem, meta (when scoring performed by an extra algorithm)';
COMMENT ON COLUMN public.scoring.name IS 'The name of the computed score.';


ALTER SEQUENCE public.scoring_scoring_id_seq OWNED BY public.scoring.scoring_id;

CREATE TABLE public.cache (
                scope VARCHAR(250) NOT NULL,
                id INTEGER NOT NULL,
                format VARCHAR(50) NOT NULL,
                byte_order INTEGER NOT NULL,
                data BYTEA NOT NULL,
                compression VARCHAR(20) NOT NULL,
                timestamp INTEGER NOT NULL,
                CONSTRAINT cache_pk PRIMARY KEY (scope, id, format, byte_order)
);
COMMENT ON COLUMN public.cache.format IS 'examples: perl.storable java.serializable json';
COMMENT ON COLUMN public.cache.compression IS 'none, zlib, lzma, snappy';


CREATE SEQUENCE public.peaklist_software_peaklist_software_id_seq;

CREATE TABLE public.peaklist_software (
                peaklist_software_id INTEGER NOT NULL DEFAULT nextval('public.peaklist_software_peaklist_software_id_seq'),
                name VARCHAR(100) NOT NULL,
                version VARCHAR(100) NOT NULL,
                serialized_properties OTHER,
                CONSTRAINT peaklist_software_pk PRIMARY KEY (peaklist_software_id)
);
COMMENT ON TABLE public.peaklist_software IS 'UNIQUE( name, version )';
COMMENT ON COLUMN public.peaklist_software.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


ALTER SEQUENCE public.peaklist_software_peaklist_software_id_seq OWNED BY public.peaklist_software.peaklist_software_id;

CREATE SEQUENCE public.theoretical_fragment_theoretical_fragment_id_seq;

CREATE TABLE public.theoretical_fragment (
                theoretical_fragment_id INTEGER NOT NULL DEFAULT nextval('public.theoretical_fragment_theoretical_fragment_id_seq'),
                type VARCHAR(9),
                neutral_loss VARCHAR(5),
                serialized_properties OTHER,
                CONSTRAINT theoretical_fragment_pk PRIMARY KEY (theoretical_fragment_id)
);
COMMENT ON COLUMN public.theoretical_fragment.type IS 'MUST be one of : a b c d v w x y z z+1 z+2 ya yb immonium precursor';
COMMENT ON COLUMN public.theoretical_fragment.neutral_loss IS 'must be one of H2O, NH3, H3PO4';


ALTER SEQUENCE public.theoretical_fragment_theoretical_fragment_id_seq OWNED BY public.theoretical_fragment.theoretical_fragment_id;

CREATE SEQUENCE public.spec_title_parsing_rule_id_seq;

CREATE TABLE public.spec_title_parsing_rule (
                id INTEGER NOT NULL DEFAULT nextval('public.spec_title_parsing_rule_id_seq'),
                name VARCHAR(100) NOT NULL,
                raw_file_name VARCHAR(100),
                first_cycle VARCHAR(100),
                last_cycle VARCHAR(100),
                first_scan VARCHAR(100),
                last_scan VARCHAR(100),
                last_time VARCHAR(100),
                first_time VARCHAR(100),
                CONSTRAINT spec_title_parsing_rule_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.spec_title_parsing_rule IS 'Describe rules used to parse the content of the MS2 spectrum description. Note: using the attribute names of  the spectrum table enables an easier implementation.';
COMMENT ON COLUMN public.spec_title_parsing_rule.name IS 'The name of the rule. The name of th script used to generate the peaklist may be used but there is no restriction. Examples: extract_msn, Mascot Distiller, mascot.dll';


ALTER SEQUENCE public.spec_title_parsing_rule_id_seq OWNED BY public.spec_title_parsing_rule.id;

CREATE SEQUENCE public.seq_database_seq_database_id_seq;

CREATE TABLE public.seq_database (
                seq_database_id INTEGER NOT NULL DEFAULT nextval('public.seq_database_seq_database_id_seq'),
                name VARCHAR(100) NOT NULL,
                file_path VARCHAR(500) NOT NULL,
                version VARCHAR(100),
                release_date VARCHAR(8),
                sequence_count INTEGER,
                serialized_properties OTHER,
                CONSTRAINT seq_database_pk PRIMARY KEY (seq_database_id)
);
COMMENT ON TABLE public.seq_database IS 'The database used in the MSI search';
COMMENT ON COLUMN public.seq_database.name IS 'The name of the database.';
COMMENT ON COLUMN public.seq_database.file_path IS 'The path to the file containing the sequences. MUST BE UNIQUE';
COMMENT ON COLUMN public.seq_database.version IS 'The version of the database';
COMMENT ON COLUMN public.seq_database.release_date IS 'The release date of the database. Format is yyyy/mm/dd ex: 20081231';
COMMENT ON COLUMN public.seq_database.sequence_count IS 'The number of sequences contained in the database.';


ALTER SEQUENCE public.seq_database_seq_database_id_seq OWNED BY public.seq_database.seq_database_id;

CREATE TABLE public.activation (
                type VARCHAR(100) NOT NULL,
                CONSTRAINT activation_pk PRIMARY KEY (type)
);
COMMENT ON TABLE public.activation IS 'The activation type used for fragmentation';
COMMENT ON COLUMN public.activation.type IS 'HCD CID ETD...';


CREATE SEQUENCE public.ptm_classification_ptm_classification_id_seq;

CREATE TABLE public.ptm_classification (
                ptm_classification_id INTEGER NOT NULL DEFAULT nextval('public.ptm_classification_ptm_classification_id_seq'),
                name VARCHAR(1000) NOT NULL,
                CONSTRAINT ptm_classification_pk PRIMARY KEY (ptm_classification_id)
);
COMMENT ON TABLE public.ptm_classification IS 'See UNIMOD help';


ALTER SEQUENCE public.ptm_classification_ptm_classification_id_seq OWNED BY public.ptm_classification.ptm_classification_id;

CREATE SEQUENCE public.atom_label_atom_label_id_seq;

CREATE TABLE public.atom_label (
                atom_label_id INTEGER NOT NULL DEFAULT nextval('public.atom_label_atom_label_id_seq'),
                name VARCHAR(100) NOT NULL,
                symbol VARCHAR(2) NOT NULL,
                mono_mass DOUBLE PRECISION NOT NULL,
                average_mass DOUBLE PRECISION NOT NULL,
                serialized_properties OTHER,
                CONSTRAINT atom_label_pk PRIMARY KEY (atom_label_id)
);
COMMENT ON TABLE public.atom_label IS 'Enables the description of 14N/15N and 16O/18O labeling.';
COMMENT ON COLUMN public.atom_label.name IS 'The name of the label. EX: 15N';
COMMENT ON COLUMN public.atom_label.symbol IS 'The symbol of the atom. EX: N';


ALTER SEQUENCE public.atom_label_atom_label_id_seq OWNED BY public.atom_label.atom_label_id;

CREATE SEQUENCE public.instrument_instrument_id_seq;

CREATE TABLE public.instrument (
                instrument_id INTEGER NOT NULL DEFAULT nextval('public.instrument_instrument_id_seq'),
                name VARCHAR(100) NOT NULL,
                source VARCHAR(100) NOT NULL,
                serialized_properties OTHER,
                CONSTRAINT instrument_pk PRIMARY KEY (instrument_id)
);
COMMENT ON TABLE public.instrument IS 'name and source must be unique';


ALTER SEQUENCE public.instrument_instrument_id_seq OWNED BY public.instrument.instrument_id;

CREATE SEQUENCE public.instrument_config_instrument_config_id_seq;

CREATE TABLE public.instrument_config (
                instrument_config_id INTEGER NOT NULL DEFAULT nextval('public.instrument_config_instrument_config_id_seq'),
                name VARCHAR(100) NOT NULL,
                ms1_analyzer VARCHAR(100) NOT NULL,
                msn_analyzer VARCHAR(100),
                serialized_properties OTHER,
                instrument_id INTEGER NOT NULL,
                activation_type VARCHAR(100) NOT NULL,
                CONSTRAINT instrument_config_pk PRIMARY KEY (instrument_config_id)
);
COMMENT ON COLUMN public.instrument_config.name IS 'MUST BE UNIQUE';


ALTER SEQUENCE public.instrument_config_instrument_config_id_seq OWNED BY public.instrument_config.instrument_config_id;

CREATE TABLE public.fragmentation_rule (
                fragmentation_rule_id INTEGER NOT NULL,
                description VARCHAR(1000),
                precursor_min_charge INTEGER,
                fragment_charge INTEGER,
                fragment_max_moz REAL,
                fragment_residue_constraint VARCHAR(20),
                required_serie_quality_level VARCHAR(15),
                serialized_properties OTHER,
                theoretical_fragment_id INTEGER NOT NULL,
                required_serie_id INTEGER NOT NULL,
                CONSTRAINT fragmentation_rule_pk PRIMARY KEY (fragmentation_rule_id)
);
COMMENT ON TABLE public.fragmentation_rule IS 'Each instrument can have one or more of  fragment ion / rules';
COMMENT ON COLUMN public.fragmentation_rule.description IS '# 1  # singly charged  # 2  # doubly charged if precursor 2+ or higher #      (not internal or immonium)  # 3  # doubly charged if precursor 3+ or higher #      (not internal or immonium)  # 4  # immonium # 5  # a series # 6  # a - NH3 if a significant and fragment includes RKNQ # 7  # a - H2O if a significant and fragment includes STED # 8  # b series # 9  # b - NH3 if b significant and fragment includes RKNQ # 10 # b - H2O if b significant and fragment includes STED # 11 # c series # 12 # x series # 13 # y series # 14 # y - NH3 if y significant and fragment includes RKNQ # 15 # y - H2O if y significant and fragment includes STED # 16 # z series # 17 # internal yb < 700 Da # 18 # internal ya < 700 Da # 19 # y or y++ must be significant # 20 # y or y++ must be highest scoring series # 21 # z+1 series # 22 # d and d'' series # 23 # v series # 24 # w and w'' series # 25 # z+2 series';
COMMENT ON COLUMN public.fragmentation_rule.fragment_residue_constraint IS 'The fragment must contain one of the residues described here. y-NH3  => RKNQ y-H2O => STED';
COMMENT ON COLUMN public.fragmentation_rule.required_serie_quality_level IS 'significant|highest_scoring';


CREATE TABLE public.instrument_config_fragmentation_rule_map (
                instrument_config_id INTEGER NOT NULL,
                fragmentation_rule_id INTEGER NOT NULL,
                CONSTRAINT instrument_config_fragmentation_rule_map_pk PRIMARY KEY (instrument_config_id, fragmentation_rule_id)
);


CREATE SEQUENCE public.peptide_peptide_id_seq;

CREATE TABLE public.peptide (
                peptide_id INTEGER NOT NULL DEFAULT nextval('public.peptide_peptide_id_seq'),
                sequence OTHER NOT NULL,
                ptm_string OTHER,
                is_ptm_insert_ok BOOLEAN NOT NULL,
                calculated_mass DOUBLE PRECISION NOT NULL,
                serialized_properties OTHER,
                atom_label_id INTEGER NOT NULL,
                CONSTRAINT peptide_pk PRIMARY KEY (peptide_id)
);
COMMENT ON TABLE public.peptide IS 'A peptide is an amino acid (AA) sequence with given PTMs. A peptide has a unique pair of sequence/PTM string.';
COMMENT ON COLUMN public.peptide.sequence IS 'The AA sequence of this peptide';
COMMENT ON COLUMN public.peptide.ptm_string IS 'A string that describes the ptm structure. EX : MENHIR with oxidation (M) and SILAC label (R) 1[O]7[C(-9) 13C(9)] Each ptm is described by its delta composition. The prefix number gives the position of ptm on the peptide. The atomic number MUST be explicited for non natural isotope only (EX: 15N) . The number of added (or removed) atoms MUST be specified ONLY if more than one atom is concerned. Must be also defined for atom labeling (EX: N(-1) 15N).';
COMMENT ON COLUMN public.peptide.is_ptm_insert_ok IS 'This value helps to know the status of insertion of related PTMs. When peptide and peptide_ptm tables are filled in separate transactions (i.e. when using BULK insert) this information is really needed to avoid data corruption.';
COMMENT ON COLUMN public.peptide.calculated_mass IS 'The theoretical mass of the peptide.';
COMMENT ON COLUMN public.peptide.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


ALTER SEQUENCE public.peptide_peptide_id_seq OWNED BY public.peptide.peptide_id;

CREATE SEQUENCE public.search_settings_search_settings_id_seq;

CREATE TABLE public.search_settings (
                search_settings_id INTEGER NOT NULL DEFAULT nextval('public.search_settings_search_settings_id_seq'),
                software_name VARCHAR(1000),
                software_version VARCHAR(1000),
                taxonomy VARCHAR(1000),
                max_missed_cleavages INTEGER,
                peptide_charge_states VARCHAR(100),
                peptide_mass_error_tolerance DOUBLE PRECISION,
                peptide_mass_error_tolerance_unit VARCHAR(3),
                quantitation VARCHAR(100),
                is_decoy BOOLEAN NOT NULL,
                serialized_properties OTHER,
                instrument_config_id INTEGER NOT NULL,
                CONSTRAINT search_settings_pk PRIMARY KEY (search_settings_id)
);
COMMENT ON TABLE public.search_settings IS 'The settings used in a given MSI search';
COMMENT ON COLUMN public.search_settings.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


ALTER SEQUENCE public.search_settings_search_settings_id_seq OWNED BY public.search_settings.search_settings_id;

CREATE TABLE public.search_settings_seq_database_map (
                search_settings_id INTEGER NOT NULL,
                seq_database_id INTEGER NOT NULL,
                searched_sequences_count INTEGER NOT NULL,
                serialized_properties OTHER,
                CONSTRAINT search_settings_seq_database_map_pk PRIMARY KEY (search_settings_id, seq_database_id)
);


CREATE TABLE public.sample (
                sample_id INTEGER NOT NULL,
                name VARCHAR(50) NOT NULL,
                project_name VARCHAR(50),
                study_name VARCHAR(50),
                CONSTRAINT sample_pk PRIMARY KEY (sample_id)
);


CREATE SEQUENCE public.run_run_id_seq;

CREATE TABLE public.run (
                run_id INTEGER NOT NULL DEFAULT nextval('public.run_run_id_seq'),
                run_owner VARCHAR(50),
                analyst VARCHAR(50),
                raw_file_name VARCHAR(255) NOT NULL,
                raw_file_directory VARCHAR(1024),
                lc_method VARCHAR(50),
                ms_method VARCHAR(50),
                run_date DATE,
                duration REAL,
                run_start REAL,
                run_stop REAL,
                sample_id INTEGER NOT NULL,
                CONSTRAINT run_pk PRIMARY KEY (run_id)
);


ALTER SEQUENCE public.run_run_id_seq OWNED BY public.run.run_id;

CREATE TABLE public.run_instrument_config_map (
                run_id INTEGER NOT NULL,
                instrument_config_id INTEGER NOT NULL,
                CONSTRAINT run_instrument_config_map_pk PRIMARY KEY (run_id, instrument_config_id)
);


CREATE TABLE public.ptm (
                ptm_id INTEGER NOT NULL,
                unimod_id INTEGER,
                full_name VARCHAR(1000),
                short_name VARCHAR(100) NOT NULL,
                serialized_properties OTHER,
                CONSTRAINT ptm_pk PRIMARY KEY (ptm_id)
);
COMMENT ON TABLE public.ptm IS 'Describes the names of the ptm definitions';
COMMENT ON COLUMN public.ptm.unimod_id IS 'the unimod record_id';
COMMENT ON COLUMN public.ptm.short_name IS 'Name that doesn''t include the specificity. For example, Carboxymethyl is the short name, not Carboxymethyl-Cys or Carboxymethyl (C).  MUST BE UNIQUE';


CREATE SEQUENCE public.ptm_specificity_ptm_specificity_id_seq;

CREATE TABLE public.ptm_specificity (
                ptm_specificity_id INTEGER NOT NULL DEFAULT nextval('public.ptm_specificity_ptm_specificity_id_seq'),
                location VARCHAR(14) NOT NULL,
                residue CHAR(1),
                ptm_id INTEGER NOT NULL,
                classification_id INTEGER NOT NULL,
                CONSTRAINT ptm_specificity_pk PRIMARY KEY (ptm_specificity_id)
);
COMMENT ON TABLE public.ptm_specificity IS 'Describes the specificities of the ptm definitions';
COMMENT ON COLUMN public.ptm_specificity.location IS 'Anywhere, Any N-term, Any C-term, Protein N-term, Protein C-term';
COMMENT ON COLUMN public.ptm_specificity.residue IS 'The symbol of the specific residue for this modification.';


ALTER SEQUENCE public.ptm_specificity_ptm_specificity_id_seq OWNED BY public.ptm_specificity.ptm_specificity_id;

CREATE SEQUENCE public.ptm_evidence_ptm_evidence_id_seq;

CREATE TABLE public.ptm_evidence (
                ptm_evidence_id INTEGER NOT NULL DEFAULT nextval('public.ptm_evidence_ptm_evidence_id_seq'),
                type VARCHAR(14) NOT NULL,
                is_required BOOLEAN NOT NULL,
                composition VARCHAR(50) NOT NULL,
                mono_mass DOUBLE PRECISION NOT NULL,
                average_mass DOUBLE PRECISION NOT NULL,
                ptm_id INTEGER NOT NULL,
                CONSTRAINT ptm_evidence_pk PRIMARY KEY (ptm_evidence_id)
);
COMMENT ON TABLE public.ptm_evidence IS 'Ptm associated ions/delta. Only one "Precursor" delta type MUST be defined for each ptm.';
COMMENT ON COLUMN public.ptm_evidence.type IS 'NOT NULL ; available types are: - Precursor =>  delta for the precursor ion - Artefact => associated artefact peaks - NeutralLoss => fragment ion neutral loss - PepNeutralLoss => precursor ion neutral loss';
COMMENT ON COLUMN public.ptm_evidence.is_required IS 'true (=1) for "Scoring Neutral Loss" (flag=false in unmod.xml) and "Required Peptide Neutral Loss" (required=true in unimod.xml). For more information see mascot Neutral Loss definition and unimod.xsd';
COMMENT ON COLUMN public.ptm_evidence.composition IS 'The chemical composition of the modification as a delta between the modified and unmodified residue or terminus. The formula is displayed and entered as ''atoms'', optionally followed by a number in parentheses. The atom terms are separated by spaces, and order is not important. For example, if the modification removes an H and adds a CH3 group, the Composition would be shown as H(2) C. Atoms can be either elements or molecular sub-units. The number may be negative and, if there is no number, 1 is assumed. Hence, H(2) C is the same as H(2) C(1).';


ALTER SEQUENCE public.ptm_evidence_ptm_evidence_id_seq OWNED BY public.ptm_evidence.ptm_evidence_id;

CREATE SEQUENCE public.protein_protein_id_seq;

CREATE TABLE public.protein (
                protein_id INTEGER NOT NULL DEFAULT nextval('public.protein_protein_id_seq'),
                mass DOUBLE PRECISION NOT NULL,
                pi REAL NOT NULL,
                sequence OTHER NOT NULL,
                length INTEGER NOT NULL,
                crc64 VARCHAR(32) NOT NULL,
                serialized_properties OTHER NOT NULL,
                CONSTRAINT protein_pk PRIMARY KEY (protein_id)
);
COMMENT ON TABLE public.protein IS 'Like Uniparc, it  is a non-redundant protein sequence archive. Note: it contains both active and dead sequences, and it is species-merged since sequences are handled just as strings - all sequences 100% identical over the whole length of the sequence between species are merged. A sequence that exists in many copies in different databases is represented as a single entry which allows to identify the same protein from different sources. Only sequences corresponding to protein_match of the MSI-DB are recorded here.  UNIQUE(mass, crc64) => faster than sequence to be checked and anyway Postgres can''t index fields with a too big content TODO: rename to protein_sequence ?';
COMMENT ON COLUMN public.protein.mass IS 'The molecular mass of the protein.';
COMMENT ON COLUMN public.protein.pi IS 'The isoelectric point of the protein.';
COMMENT ON COLUMN public.protein.sequence IS 'A string composed by the residues of this protein sequence. TODO: rename to residues ?';
COMMENT ON COLUMN public.protein.length IS 'The length of the protein sequence.';
COMMENT ON COLUMN public.protein.crc64 IS 'A numerical signature of the protein sequence built by a CRC64 algorithm.';
COMMENT ON COLUMN public.protein.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


ALTER SEQUENCE public.protein_protein_id_seq OWNED BY public.protein.protein_id;

CREATE TABLE public.property_definition (
                property_definition_id VARCHAR(1000) NOT NULL,
                unique_name VARCHAR(10) NOT NULL,
                version VARCHAR(100) NOT NULL,
                short_name OTHER NOT NULL,
                serialized_properties OTHER,
                CONSTRAINT object_tree_schema_pk PRIMARY KEY (property_definition_id)
);
COMMENT ON COLUMN public.property_definition.unique_name IS 'XSD or JSON';
COMMENT ON COLUMN public.property_definition.short_name IS 'The document describing the schema used for the serialization of the object_tree.';


CREATE SEQUENCE public.object_tree_object_tree_id_seq;

CREATE TABLE public.property (
                property_id INTEGER NOT NULL DEFAULT nextval('public.object_tree_object_tree_id_seq'),
                value OTHER NOT NULL,
                property_definition_id VARCHAR(1000) NOT NULL,
                CONSTRAINT object_tree_pk PRIMARY KEY (property_id)
);
COMMENT ON COLUMN public.property.value IS 'A object tree serialized in a string using a given format (XML or JSON).';


ALTER SEQUENCE public.object_tree_object_tree_id_seq OWNED BY public.property.property_id;

CREATE SEQUENCE public.peptide_ptm_peptide_ptm_id_seq;

CREATE TABLE public.peptide_ptm (
                peptide_ptm_id INTEGER NOT NULL DEFAULT nextval('public.peptide_ptm_peptide_ptm_id_seq'),
                seq_position INTEGER NOT NULL,
                mono_mass DOUBLE PRECISION,
                average_mass DOUBLE PRECISION,
                peptide_id INTEGER NOT NULL,
                ptm_specificity_id INTEGER NOT NULL,
                atom_label_id INTEGER NOT NULL,
                CONSTRAINT peptide_ptm_pk PRIMARY KEY (peptide_ptm_id)
);
COMMENT ON TABLE public.peptide_ptm IS 'Describes the PTM''s associated to a given peptide';
COMMENT ON COLUMN public.peptide_ptm.seq_position IS '0 means N-ter -1 means C-ter Other integer values give the position in the peptide sequence';


ALTER SEQUENCE public.peptide_ptm_peptide_ptm_id_seq OWNED BY public.peptide_ptm.peptide_ptm_id;

CREATE TABLE public.msms_search (
                search_settings_id INTEGER NOT NULL,
                fragment_charge_states VARCHAR(100),
                fragment_mass_error_tolerance DOUBLE PRECISION NOT NULL,
                fragment_mass_error_tolerance_unit VARCHAR(3) NOT NULL,
                CONSTRAINT msms_search_pk PRIMARY KEY (search_settings_id)
);
COMMENT ON TABLE public.msms_search IS 'rename to ms2_search_settings ?';


CREATE TABLE public.ion_search (
                search_settings_id INTEGER NOT NULL,
                max_protein_mass DOUBLE PRECISION,
                min_protein_mass DOUBLE PRECISION,
                protein_pi REAL,
                CONSTRAINT ion_search_pk PRIMARY KEY (search_settings_id)
);
COMMENT ON TABLE public.ion_search IS 'rename to pmf_search_settings ?';


CREATE SEQUENCE public.peaklist_peaklist_id_seq;

CREATE TABLE public.peaklist (
                peaklist_id INTEGER NOT NULL DEFAULT nextval('public.peaklist_peaklist_id_seq'),
                type VARCHAR(10),
                path VARCHAR(1000),
                ms_level INTEGER NOT NULL,
                spectrum_data_compression VARCHAR(20) NOT NULL,
                serialized_properties OTHER,
                run_id INTEGER NOT NULL,
                peaklist_software_id INTEGER NOT NULL,
                CONSTRAINT peaklist_pk PRIMARY KEY (peaklist_id)
);
COMMENT ON TABLE public.peaklist IS 'A peaklist can be a merge of several peaklists';
COMMENT ON COLUMN public.peaklist.type IS 'the type of the source file submitted to the search engine. The sourcefile is the file at the very beginning of the whole search process. This can be a peak list file (MGF, PKL, DTA, mzXML, etc) or a raw data file if the search process is done via Mascot Daemon for example (.raw, .wiff, etc)';
COMMENT ON COLUMN public.peaklist.path IS 'the path to the source file if exists.';
COMMENT ON COLUMN public.peaklist.ms_level IS '1 => PMF 2 => MS/MS n => mix of MS2 and MS3';
COMMENT ON COLUMN public.peaklist.spectrum_data_compression IS 'Describes the compression applied on moz_list and intensity_list of related spectra (must be one of none, zlib, lzma).';
COMMENT ON COLUMN public.peaklist.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


ALTER SEQUENCE public.peaklist_peaklist_id_seq OWNED BY public.peaklist.peaklist_id;

CREATE TABLE public.peaklist_relation (
                parent_peaklist_id INTEGER NOT NULL,
                child_peaklist_id INTEGER NOT NULL,
                CONSTRAINT peaklist_relation_pk PRIMARY KEY (parent_peaklist_id, child_peaklist_id)
);


CREATE SEQUENCE public.spectrum_spectrum_id_seq;

CREATE TABLE public.spectrum (
                spectrum_id INTEGER NOT NULL DEFAULT nextval('public.spectrum_spectrum_id_seq'),
                title VARCHAR(1024) NOT NULL,
                precursor_moz DOUBLE PRECISION,
                precursor_intensity DOUBLE PRECISION,
                precursor_charge INTEGER,
                is_summed BOOLEAN NOT NULL,
                first_cycle INTEGER,
                last_cycle INTEGER,
                first_scan INTEGER,
                last_scan INTEGER,
                first_time REAL,
                last_time REAL,
                moz_list BYTEA,
                intensity_list BYTEA,
                peak_count INTEGER NOT NULL,
                serialized_properties OTHER,
                peaklist_id INTEGER NOT NULL,
                instrument_config_id INTEGER NOT NULL,
                CONSTRAINT spectrum_pk PRIMARY KEY (spectrum_id)
);
COMMENT ON TABLE public.spectrum IS 'The fragmentation spectrum submitted to the search engine. It can be a merge of multiple ms2 spectra. Time and scan values correspond then to the first and the last spectrum of the merge. In PMF studies only precursor attributes are used.';
COMMENT ON COLUMN public.spectrum.title IS 'The description associated to this spectrum.';
COMMENT ON COLUMN public.spectrum.precursor_moz IS 'The parent ion m/z';
COMMENT ON COLUMN public.spectrum.precursor_intensity IS 'The parent ion intensity (optional)';
COMMENT ON COLUMN public.spectrum.precursor_charge IS 'The parent ion charge which could be undefined for some spectra.';
COMMENT ON COLUMN public.spectrum.first_time IS 'The chromatographic time at which this spectrum has been acquired.';
COMMENT ON COLUMN public.spectrum.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


ALTER SEQUENCE public.spectrum_spectrum_id_seq OWNED BY public.spectrum.spectrum_id;

CREATE SEQUENCE public.consensus_spectrum_consensus_spectrum_id_seq;

CREATE TABLE public.consensus_spectrum (
                consensus_spectrum_id INTEGER NOT NULL DEFAULT nextval('public.consensus_spectrum_consensus_spectrum_id_seq'),
                precursor_charge INTEGER NOT NULL,
                precursor_calculated_moz DOUBLE PRECISION NOT NULL,
                normalized_elution_time REAL NOT NULL,
                is_artificial BOOLEAN NOT NULL,
                creation_mode VARCHAR(10) NOT NULL,
                serialized_properties OTHER NOT NULL,
                spectrum_id INTEGER NOT NULL,
                peptide_id INTEGER NOT NULL,
                CONSTRAINT consensus_spectrum_pk PRIMARY KEY (consensus_spectrum_id)
);
COMMENT ON COLUMN public.consensus_spectrum.precursor_calculated_moz IS 'may be usefull for a library search engine';
COMMENT ON COLUMN public.consensus_spectrum.normalized_elution_time IS 'Value between 0 and 1';
COMMENT ON COLUMN public.consensus_spectrum.creation_mode IS 'auto => this consensus has been created by a program ; manual => this consensus has been created by a user';
COMMENT ON COLUMN public.consensus_spectrum.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


ALTER SEQUENCE public.consensus_spectrum_consensus_spectrum_id_seq OWNED BY public.consensus_spectrum.consensus_spectrum_id;

CREATE TABLE public.used_ptm (
                ptm_specificity_id INTEGER NOT NULL,
                search_settings_id INTEGER NOT NULL,
                is_fixed BOOLEAN NOT NULL,
                type VARCHAR(50),
                CONSTRAINT used_ptm_pk PRIMARY KEY (ptm_specificity_id, search_settings_id)
);
COMMENT ON COLUMN public.used_ptm.type IS 'TODO: remove ???';


CREATE SEQUENCE public.enzyme_enzyme_id_seq;

CREATE TABLE public.enzyme (
                enzyme_id INTEGER NOT NULL DEFAULT nextval('public.enzyme_enzyme_id_seq'),
                name VARCHAR(100) NOT NULL,
                cleavage_regexp VARCHAR(50),
                is_independant BOOLEAN NOT NULL,
                is_semi_specific BOOLEAN NOT NULL,
                CONSTRAINT enzyme_pk PRIMARY KEY (enzyme_id)
);
COMMENT ON COLUMN public.enzyme.name IS 'MUST BE UNIQUE';
COMMENT ON COLUMN public.enzyme.cleavage_regexp IS 'The regular expression used to find cleavage site';


ALTER SEQUENCE public.enzyme_enzyme_id_seq OWNED BY public.enzyme.enzyme_id;

CREATE SEQUENCE public.enzyme_cleavage_enzyme_cleavage_id_seq;

CREATE TABLE public.enzyme_cleavage (
                enzyme_cleavage_id INTEGER NOT NULL DEFAULT nextval('public.enzyme_cleavage_enzyme_cleavage_id_seq'),
                position VARCHAR(6) NOT NULL,
                residues VARCHAR(20) NOT NULL,
                restrictive_residue VARCHAR(20),
                enzyme_id INTEGER NOT NULL,
                CONSTRAINT enzyme_cleavage_pk PRIMARY KEY (enzyme_cleavage_id)
);
COMMENT ON COLUMN public.enzyme_cleavage.position IS 'Must be N-term or C-term (cleave before or after the residue)';
COMMENT ON COLUMN public.enzyme_cleavage.restrictive_residue IS 'A string which main contains one or more symbols of amino acids restricting enzyme cleavage.';


ALTER SEQUENCE public.enzyme_cleavage_enzyme_cleavage_id_seq OWNED BY public.enzyme_cleavage.enzyme_cleavage_id;

CREATE TABLE public.used_enzyme (
                search_settings_id INTEGER NOT NULL,
                enzyme_id INTEGER NOT NULL,
                CONSTRAINT used_enzyme_pk PRIMARY KEY (search_settings_id, enzyme_id)
);


CREATE SEQUENCE public.result_set_result_set_id_seq;

CREATE TABLE public.result_set (
                result_set_id INTEGER NOT NULL DEFAULT nextval('public.result_set_result_set_id_seq'),
                name VARCHAR(1000),
                description VARCHAR(10000),
                type VARCHAR(50) NOT NULL,
                modification_timestamp TIMESTAMP NOT NULL,
                serialized_properties OTHER,
                decoy_result_set_id INTEGER NOT NULL,
                CONSTRAINT result_set_pk PRIMARY KEY (result_set_id)
);
COMMENT ON TABLE public.result_set IS 'A result_set may correspond to results coming from a single result file (one msi_search ) or from multiple result files (result set can be organized hierarchically). The table result_set_relation is used to define the hierarchy between a grouped  result_set and its children. Peptide matches, sequences matches and protein matches are associated to a result set. The type of result_set defines if it corresponds to a native data file or to a result_set created by the user (i.e. result grouping, quantitation...).';
COMMENT ON COLUMN public.result_set.name IS 'The name of the result set';
COMMENT ON COLUMN public.result_set.description IS 'The description of the content';
COMMENT ON COLUMN public.result_set.type IS 'SEARCH for result set representing a unique search, DECOY_SEARCH for result set representing a unique decoy search or USER for user defined result set.';
COMMENT ON COLUMN public.result_set.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


ALTER SEQUENCE public.result_set_result_set_id_seq OWNED BY public.result_set.result_set_id;

CREATE SEQUENCE public.result_summary_result_summary_id_seq;

CREATE TABLE public.result_summary (
                result_summary_id INTEGER NOT NULL DEFAULT nextval('public.result_summary_result_summary_id_seq'),
                description VARCHAR(10000),
                modification_timestamp TIMESTAMP NOT NULL,
                is_quantified BOOLEAN,
                serialized_properties OTHER,
                result_summary_id INTEGER NOT NULL,
                result_set_id INTEGER NOT NULL,
                CONSTRAINT result_summary_pk PRIMARY KEY (result_summary_id)
);
COMMENT ON COLUMN public.result_summary.description IS 'A user description for this result summary.';
COMMENT ON COLUMN public.result_summary.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


ALTER SEQUENCE public.result_summary_result_summary_id_seq OWNED BY public.result_summary.result_summary_id;

CREATE TABLE public.result_summary_property (
                result_summary_id INTEGER NOT NULL,
                property_id INTEGER NOT NULL,
                CONSTRAINT result_summary_object_tree_map_pk PRIMARY KEY (result_summary_id, property_id)
);


CREATE TABLE public.result_summary_relation (
                parent_result_summary_id INTEGER NOT NULL,
                child_result_summary_id INTEGER NOT NULL,
                CONSTRAINT result_summary_relation_pk PRIMARY KEY (parent_result_summary_id, child_result_summary_id)
);


CREATE TABLE public.quanti_component_set (
                quanti_component_set_id INTEGER NOT NULL,
                name VARCHAR(1000) NOT NULL,
                description VARCHAR(1000),
                result_summary_id INTEGER NOT NULL,
                CONSTRAINT quanti_component_set_pk PRIMARY KEY (quanti_component_set_id)
);
COMMENT ON TABLE public.quanti_component_set IS 'A set of quantification component. For example : - Component heavy and light for 2 peptides (14N, 15N) with the same sequence but a different mass - All the reporter (for example iTraq) of a query - ...';
COMMENT ON COLUMN public.quanti_component_set.result_summary_id IS 'Used for indexation by result summary';


CREATE SEQUENCE public.quanti_component_quanti_component_id_seq;

CREATE TABLE public.quanti_component (
                quanti_component_id INTEGER NOT NULL DEFAULT nextval('public.quanti_component_quanti_component_id_seq'),
                name VARCHAR(50),
                raw_abundance_value DOUBLE PRECISION,
                abundance_value DOUBLE PRECISION,
                abundance_unit VARCHAR(50),
                is_relevant BOOLEAN,
                serialized_properties OTHER,
                quanti_component_set_id INTEGER NOT NULL,
                result_summary_id INTEGER NOT NULL,
                CONSTRAINT quanti_component_pk PRIMARY KEY (quanti_component_id)
);
COMMENT ON TABLE public.quanti_component IS 'An element of quantification. Can be related to many items (ms_query, peptide_ion, protein_set) which could be quantified';
COMMENT ON COLUMN public.quanti_component.name IS 'The name of the quantification component';
COMMENT ON COLUMN public.quanti_component.raw_abundance_value IS 'The raw abundance of the component (without any post processing)';
COMMENT ON COLUMN public.quanti_component.abundance_value IS 'The abundance of the component (can be null => the component is known but not its abundance)';
COMMENT ON COLUMN public.quanti_component.abundance_unit IS 'The unit of the abundance_value';
COMMENT ON COLUMN public.quanti_component.is_relevant IS 'Show if the component must be taken for calcul of quantification';
COMMENT ON COLUMN public.quanti_component.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';
COMMENT ON COLUMN public.quanti_component.result_summary_id IS 'Used for indexation by result summary';


ALTER SEQUENCE public.quanti_component_quanti_component_id_seq OWNED BY public.quanti_component.quanti_component_id;

CREATE SEQUENCE public.peptide_instance_peptide_instance_id_seq;

CREATE TABLE public.peptide_instance (
                peptide_instance_id INTEGER NOT NULL DEFAULT nextval('public.peptide_instance_peptide_instance_id_seq'),
                peptide_match_count INTEGER,
                protein_match_count INTEGER,
                protein_set_count INTEGER,
                selection_level INTEGER NOT NULL,
                serialized_properties OTHER,
                peptide_id INTEGER NOT NULL,
                peptide_instance_id INTEGER NOT NULL,
                result_summary_id INTEGER NOT NULL,
                CONSTRAINT peptide_instance_pk PRIMARY KEY (peptide_instance_id)
);
COMMENT ON TABLE public.peptide_instance IS 'Table to list all the distinct peptide_match. A peptide instance can be considered as a unique peptide identification related to a given result set.';
COMMENT ON COLUMN public.peptide_instance.peptide_match_count IS 'The number of peptide matches related to the same peptide instance.';
COMMENT ON COLUMN public.peptide_instance.protein_match_count IS 'The number of protein matches containaning an AA sequence corresponding to this peptide instance. Note: a peptide could be considered as proteotypic if this number equals 1.';
COMMENT ON COLUMN public.peptide_instance.protein_set_count IS 'The number of protein sets related to this peptide instance. Note: a peptide could be considered as proteo-specific if this number equals 1.';
COMMENT ON COLUMN public.peptide_instance.selection_level IS 'An integer coding for the selection of this peptide instance : 0 = manual deselection 1 = automatic deselection 2 = automatic selection 4 = manual selection';
COMMENT ON COLUMN public.peptide_instance.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';
COMMENT ON COLUMN public.peptide_instance.result_summary_id IS 'Used for indexation by result summary';


ALTER SEQUENCE public.peptide_instance_peptide_instance_id_seq OWNED BY public.peptide_instance.peptide_instance_id;

CREATE TABLE public.peptide_instance_quanti_component_map (
                peptide_instance_id INTEGER NOT NULL,
                quanti_component_id INTEGER NOT NULL,
                result_summary_id INTEGER NOT NULL,
                CONSTRAINT peptide_instance_quanti_component_map_pk PRIMARY KEY (peptide_instance_id, quanti_component_id)
);


CREATE TABLE public.result_set_relation (
                parent_result_set_id INTEGER NOT NULL,
                child_result_set_id INTEGER NOT NULL,
                CONSTRAINT result_set_relation_pk PRIMARY KEY (parent_result_set_id, child_result_set_id)
);


CREATE TABLE public.result_set_property_item (
                result_set_id INTEGER NOT NULL,
                property_id INTEGER NOT NULL,
                CONSTRAINT result_set_object_tree_map_pk PRIMARY KEY (result_set_id, property_id)
);


CREATE SEQUENCE public.protein_match_protein_match_id_seq;

CREATE TABLE public.protein_match (
                protein_match_id INTEGER NOT NULL DEFAULT nextval('public.protein_match_protein_match_id_seq'),
                accession VARCHAR(100) NOT NULL,
                description VARCHAR(10000),
                score REAL,
                coverage REAL NOT NULL,
                peptide_match_count INTEGER,
                is_decoy BOOLEAN NOT NULL,
                serialized_properties OTHER,
                taxon_id INTEGER,
                protein_id INTEGER NOT NULL,
                scoring_id INTEGER NOT NULL,
                result_set_id INTEGER NOT NULL,
                CONSTRAINT protein_match_pk PRIMARY KEY (protein_match_id)
);
COMMENT ON TABLE public.protein_match IS 'A protein sequence which has been matched by one or more peptide matches.';
COMMENT ON COLUMN public.protein_match.accession IS 'The label used by the search engine to identify the protein.';
COMMENT ON COLUMN public.protein_match.description IS 'The protein description as provided by the search engine.';
COMMENT ON COLUMN public.protein_match.score IS 'The identification score of the protein.';
COMMENT ON COLUMN public.protein_match.coverage IS 'The percentage of the protein sequence residues covered by the sequence matches.';
COMMENT ON COLUMN public.protein_match.peptide_match_count IS 'The number of peptide matches which are related to this protein match.';
COMMENT ON COLUMN public.protein_match.is_decoy IS 'Specify if the protein match is related  to a decoy database search.';
COMMENT ON COLUMN public.protein_match.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';
COMMENT ON COLUMN public.protein_match.taxon_id IS 'The NCBI taxon id corresponding to this protein match.';
COMMENT ON COLUMN public.protein_match.protein_id IS 'The id of the corresponding protein sequence';


ALTER SEQUENCE public.protein_match_protein_match_id_seq OWNED BY public.protein_match.protein_match_id;

CREATE TABLE public.protein_match_seq_database_map (
                protein_match_id INTEGER NOT NULL,
                seq_database_id INTEGER NOT NULL,
                result_set_id INTEGER NOT NULL,
                CONSTRAINT protein_match_seq_database_map_pk PRIMARY KEY (protein_match_id, seq_database_id)
);


CREATE SEQUENCE public.protein_set_protein_set_id_seq;

CREATE TABLE public.protein_set (
                protein_set_id INTEGER NOT NULL DEFAULT nextval('public.protein_set_protein_set_id_seq'),
                score REAL,
                is_validated BOOLEAN NOT NULL,
                selection_level INTEGER NOT NULL,
                is_cluster BOOLEAN NOT NULL,
                serialized_properties OTHER,
                typical_protein_match_id INTEGER NOT NULL,
                scoring_id INTEGER NOT NULL,
                result_summary_id INTEGER NOT NULL,
                CONSTRAINT protein_set_pk PRIMARY KEY (protein_set_id)
);
COMMENT ON TABLE public.protein_set IS 'Identifies a set of one or more proteins. Enable : - the annotation of this set of proteins, - the grouping of multiple protein sets. A protein set can be defined as a cluster of other protein sets0 In this case it is not linked to a peptide_set but must have mappings to protein_matches.';
COMMENT ON COLUMN public.protein_set.is_validated IS 'The validation status of the protein set.';
COMMENT ON COLUMN public.protein_set.selection_level IS 'An integer coding for the selection of this protein set:  0 = manual deselection 1 = automatic deselection 2 = automatic selection 4 = manual selection';
COMMENT ON COLUMN public.protein_set.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';
COMMENT ON COLUMN public.protein_set.typical_protein_match_id IS 'Specifies the id of the protein match which is the most typical (i.e. representative) of the protein set.';
COMMENT ON COLUMN public.protein_set.result_summary_id IS 'Used for indexation by result summary';


ALTER SEQUENCE public.protein_set_protein_set_id_seq OWNED BY public.protein_set.protein_set_id;

CREATE TABLE public.protein_set_cluster_item (
                protein_set_id INTEGER NOT NULL,
                protein_set_id INTEGER NOT NULL,
                result_summary_id INTEGER NOT NULL,
                CONSTRAINT protein_set_cluster_item_pk PRIMARY KEY (protein_set_id, protein_set_id)
);


CREATE TABLE public.protein_set_object_tree_map (
                protein_set_id INTEGER NOT NULL,
                property_id INTEGER NOT NULL,
                CONSTRAINT protein_set_object_tree_map_pk PRIMARY KEY (protein_set_id, property_id)
);


CREATE TABLE public.protein_set_protein_match_item (
                protein_set_id INTEGER NOT NULL,
                protein_match_id INTEGER NOT NULL,
                result_summary_id INTEGER NOT NULL,
                CONSTRAINT protein_set_protein_match_item_pk PRIMARY KEY (protein_set_id, protein_match_id)
);
COMMENT ON TABLE public.protein_set_protein_match_item IS 'Explicits the relations between protein sequence matches and protein sets.';
COMMENT ON COLUMN public.protein_set_protein_match_item.result_summary_id IS 'Used for indexation by result summary';


CREATE TABLE public.protein_set_quanti_component_item (
                protein_set_id INTEGER NOT NULL,
                quanti_component_id INTEGER NOT NULL,
                result_summary_id INTEGER NOT NULL,
                CONSTRAINT protein_set_quanti_component_map_pk PRIMARY KEY (protein_set_id, quanti_component_id)
);
COMMENT ON TABLE public.protein_set_quanti_component_item IS 'The links between protein set and quantification components';


CREATE SEQUENCE public.peptide_set_peptide_set_id_seq;

CREATE TABLE public.peptide_set (
                peptide_set_id INTEGER NOT NULL DEFAULT nextval('public.peptide_set_peptide_set_id_seq'),
                is_subset BOOLEAN,
                peptide_count INTEGER,
                peptide_match_count INTEGER,
                serialized_properties OTHER,
                protein_set_id INTEGER NOT NULL,
                result_summary_id INTEGER NOT NULL,
                CONSTRAINT peptide_set_pk PRIMARY KEY (peptide_set_id)
);
COMMENT ON TABLE public.peptide_set IS 'Identifies a set of peptides belonging to one or more proteins.';
COMMENT ON COLUMN public.peptide_set.is_subset IS 'Indicates if the peptide set is a subset or not.';
COMMENT ON COLUMN public.peptide_set.peptide_count IS 'The number of peptides contained in this set.';
COMMENT ON COLUMN public.peptide_set.peptide_match_count IS 'The number of peptide matches related to this peptide set.';
COMMENT ON COLUMN public.peptide_set.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';
COMMENT ON COLUMN public.peptide_set.result_summary_id IS 'Used for indexation by result summary';


ALTER SEQUENCE public.peptide_set_peptide_set_id_seq OWNED BY public.peptide_set.peptide_set_id;

CREATE TABLE public.peptide_set_protein_match_map (
                peptide_set_id INTEGER NOT NULL,
                protein_match_id INTEGER NOT NULL,
                result_summary_id INTEGER NOT NULL,
                CONSTRAINT peptide_set_protein_match_map_pk PRIMARY KEY (peptide_set_id, protein_match_id)
);
COMMENT ON TABLE public.peptide_set_protein_match_map IS 'Explicits the relations between protein sequence matches and peptide sets.';
COMMENT ON COLUMN public.peptide_set_protein_match_map.result_summary_id IS 'Used for indexation by result summary.';


CREATE TABLE public.peptide_set_peptide_instance_item (
                peptide_set_id INTEGER NOT NULL,
                peptide_instance_id INTEGER NOT NULL,
                is_best_peptide_set BOOLEAN,
                selection_level INTEGER,
                serialized_properties OTHER NOT NULL,
                result_summary_id INTEGER NOT NULL,
                CONSTRAINT peptide_set_peptide_instance_item_pk PRIMARY KEY (peptide_set_id, peptide_instance_id)
);
COMMENT ON TABLE public.peptide_set_peptide_instance_item IS 'Defines the list of peptide instances belonging to a given peptide set.';
COMMENT ON COLUMN public.peptide_set_peptide_instance_item.selection_level IS 'TODO: NOT NULL ?';
COMMENT ON COLUMN public.peptide_set_peptide_instance_item.result_summary_id IS 'Used for indexation by result summary';


CREATE TABLE public.peptide_set_relation (
                peptide_overset_id INTEGER NOT NULL,
                peptide_subset_id INTEGER NOT NULL,
                is_strict_subset BOOLEAN NOT NULL,
                result_summary_id INTEGER NOT NULL,
                CONSTRAINT peptide_set_relation_pk PRIMARY KEY (peptide_overset_id, peptide_subset_id)
);
COMMENT ON TABLE public.peptide_set_relation IS 'Defines the relation between a peptide overset and a peptide subset.';
COMMENT ON COLUMN public.peptide_set_relation.result_summary_id IS 'Used for indexation by result summary';


CREATE SEQUENCE public.msi_search_msi_search_id_seq;

CREATE TABLE public.msi_search (
                msi_search_id INTEGER NOT NULL DEFAULT nextval('public.msi_search_msi_search_id_seq'),
                title VARCHAR(1000),
                date DATE,
                result_file_number INTEGER NOT NULL,
                result_file_path VARCHAR(1000),
                user_name VARCHAR(100),
                user_email VARCHAR(100),
                queries_count INTEGER,
                submitted_queries_count INTEGER NOT NULL,
                searched_proteins_count INTEGER,
                serialized_properties OTHER,
                search_settings_id INTEGER NOT NULL,
                peaklist_id INTEGER NOT NULL,
                target_result_set_id INTEGER NOT NULL,
                CONSTRAINT msi_search_pk PRIMARY KEY (msi_search_id)
);
COMMENT ON TABLE public.msi_search IS 'An identification search performed with a search engine such as Mascot. Contains  the description of the identification search.';
COMMENT ON COLUMN public.msi_search.date IS 'the date of the search.';
COMMENT ON COLUMN public.msi_search.result_file_number IS 'rename to result_file_name and add job_number ???';
COMMENT ON COLUMN public.msi_search.user_name IS 'The name of the user who submit the search to the search engine.';
COMMENT ON COLUMN public.msi_search.user_email IS 'The email of the user.';
COMMENT ON COLUMN public.msi_search.queries_count IS 'The number of queries actually associated to this msi search in the database.';
COMMENT ON COLUMN public.msi_search.submitted_queries_count IS 'The number of spectra submitted to the search engine. This count may be different from the number of queries actually associated to this identification in the database (queries_count) since only queries providing peptide identification may be stored in the database.';
COMMENT ON COLUMN public.msi_search.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';
COMMENT ON COLUMN public.msi_search.target_result_set_id IS 'Link to target (forward) result set';


ALTER SEQUENCE public.msi_search_msi_search_id_seq OWNED BY public.msi_search.msi_search_id;

CREATE TABLE public.msi_search_property_item (
                msi_search_id INTEGER NOT NULL,
                object_tree_id INTEGER NOT NULL,
                CONSTRAINT msi_search_object_tree_map_pk PRIMARY KEY (msi_search_id, object_tree_id)
);


CREATE SEQUENCE public.ms_query_ms_query_id_seq;

CREATE TABLE public.ms_query (
                ms_query_id INTEGER NOT NULL DEFAULT nextval('public.ms_query_ms_query_id_seq'),
                initial_id INTEGER NOT NULL,
                charge INTEGER NOT NULL,
                moz DOUBLE PRECISION NOT NULL,
                serialized_target_properties OTHER,
                spectrum_id INTEGER NOT NULL,
                msi_search_id INTEGER NOT NULL,
                CONSTRAINT ms_query_pk PRIMARY KEY (ms_query_id)
);
COMMENT ON TABLE public.ms_query IS 'One of the queries submitted to the search engine. A query represents a spectrum contained in the submitted peaklist. Search engines such as MASCOT usually identify each spectrum with it''s own id and generates a description from some properties of the original spectrum. This table is where these id and description are stored.';
COMMENT ON COLUMN public.ms_query.initial_id IS 'The id associated to this query by the search engine.';
COMMENT ON COLUMN public.ms_query.serialized_target_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


ALTER SEQUENCE public.ms_query_ms_query_id_seq OWNED BY public.ms_query.ms_query_id;

CREATE TABLE public.ms_query_quanti_component_item (
                ms_query_id INTEGER NOT NULL,
                quanti_component_id INTEGER NOT NULL,
                result_summary_id INTEGER NOT NULL,
                CONSTRAINT ms_query_quanti_component_map_pk PRIMARY KEY (ms_query_id, quanti_component_id)
);
COMMENT ON TABLE public.ms_query_quanti_component_item IS 'The links between ms_query and quantification components (for iTraq experiment for example)';
COMMENT ON COLUMN public.ms_query_quanti_component_item.result_summary_id IS 'Used for indexation by result summary';


CREATE SEQUENCE public.peptide_match_peptide_match_id_seq;

CREATE TABLE public.peptide_match (
                peptide_match_id INTEGER NOT NULL DEFAULT nextval('public.peptide_match_peptide_match_id_seq'),
                charge INTEGER NOT NULL,
                experimental_moz DOUBLE PRECISION NOT NULL,
                elution_value DOUBLE PRECISION,
                elution_unit VARCHAR(10),
                score REAL,
                rank INTEGER,
                delta_moz DOUBLE PRECISION,
                missed_cleavage INTEGER NOT NULL,
                fragment_match_count INTEGER NOT NULL,
                is_decoy BOOLEAN NOT NULL,
                serialized_properties OTHER,
                peptide_id INTEGER NOT NULL,
                ms_query_id INTEGER NOT NULL,
                best_child_id INTEGER NOT NULL,
                scoring_id INTEGER NOT NULL,
                result_set_id INTEGER NOT NULL,
                CONSTRAINT peptide_match_pk PRIMARY KEY (peptide_match_id)
);
COMMENT ON TABLE public.peptide_match IS 'A peptide match is an amino acid (AA) sequence identified from a MS query. A peptide match can be an AA sequence that potentially match a fragmentation spectrum (called observed peptide match, cause they are experimentally "observed" through their fragmentation spectrum) or a group of observed peptide matches sharing the same caracteristics. In this later case, the observed peptide matches are called child peptide matches. Note: this constraint should be added => UNIQUE(peptide_id, ms_query_id, result_set_id)';
COMMENT ON COLUMN public.peptide_match.charge IS 'The charge state.';
COMMENT ON COLUMN public.peptide_match.experimental_moz IS 'The observed m/z. Note: this value is intentionally redundant with the one stored in the ms_query table.';
COMMENT ON COLUMN public.peptide_match.elution_value IS 'A value representing an elution property of the peptide match (see elution_unit for more details).';
COMMENT ON COLUMN public.peptide_match.elution_unit IS 'The elution_value can be expressed using the following units: time, scan or cycle.';
COMMENT ON COLUMN public.peptide_match.score IS 'The identification score of the peptide match provided by the search engine.';
COMMENT ON COLUMN public.peptide_match.rank IS 'It is computed by comparison of the peptide match scores obtained for a given ms_query. The score are sorted in a descending order and peptide and ranked from 1 to n. The highest the score the lowest the rank. Note: Mascot keeps only peptide matches ranking from 1 to 10.';
COMMENT ON COLUMN public.peptide_match.delta_moz IS 'It is the m/z difference between the observed m/z and a calculated m/z derived from the peptide calculated mass. Note: delta_moz = exp_moz - calc_moz';
COMMENT ON COLUMN public.peptide_match.missed_cleavage IS 'It is the number of enzyme missed cleavages that are present in the peptide sequence.';
COMMENT ON COLUMN public.peptide_match.fragment_match_count IS 'The number of observed MS2 fragments that were matched to theoretical fragments of this peptide.';
COMMENT ON COLUMN public.peptide_match.is_decoy IS 'Specify if the peptide match is related  to a decoy database search.';
COMMENT ON COLUMN public.peptide_match.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


ALTER SEQUENCE public.peptide_match_peptide_match_id_seq OWNED BY public.peptide_match.peptide_match_id;

CREATE SEQUENCE public.peptide_ion_peptide_ion_id_seq;

CREATE TABLE public.peptide_ion (
                peptide_ion_id INTEGER NOT NULL DEFAULT nextval('public.peptide_ion_peptide_ion_id_seq'),
                charge INTEGER,
                moz DOUBLE PRECISION NOT NULL,
                elution_value DOUBLE PRECISION NOT NULL,
                elution_unit VARCHAR(10) NOT NULL,
                lcms_feature_id INTEGER,
                serialized_properties OTHER,
                peptide_id INTEGER NOT NULL,
                peptide_instance_id INTEGER NOT NULL,
                quanti_component_id INTEGER NOT NULL,
                peptide_match_id INTEGER NOT NULL,
                peptide_ion_id INTEGER NOT NULL,
                result_summary_id INTEGER NOT NULL,
                CONSTRAINT peptide_ion_pk PRIMARY KEY (peptide_ion_id)
);
COMMENT ON TABLE public.peptide_ion IS 'A peptide ion corresponds to a ionized peptide produced by the mass spectrometer. Its characteristics (charge, m/z, elution time) could be retrieved using LCMS analysis. The observed abundance is described by the related quanti_component. The table can also be considered as a link between peptide and quantification components.  If a peptide ion can be related to a peptide match, the peptide_instance_id and peptide_id have to be defined.';
COMMENT ON COLUMN public.peptide_ion.charge IS 'The charge of the quantified item (example : 2+, 3+, etc...)';
COMMENT ON COLUMN public.peptide_ion.lcms_feature_id IS 'A link to a lcms feature in an lcms database';
COMMENT ON COLUMN public.peptide_ion.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';
COMMENT ON COLUMN public.peptide_ion.peptide_instance_id IS 'Raccourci pour savoir si le peptide à été identifié (=si non null)';
COMMENT ON COLUMN public.peptide_ion.result_summary_id IS 'Used for indexation by result summary';


ALTER SEQUENCE public.peptide_ion_peptide_ion_id_seq OWNED BY public.peptide_ion.peptide_ion_id;

CREATE TABLE public.peptide_ion_relation (
                parent_peptide_ion_id INTEGER NOT NULL,
                child_peptide_ion_id INTEGER NOT NULL,
                parent_result_summary_id INTEGER NOT NULL,
                CONSTRAINT peptide_ion_relation_pk PRIMARY KEY (parent_peptide_ion_id, child_peptide_ion_id)
);
COMMENT ON COLUMN public.peptide_ion_relation.parent_result_summary_id IS 'Used for indexation by result summary of the parent peptide ion';


CREATE TABLE public.peptide_match_object_tree_map (
                peptide_match_id INTEGER NOT NULL,
                property_id INTEGER NOT NULL,
                CONSTRAINT peptide_match_object_tree_map_pk PRIMARY KEY (peptide_match_id, property_id)
);
COMMENT ON TABLE public.peptide_match_object_tree_map IS 'TODO: store fragment matches here';


CREATE TABLE public.peptide_instance_peptide_match_map (
                peptide_instance_id INTEGER NOT NULL,
                peptide_match_id INTEGER NOT NULL,
                result_summary_id INTEGER NOT NULL,
                CONSTRAINT peptide_instance_peptide_match_map_pk PRIMARY KEY (peptide_instance_id, peptide_match_id)
);
COMMENT ON COLUMN public.peptide_instance_peptide_match_map.result_summary_id IS 'Used for indexation by result summary';


CREATE TABLE public.peptide_match_relation (
                parent_peptide_match_id INTEGER NOT NULL,
                child_peptide_match_id INTEGER NOT NULL,
                result_set_id INTEGER NOT NULL,
                CONSTRAINT peptide_match_relation_pk PRIMARY KEY (parent_peptide_match_id, child_peptide_match_id)
);
COMMENT ON TABLE public.peptide_match_relation IS 'Parent-child relationship between peptide matches. See peptide match description.';


CREATE TABLE public.sequence_match (
                protein_match_id INTEGER NOT NULL,
                peptide_id INTEGER NOT NULL,
                start INTEGER NOT NULL,
                stop INTEGER NOT NULL,
                preceding_residue CHAR(1),
                following_residue CHAR(1),
                is_decoy BOOLEAN NOT NULL,
                serialized_properties OTHER NOT NULL,
                best_peptide_match_id INTEGER NOT NULL,
                protein_id INTEGER NOT NULL,
                result_set_id INTEGER NOT NULL,
                CONSTRAINT sequence_match_pk PRIMARY KEY (protein_match_id, peptide_id, start, stop)
);
COMMENT ON TABLE public.sequence_match IS 'A peptide sequence which matches a protein sequence. Note: start and stop are included in the PK in order to handle repeated peptide sequences in a given protein sequence.';
COMMENT ON COLUMN public.sequence_match.start IS 'The start position of the peptide in the protein.';
COMMENT ON COLUMN public.sequence_match.stop IS 'The end position of the peptide in the protein. "end" is a reserved word in Postgres so stop is used instead.';
COMMENT ON COLUMN public.sequence_match.preceding_residue IS 'The residue which is located before the peptide in the protein sequence.';
COMMENT ON COLUMN public.sequence_match.following_residue IS 'The residue which is located after the peptide in the protein sequence.';
COMMENT ON COLUMN public.sequence_match.is_decoy IS 'Specify if the sequence match is related  to a decoy database search.';


CREATE TABLE public.result_set_msi_search_map (
                result_set_id INTEGER NOT NULL,
                msi_search_id INTEGER NOT NULL,
                CONSTRAINT result_set_msi_search_map_pk PRIMARY KEY (result_set_id, msi_search_id)
);


CREATE TABLE public.admin_infos (
                model_version VARCHAR(1000) NOT NULL,
                db_creation_date DATE,
                model_update_date DATE,
                CONSTRAINT admin_infos_pk PRIMARY KEY (model_version)
);
COMMENT ON TABLE public.admin_infos IS 'Give information about the current database model';


ALTER TABLE public.protein_set ADD CONSTRAINT scoring_protein_set_fk
FOREIGN KEY (scoring_id)
REFERENCES public.scoring (scoring_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.protein_match ADD CONSTRAINT scoring_protein_match_fk
FOREIGN KEY (scoring_id)
REFERENCES public.scoring (scoring_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_match ADD CONSTRAINT scoring_peptide_match_fk
FOREIGN KEY (scoring_id)
REFERENCES public.scoring (scoring_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peaklist ADD CONSTRAINT peaklist_software_peaklist_fk
FOREIGN KEY (peaklist_software_id)
REFERENCES public.peaklist_software (peaklist_software_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.fragmentation_rule ADD CONSTRAINT theoretical_fragment_fragmentation_rule_fk
FOREIGN KEY (theoretical_fragment_id)
REFERENCES public.theoretical_fragment (theoretical_fragment_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.fragmentation_rule ADD CONSTRAINT required_serie_fragmentation_rule_fk
FOREIGN KEY (required_serie_id)
REFERENCES public.theoretical_fragment (theoretical_fragment_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.search_settings_seq_database_map ADD CONSTRAINT seq_database_search_settings_seq_database_map_fk
FOREIGN KEY (seq_database_id)
REFERENCES public.seq_database (seq_database_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.protein_match_seq_database_map ADD CONSTRAINT seq_database_protein_match_seq_database_map_fk
FOREIGN KEY (seq_database_id)
REFERENCES public.seq_database (seq_database_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.instrument_config ADD CONSTRAINT fragmentation_mode_instrument_config_fk
FOREIGN KEY (activation_type)
REFERENCES public.activation (type)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.ptm_specificity ADD CONSTRAINT ptm_classification_ptm_specificity_fk
FOREIGN KEY (classification_id)
REFERENCES public.ptm_classification (ptm_classification_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_ptm ADD CONSTRAINT atom_label_peptide_ptm_fk
FOREIGN KEY (atom_label_id)
REFERENCES public.atom_label (atom_label_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide ADD CONSTRAINT atom_label_peptide_fk
FOREIGN KEY (atom_label_id)
REFERENCES public.atom_label (atom_label_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.instrument_config ADD CONSTRAINT instrument_instrument_config_fk
FOREIGN KEY (instrument_id)
REFERENCES public.instrument (instrument_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.search_settings ADD CONSTRAINT instrument_config_search_settings_fk
FOREIGN KEY (instrument_config_id)
REFERENCES public.instrument_config (instrument_config_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.spectrum ADD CONSTRAINT instrument_config_spectrum_fk
FOREIGN KEY (instrument_config_id)
REFERENCES public.instrument_config (instrument_config_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.instrument_config_fragmentation_rule_map ADD CONSTRAINT instrument_config_instrument_fragmentation_rule_fk
FOREIGN KEY (instrument_config_id)
REFERENCES public.instrument_config (instrument_config_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.run_instrument_config_map ADD CONSTRAINT instrument_config_run_instrument_config_map_fk
FOREIGN KEY (instrument_config_id)
REFERENCES public.instrument_config (instrument_config_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.instrument_config_fragmentation_rule_map ADD CONSTRAINT fragmentation_rule_instrument_fragmentation_rule_fk
FOREIGN KEY (fragmentation_rule_id)
REFERENCES public.fragmentation_rule (fragmentation_rule_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.sequence_match ADD CONSTRAINT peptide_sequence_match_fk
FOREIGN KEY (peptide_id)
REFERENCES public.peptide (peptide_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_ptm ADD CONSTRAINT peptide_peptide_ptm_fk
FOREIGN KEY (peptide_id)
REFERENCES public.peptide (peptide_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.consensus_spectrum ADD CONSTRAINT peptide_consensus_spectrum_fk
FOREIGN KEY (peptide_id)
REFERENCES public.peptide (peptide_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_instance ADD CONSTRAINT peptide_peptide_instance_fk
FOREIGN KEY (peptide_id)
REFERENCES public.peptide (peptide_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_ion ADD CONSTRAINT peptide_quanti_peptide_ion_fk
FOREIGN KEY (peptide_id)
REFERENCES public.peptide (peptide_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_match ADD CONSTRAINT peptide_peptide_match_fk
FOREIGN KEY (peptide_id)
REFERENCES public.peptide (peptide_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.msi_search ADD CONSTRAINT search_settings_msi_search_fk
FOREIGN KEY (search_settings_id)
REFERENCES public.search_settings (search_settings_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.used_ptm ADD CONSTRAINT search_settings_used_ptm_fk
FOREIGN KEY (search_settings_id)
REFERENCES public.search_settings (search_settings_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.ion_search ADD CONSTRAINT search_settings_ion_search_fk
FOREIGN KEY (search_settings_id)
REFERENCES public.search_settings (search_settings_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.msms_search ADD CONSTRAINT search_settings_msms_search_fk
FOREIGN KEY (search_settings_id)
REFERENCES public.search_settings (search_settings_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.used_enzyme ADD CONSTRAINT search_settings_used_enzyme_fk
FOREIGN KEY (search_settings_id)
REFERENCES public.search_settings (search_settings_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.search_settings_seq_database_map ADD CONSTRAINT search_settings_search_settings_seq_database_map_fk
FOREIGN KEY (search_settings_id)
REFERENCES public.search_settings (search_settings_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.run ADD CONSTRAINT sample_run_fk
FOREIGN KEY (sample_id)
REFERENCES public.sample (sample_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peaklist ADD CONSTRAINT run_peaklist_fk
FOREIGN KEY (run_id)
REFERENCES public.run (run_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.run_instrument_config_map ADD CONSTRAINT run_run_instrument_config_map_fk
FOREIGN KEY (run_id)
REFERENCES public.run (run_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.ptm_evidence ADD CONSTRAINT ptm_ptm_ion_fk
FOREIGN KEY (ptm_id)
REFERENCES public.ptm (ptm_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.ptm_specificity ADD CONSTRAINT ptm_ptm_specificity_fk
FOREIGN KEY (ptm_id)
REFERENCES public.ptm (ptm_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.used_ptm ADD CONSTRAINT ptm_specificity_used_ptm_fk
FOREIGN KEY (ptm_specificity_id)
REFERENCES public.ptm_specificity (ptm_specificity_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_ptm ADD CONSTRAINT ptm_specificity_peptide_ptm_fk
FOREIGN KEY (ptm_specificity_id)
REFERENCES public.ptm_specificity (ptm_specificity_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.sequence_match ADD CONSTRAINT protein_sequence_match_fk
FOREIGN KEY (protein_id)
REFERENCES public.protein (protein_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.protein_match ADD CONSTRAINT protein_protein_match_fk
FOREIGN KEY (protein_id)
REFERENCES public.protein (protein_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.property ADD CONSTRAINT property_definition_property_fk
FOREIGN KEY (property_definition_id)
REFERENCES public.property_definition (property_definition_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.result_set_property_item ADD CONSTRAINT property_result_set_properties_fk
FOREIGN KEY (property_id)
REFERENCES public.property (property_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.msi_search_property_item ADD CONSTRAINT property_msi_search_properties_fk
FOREIGN KEY (object_tree_id)
REFERENCES public.property (property_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.result_summary_property ADD CONSTRAINT property_result_summary_property_fk
FOREIGN KEY (property_id)
REFERENCES public.property (property_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.protein_set_object_tree_map ADD CONSTRAINT object_tree_protein_set_object_tree_map_fk
FOREIGN KEY (property_id)
REFERENCES public.property (property_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_match_object_tree_map ADD CONSTRAINT object_tree_peptide_match_object_tree_map_fk
FOREIGN KEY (property_id)
REFERENCES public.property (property_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.msi_search ADD CONSTRAINT peaklist_msi_search_fk
FOREIGN KEY (peaklist_id)
REFERENCES public.peaklist (peaklist_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.spectrum ADD CONSTRAINT peaklist_spectrum_fk
FOREIGN KEY (peaklist_id)
REFERENCES public.peaklist (peaklist_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peaklist_relation ADD CONSTRAINT parent_peaklist_peaklist_merge_fk
FOREIGN KEY (parent_peaklist_id)
REFERENCES public.peaklist (peaklist_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peaklist_relation ADD CONSTRAINT child_peaklist_peaklist_merge_fk
FOREIGN KEY (child_peaklist_id)
REFERENCES public.peaklist (peaklist_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.ms_query ADD CONSTRAINT spectrum_ms_query_fk
FOREIGN KEY (spectrum_id)
REFERENCES public.spectrum (spectrum_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.consensus_spectrum ADD CONSTRAINT spectrum_consensus_spectrum_fk
FOREIGN KEY (spectrum_id)
REFERENCES public.spectrum (spectrum_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.used_enzyme ADD CONSTRAINT enzyme_used_enzyme_fk
FOREIGN KEY (enzyme_id)
REFERENCES public.enzyme (enzyme_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.enzyme_cleavage ADD CONSTRAINT enzyme_enzyme_cleavage_fk
FOREIGN KEY (enzyme_id)
REFERENCES public.enzyme (enzyme_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.result_set_msi_search_map ADD CONSTRAINT result_set_result_set_search_fk
FOREIGN KEY (result_set_id)
REFERENCES public.result_set (result_set_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_match ADD CONSTRAINT result_set_peptide_match_fk
FOREIGN KEY (result_set_id)
REFERENCES public.result_set (result_set_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.sequence_match ADD CONSTRAINT result_set_sequence_match_fk
FOREIGN KEY (result_set_id)
REFERENCES public.result_set (result_set_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.result_set ADD CONSTRAINT decoy_result_set_result_set_fk
FOREIGN KEY (decoy_result_set_id)
REFERENCES public.result_set (result_set_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.msi_search ADD CONSTRAINT target_result_set_msi_search_fk
FOREIGN KEY (target_result_set_id)
REFERENCES public.result_set (result_set_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.protein_match ADD CONSTRAINT result_set_protein_match_fk
FOREIGN KEY (result_set_id)
REFERENCES public.result_set (result_set_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.result_set_property_item ADD CONSTRAINT result_set_result_set_properties_fk
FOREIGN KEY (result_set_id)
REFERENCES public.result_set (result_set_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.result_set_relation ADD CONSTRAINT parent_result_set_result_set_relation_fk
FOREIGN KEY (parent_result_set_id)
REFERENCES public.result_set (result_set_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.result_set_relation ADD CONSTRAINT child_result_set_result_set_relation_fk
FOREIGN KEY (child_result_set_id)
REFERENCES public.result_set (result_set_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.result_summary ADD CONSTRAINT result_set_result_summary_fk
FOREIGN KEY (result_set_id)
REFERENCES public.result_set (result_set_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_match_relation ADD CONSTRAINT result_set_peptide_match_relation_fk
FOREIGN KEY (result_set_id)
REFERENCES public.result_set (result_set_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.protein_match_seq_database_map ADD CONSTRAINT result_set_protein_match_seq_database_map_fk
FOREIGN KEY (result_set_id)
REFERENCES public.result_set (result_set_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_set ADD CONSTRAINT result_summary_peptide_set_fk
FOREIGN KEY (result_summary_id)
REFERENCES public.result_summary (result_summary_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.protein_set ADD CONSTRAINT result_summary_protein_set_fk
FOREIGN KEY (result_summary_id)
REFERENCES public.result_summary (result_summary_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_set_relation ADD CONSTRAINT result_summary_peptide_set_relation_fk
FOREIGN KEY (result_summary_id)
REFERENCES public.result_summary (result_summary_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_instance ADD CONSTRAINT result_summary_peptide_instance_fk
FOREIGN KEY (result_summary_id)
REFERENCES public.result_summary (result_summary_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.quanti_component ADD CONSTRAINT result_summary_quanti_component_fk
FOREIGN KEY (result_summary_id)
REFERENCES public.result_summary (result_summary_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.ms_query_quanti_component_item ADD CONSTRAINT result_summary_ms_query_quanti_component_item_fk
FOREIGN KEY (result_summary_id)
REFERENCES public.result_summary (result_summary_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_ion ADD CONSTRAINT result_summary_peptide_ion_fk
FOREIGN KEY (result_summary_id)
REFERENCES public.result_summary (result_summary_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.protein_set_quanti_component_item ADD CONSTRAINT result_summary_protein_set_quanti_component_map_fk
FOREIGN KEY (result_summary_id)
REFERENCES public.result_summary (result_summary_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.quanti_component_set ADD CONSTRAINT result_summary_quanti_component_set_fk
FOREIGN KEY (result_summary_id)
REFERENCES public.result_summary (result_summary_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_set_peptide_instance_item ADD CONSTRAINT result_summary_peptide_set_peptide_instance_item_fk
FOREIGN KEY (result_summary_id)
REFERENCES public.result_summary (result_summary_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_ion_relation ADD CONSTRAINT result_summary_peptide_ion_relation_fk
FOREIGN KEY (parent_result_summary_id)
REFERENCES public.result_summary (result_summary_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.result_summary_relation ADD CONSTRAINT parent_result_summary_result_summary_relation_fk
FOREIGN KEY (parent_result_summary_id)
REFERENCES public.result_summary (result_summary_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.result_summary_relation ADD CONSTRAINT child_result_summary_result_summary_relation_fk
FOREIGN KEY (child_result_summary_id)
REFERENCES public.result_summary (result_summary_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.result_summary_property ADD CONSTRAINT result_summary_result_summary_property_fk
FOREIGN KEY (result_summary_id)
REFERENCES public.result_summary (result_summary_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_instance_peptide_match_map ADD CONSTRAINT result_summary_peptide_instance_peptide_match_map_fk
FOREIGN KEY (result_summary_id)
REFERENCES public.result_summary (result_summary_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_set_protein_match_map ADD CONSTRAINT result_summary_peptide_set_protein_match_map_fk
FOREIGN KEY (result_summary_id)
REFERENCES public.result_summary (result_summary_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.protein_set_protein_match_item ADD CONSTRAINT result_summary_protein_set_protein_match_item_fk
FOREIGN KEY (result_summary_id)
REFERENCES public.result_summary (result_summary_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_instance_quanti_component_map ADD CONSTRAINT result_summary_peptide_instance_quanti_component_map_fk
FOREIGN KEY (result_summary_id)
REFERENCES public.result_summary (result_summary_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.result_summary ADD CONSTRAINT result_summary_result_summary_fk
FOREIGN KEY (result_summary_id)
REFERENCES public.result_summary (result_summary_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.protein_set_cluster_item ADD CONSTRAINT result_summary_protein_cluster_item_fk
FOREIGN KEY (result_summary_id)
REFERENCES public.result_summary (result_summary_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.quanti_component ADD CONSTRAINT quanti_component_set_quanti_component_fk
FOREIGN KEY (quanti_component_set_id)
REFERENCES public.quanti_component_set (quanti_component_set_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.ms_query_quanti_component_item ADD CONSTRAINT quantification_component_ms_query_quanti_component_item_fk
FOREIGN KEY (quanti_component_id)
REFERENCES public.quanti_component (quanti_component_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.protein_set_quanti_component_item ADD CONSTRAINT quanti_component_protein_set_quanti_component_item_fk
FOREIGN KEY (quanti_component_id)
REFERENCES public.quanti_component (quanti_component_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_ion ADD CONSTRAINT quanti_component_peptide_ion_fk
FOREIGN KEY (quanti_component_id)
REFERENCES public.quanti_component (quanti_component_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_instance_quanti_component_map ADD CONSTRAINT quanti_component_peptide_instance_quanti_component_map_fk
FOREIGN KEY (quanti_component_id)
REFERENCES public.quanti_component (quanti_component_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_set_peptide_instance_item ADD CONSTRAINT peptide_instance_peptide_set_peptide_instance_item_fk
FOREIGN KEY (peptide_instance_id)
REFERENCES public.peptide_instance (peptide_instance_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_ion ADD CONSTRAINT peptide_instance_peptide_ion_fk
FOREIGN KEY (peptide_instance_id)
REFERENCES public.peptide_instance (peptide_instance_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_instance_peptide_match_map ADD CONSTRAINT peptide_instance_peptide_instance_peptide_match_map_fk
FOREIGN KEY (peptide_instance_id)
REFERENCES public.peptide_instance (peptide_instance_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_instance ADD CONSTRAINT peptide_instance_peptide_instance_fk
FOREIGN KEY (peptide_instance_id)
REFERENCES public.peptide_instance (peptide_instance_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_instance_quanti_component_map ADD CONSTRAINT peptide_instance_peptide_instance_quanti_component_map_fk
FOREIGN KEY (peptide_instance_id)
REFERENCES public.peptide_instance (peptide_instance_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.sequence_match ADD CONSTRAINT protein_match_sequence_match_fk
FOREIGN KEY (protein_match_id)
REFERENCES public.protein_match (protein_match_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.protein_set ADD CONSTRAINT protein_match_protein_set_fk
FOREIGN KEY (typical_protein_match_id)
REFERENCES public.protein_match (protein_match_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.protein_set_protein_match_item ADD CONSTRAINT protein_match_protein_set_protein_match_item_fk
FOREIGN KEY (protein_match_id)
REFERENCES public.protein_match (protein_match_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_set_protein_match_map ADD CONSTRAINT protein_match_peptide_set_protein_match_map_fk
FOREIGN KEY (protein_match_id)
REFERENCES public.protein_match (protein_match_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.protein_match_seq_database_map ADD CONSTRAINT protein_match_protein_match_seq_database_map_fk
FOREIGN KEY (protein_match_id)
REFERENCES public.protein_match (protein_match_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_set ADD CONSTRAINT protein_set_peptide_set_fk
FOREIGN KEY (protein_set_id)
REFERENCES public.protein_set (protein_set_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.protein_set_quanti_component_item ADD CONSTRAINT protein_set_protein_set_quanti_component_item_fk
FOREIGN KEY (protein_set_id)
REFERENCES public.protein_set (protein_set_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.protein_set_protein_match_item ADD CONSTRAINT protein_set_protein_set_protein_match_item_fk
FOREIGN KEY (protein_set_id)
REFERENCES public.protein_set (protein_set_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.protein_set_object_tree_map ADD CONSTRAINT protein_set_protein_set_object_tree_map_fk
FOREIGN KEY (protein_set_id)
REFERENCES public.protein_set (protein_set_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.protein_set_cluster_item ADD CONSTRAINT protein_set_protein_cluster_item_fk
FOREIGN KEY (protein_set_id)
REFERENCES public.protein_set (protein_set_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.protein_set_cluster_item ADD CONSTRAINT protein_set_protein_cluster_item_fk
FOREIGN KEY (protein_set_id)
REFERENCES public.protein_set (protein_set_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_set_relation ADD CONSTRAINT peptide_overset_peptide_set_map_fk
FOREIGN KEY (peptide_overset_id)
REFERENCES public.peptide_set (peptide_set_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_set_relation ADD CONSTRAINT peptide_subset_peptide_set_map_fk
FOREIGN KEY (peptide_subset_id)
REFERENCES public.peptide_set (peptide_set_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_set_peptide_instance_item ADD CONSTRAINT peptide_set_peptide_set_peptide_instance_item_fk
FOREIGN KEY (peptide_set_id)
REFERENCES public.peptide_set (peptide_set_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_set_protein_match_map ADD CONSTRAINT peptide_set_peptide_set_protein_match_map_fk
FOREIGN KEY (peptide_set_id)
REFERENCES public.peptide_set (peptide_set_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.result_set_msi_search_map ADD CONSTRAINT msi_search_result_set_search_fk
FOREIGN KEY (msi_search_id)
REFERENCES public.msi_search (msi_search_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.ms_query ADD CONSTRAINT msi_search_ms_query_fk
FOREIGN KEY (msi_search_id)
REFERENCES public.msi_search (msi_search_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.msi_search_property_item ADD CONSTRAINT msi_search_msi_search_properties_fk
FOREIGN KEY (msi_search_id)
REFERENCES public.msi_search (msi_search_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_match ADD CONSTRAINT ms_query_peptide_match_fk
FOREIGN KEY (ms_query_id)
REFERENCES public.ms_query (ms_query_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.ms_query_quanti_component_item ADD CONSTRAINT ms_query_ms_query_quanti_component_item_fk
FOREIGN KEY (ms_query_id)
REFERENCES public.ms_query (ms_query_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.sequence_match ADD CONSTRAINT peptide_match_sequence_match_fk
FOREIGN KEY (best_peptide_match_id)
REFERENCES public.peptide_match (peptide_match_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_match_relation ADD CONSTRAINT parent_peptide_match_peptide_match_relation_fk
FOREIGN KEY (parent_peptide_match_id)
REFERENCES public.peptide_match (peptide_match_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_match_relation ADD CONSTRAINT child_peptide_match_peptide_match_relation_fk
FOREIGN KEY (child_peptide_match_id)
REFERENCES public.peptide_match (peptide_match_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_match ADD CONSTRAINT peptide_match_peptide_match_fk
FOREIGN KEY (best_child_id)
REFERENCES public.peptide_match (peptide_match_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_instance_peptide_match_map ADD CONSTRAINT peptide_match_peptide_instance_peptide_match_map_fk
FOREIGN KEY (peptide_match_id)
REFERENCES public.peptide_match (peptide_match_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_match_object_tree_map ADD CONSTRAINT peptide_match_peptide_match_object_tree_map_fk
FOREIGN KEY (peptide_match_id)
REFERENCES public.peptide_match (peptide_match_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_ion ADD CONSTRAINT peptide_match_peptide_ion_fk
FOREIGN KEY (peptide_match_id)
REFERENCES public.peptide_match (peptide_match_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_ion_relation ADD CONSTRAINT parent_peptide_ion_peptide_ion_relation_fk
FOREIGN KEY (parent_peptide_ion_id)
REFERENCES public.peptide_ion (peptide_ion_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_ion_relation ADD CONSTRAINT child_peptide_ion_peptide_ion_relation_fk
FOREIGN KEY (child_peptide_ion_id)
REFERENCES public.peptide_ion (peptide_ion_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_ion ADD CONSTRAINT peptide_ion_peptide_ion_fk
FOREIGN KEY (peptide_ion_id)
REFERENCES public.peptide_ion (peptide_ion_id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

CREATE SEQUENCE public.scoring_id_seq;

CREATE TABLE public.scoring (
                id BIGINT NOT NULL DEFAULT nextval('public.scoring_id_seq'),
                search_engine VARCHAR(100) NOT NULL,
                name VARCHAR(100) NOT NULL,
                description VARCHAR(1000),
                serialized_properties TEXT,
                CONSTRAINT scoring_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.scoring IS 'UNIQUE(search_engine,name)';
COMMENT ON COLUMN public.scoring.search_engine IS 'mascot, omssa, x!tandem, meta (when scoring performed by an extra algorithm)';
COMMENT ON COLUMN public.scoring.name IS 'The name of the computed score.';


ALTER SEQUENCE public.scoring_id_seq OWNED BY public.scoring.id;

CREATE UNIQUE INDEX scoring_idx
 ON public.scoring
 ( search_engine, name );

CREATE TABLE public.cache (
                scope VARCHAR(250) NOT NULL,
                id BIGINT NOT NULL,
                format VARCHAR(50) NOT NULL,
                byte_order INTEGER NOT NULL,
                data BYTEA NOT NULL,
                compression VARCHAR(20) NOT NULL,
                timestamp TIMESTAMP NOT NULL,
                serialized_properties TEXT,
                CONSTRAINT cache_pk PRIMARY KEY (scope, id, format, byte_order)
);
COMMENT ON COLUMN public.cache.format IS 'examples: perl.storable java.serializable json';
COMMENT ON COLUMN public.cache.compression IS 'none, zlib, lzma, snappy';


CREATE INDEX cache_scope_idx
 ON public.cache
 ( scope );

CREATE TABLE public.peaklist_software (
                id BIGINT NOT NULL,
                name VARCHAR(100) NOT NULL,
                version VARCHAR(100),
                serialized_properties TEXT,
                CONSTRAINT peaklist_software_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.peaklist_software IS 'UNIQUE( name, version )';
COMMENT ON COLUMN public.peaklist_software.id IS 'IDs are generated using the UDSdb.';
COMMENT ON COLUMN public.peaklist_software.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


CREATE UNIQUE INDEX peaklist_software_idx
 ON public.peaklist_software
 ( name, version );

CREATE TABLE public.instrument_config (
                id BIGINT NOT NULL,
                name VARCHAR(100) NOT NULL,
                ms1_analyzer VARCHAR(100) NOT NULL,
                msn_analyzer VARCHAR(100),
                serialized_properties TEXT,
                CONSTRAINT instrument_config_pk PRIMARY KEY (id)
);
COMMENT ON COLUMN public.instrument_config.id IS 'IDs are generated using the UDSdb.';
COMMENT ON COLUMN public.instrument_config.name IS 'MUST BE UNIQUE';


CREATE UNIQUE INDEX instrument_config_name_idx
 ON public.instrument_config
 ( name );

CREATE SEQUENCE public.seq_database_id_seq;

CREATE TABLE public.seq_database (
                id BIGINT NOT NULL DEFAULT nextval('public.seq_database_id_seq'),
                name VARCHAR(100) NOT NULL,
                fasta_file_path VARCHAR(500) NOT NULL,
                version VARCHAR(100),
                release_date TIMESTAMP NOT NULL,
                sequence_count INTEGER,
                serialized_properties TEXT,
                CONSTRAINT seq_database_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.seq_database IS 'The database used in the MSI search';
COMMENT ON COLUMN public.seq_database.name IS 'The name of the database.';
COMMENT ON COLUMN public.seq_database.fasta_file_path IS 'The path to the file containing the sequences. MUST BE UNIQUE';
COMMENT ON COLUMN public.seq_database.version IS 'The version of the database';
COMMENT ON COLUMN public.seq_database.release_date IS 'The release date of the database. Format is yyyy-mm-dd hh:mm:ss';
COMMENT ON COLUMN public.seq_database.sequence_count IS 'The number of sequences contained in the database.';


ALTER SEQUENCE public.seq_database_id_seq OWNED BY public.seq_database.id;

CREATE UNIQUE INDEX seq_database_fasta_file_path_idx
 ON public.seq_database
 ( fasta_file_path );

CREATE TABLE public.ptm_specificity (
                id BIGINT NOT NULL,
                location VARCHAR(14) NOT NULL,
                residue CHAR(1),
                serialized_properties TEXT,
                CONSTRAINT ptm_specificity_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.ptm_specificity IS 'Describes the specificities of the ptm definitions';
COMMENT ON COLUMN public.ptm_specificity.location IS 'Anywhere, Any N-term, Any C-term, Protein N-term, Protein C-term';
COMMENT ON COLUMN public.ptm_specificity.residue IS 'The symbol of the specific residue for this modification.';


CREATE TABLE public.peptide (
                id BIGINT NOT NULL,
                sequence TEXT NOT NULL,
                ptm_string TEXT,
                calculated_mass DOUBLE PRECISION NOT NULL,
                serialized_properties TEXT,
                CONSTRAINT peptide_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.peptide IS 'A peptide is an amino acid (AA) sequence with given PTMs. A peptide has a unique pair of sequence/PTM string.';
COMMENT ON COLUMN public.peptide.id IS 'IDs are generated using the PSdb.';
COMMENT ON COLUMN public.peptide.sequence IS 'The AA sequence of this peptide';
COMMENT ON COLUMN public.peptide.ptm_string IS 'A string that describes the ptm structure. EX : MENHIR with oxidation (M) and SILAC label (R) 1[O]7[C(-9) 13C(9)] Each ptm is described by its delta composition. The prefix number gives the position of ptm on the peptide. The atomic number MUST be explicited for non natural isotope only (EX: 15N) . The number of added (or removed) atoms MUST be specified ONLY if more than one atom is concerned. Must be also defined for atom labeling (EX: N(-1) 15N).';
COMMENT ON COLUMN public.peptide.calculated_mass IS 'The theoretical mass of the peptide.';
COMMENT ON COLUMN public.peptide.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


CREATE UNIQUE INDEX peptide_seq_ptm_idx
 ON public.peptide
 ( sequence, ptm_string );

CREATE INDEX peptide_mass_idx
 ON public.peptide
 ( calculated_mass );

CREATE SEQUENCE public.search_settings_id_seq;

CREATE TABLE public.search_settings (
                id BIGINT NOT NULL DEFAULT nextval('public.search_settings_id_seq'),
                software_name VARCHAR(1000),
                software_version VARCHAR(1000),
                taxonomy VARCHAR(1000),
                max_missed_cleavages INTEGER,
                peptide_charge_states VARCHAR(100),
                peptide_mass_error_tolerance DOUBLE PRECISION,
                peptide_mass_error_tolerance_unit VARCHAR(3),
                quantitation VARCHAR(100),
                is_decoy BOOLEAN NOT NULL,
                serialized_properties TEXT,
                instrument_config_id BIGINT NOT NULL,
                CONSTRAINT search_settings_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.search_settings IS 'The settings used in a given MSI search';
COMMENT ON COLUMN public.search_settings.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


ALTER SEQUENCE public.search_settings_id_seq OWNED BY public.search_settings.id;

CREATE TABLE public.search_settings_seq_database_map (
                search_settings_id BIGINT NOT NULL,
                seq_database_id BIGINT NOT NULL,
                searched_sequences_count INTEGER NOT NULL,
                serialized_properties TEXT,
                CONSTRAINT search_settings_seq_database_map_pk PRIMARY KEY (search_settings_id, seq_database_id)
);


CREATE TABLE public.bio_sequence (
                id BIGINT NOT NULL,
                alphabet VARCHAR(3) NOT NULL,
                sequence TEXT NOT NULL,
                length INTEGER NOT NULL,
                mass INTEGER NOT NULL,
                pi REAL,
                crc64 VARCHAR(32) NOT NULL,
                serialized_properties TEXT,
                CONSTRAINT bio_sequence_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.bio_sequence IS 'Like Uniparc, it  is a non-redundant protein sequence archive. Note: it contains both active and dead sequences, and it is species-merged since sequences are handled just as strings - all sequences 100% identical over the whole length of the sequence between species are merged. A sequence that exists in many copies in different databases is represented as a single entry which allows to identify the same protein from different sources. Only sequences corresponding to protein_match of the MSI-DB are recorded here.  UNIQUE(mass, crc64) => faster than sequence to be checked and anyway Postgres can''t index fields with a too big content';
COMMENT ON COLUMN public.bio_sequence.id IS 'IDs are generated using the PDIdb.';
COMMENT ON COLUMN public.bio_sequence.alphabet IS 'dna, rna or aa';
COMMENT ON COLUMN public.bio_sequence.sequence IS 'The sequence of the protein. It can contains amino acids or nucleic acids depending on the used alphabet.';
COMMENT ON COLUMN public.bio_sequence.length IS 'The length of the protein sequence.';
COMMENT ON COLUMN public.bio_sequence.mass IS 'The approximated molecular mass of the protein or of the nucleic acid strand.';
COMMENT ON COLUMN public.bio_sequence.pi IS 'The isoelectric point of the protein. Only for protein sequences (alphabet=aa).';
COMMENT ON COLUMN public.bio_sequence.crc64 IS 'A numerical signature of the protein sequence built by a CRC64 algorithm.';
COMMENT ON COLUMN public.bio_sequence.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


CREATE TABLE public.object_tree_schema (
                name VARCHAR(1000) NOT NULL,
                type VARCHAR(50) NOT NULL,
                is_binary_mode BOOLEAN NOT NULL,
                version VARCHAR(100) NOT NULL,
                schema TEXT NOT NULL,
                description VARCHAR(1000),
                serialized_properties TEXT,
                CONSTRAINT object_tree_schema_pk PRIMARY KEY (name)
);
COMMENT ON COLUMN public.object_tree_schema.type IS 'XSD or JSON or MessagePack';
COMMENT ON COLUMN public.object_tree_schema.is_binary_mode IS 'Specifies if mode of the data encoding which could be binary based or string based (XML or JSON). If binary mode is used the data must be stored in the blob_data field, else in the clob_data field.';
COMMENT ON COLUMN public.object_tree_schema.schema IS 'The document describing the schema used for the serialization of the object_tree.';


CREATE SEQUENCE public.object_tree_id_seq;

CREATE TABLE public.object_tree (
                id BIGINT NOT NULL DEFAULT nextval('public.object_tree_id_seq'),
                blob_data BYTEA,
                clob_data TEXT,
                serialized_properties TEXT,
                schema_name VARCHAR(1000) NOT NULL,
                CONSTRAINT object_tree_pk PRIMARY KEY (id)
);
COMMENT ON COLUMN public.object_tree.blob_data IS 'An object tree serialized as bytes using a given binary serialization framework.';
COMMENT ON COLUMN public.object_tree.clob_data IS 'An object tree serialized in a string of a given format (XML or JSON).';


ALTER SEQUENCE public.object_tree_id_seq OWNED BY public.object_tree.id;

CREATE TABLE public.msms_search (
                id BIGINT NOT NULL,
                fragment_charge_states VARCHAR(100),
                fragment_mass_error_tolerance DOUBLE PRECISION NOT NULL,
                fragment_mass_error_tolerance_unit VARCHAR(3) NOT NULL,
                CONSTRAINT msms_search_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.msms_search IS 'rename to ms2_search_settings ?';


CREATE TABLE public.ion_search (
                id BIGINT NOT NULL,
                max_protein_mass DOUBLE PRECISION,
                min_protein_mass DOUBLE PRECISION,
                protein_pi REAL,
                CONSTRAINT ion_search_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.ion_search IS 'rename to pmf_search_settings ?';


CREATE SEQUENCE public.peaklist_id_seq;

CREATE TABLE public.peaklist (
                id BIGINT NOT NULL DEFAULT nextval('public.peaklist_id_seq'),
                type VARCHAR(100),
                path VARCHAR(1000),
                raw_file_name VARCHAR(250),
                ms_level INTEGER NOT NULL,
                spectrum_data_compression VARCHAR(20) NOT NULL,
                serialized_properties TEXT,
                peaklist_software_id BIGINT NOT NULL,
                CONSTRAINT peaklist_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.peaklist IS 'A peaklist can be a merge of several peaklists';
COMMENT ON COLUMN public.peaklist.type IS 'the type of the source file submitted to the search engine. The sourcefile is the file at the very beginning of the whole search process. This can be a peak list file (MGF, PKL, DTA, mzXML, etc) or a raw data file if the search process is done via Mascot Daemon for example (.raw, .wiff, etc)';
COMMENT ON COLUMN public.peaklist.path IS 'the path to the source file if exists.';
COMMENT ON COLUMN public.peaklist.ms_level IS '1 => PMF 2 => MS/MS n => mix of MS2 and MS3';
COMMENT ON COLUMN public.peaklist.spectrum_data_compression IS 'Describes the compression applied on moz_list and intensity_list of related spectra (must be one of none, zlib, lzma).';
COMMENT ON COLUMN public.peaklist.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


ALTER SEQUENCE public.peaklist_id_seq OWNED BY public.peaklist.id;

CREATE TABLE public.peaklist_relation (
                parent_peaklist_id BIGINT NOT NULL,
                child_peaklist_id BIGINT NOT NULL,
                CONSTRAINT peaklist_relation_pk PRIMARY KEY (parent_peaklist_id, child_peaklist_id)
);


CREATE SEQUENCE public.spectrum_id_seq;

CREATE TABLE public.spectrum (
                id BIGINT NOT NULL DEFAULT nextval('public.spectrum_id_seq'),
                initial_id INTEGER DEFAULT 0 NOT NULL,
                title VARCHAR(1024) NOT NULL,
                precursor_moz DOUBLE PRECISION,
                precursor_intensity REAL,
                precursor_charge INTEGER,
                is_summed BOOLEAN DEFAULT false NOT NULL,
                first_cycle INTEGER,
                last_cycle INTEGER,
                first_scan INTEGER,
                last_scan INTEGER,
                first_time REAL,
                last_time REAL,
                moz_list BYTEA,
                intensity_list BYTEA,
                peak_count INTEGER NOT NULL,
                serialized_properties TEXT,
                peaklist_id BIGINT NOT NULL,
                instrument_config_id BIGINT NOT NULL,
                CONSTRAINT spectrum_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.spectrum IS 'The fragmentation spectrum submitted to the search engine. It can be a merge of multiple ms2 spectra. Time and scan values correspond then to the first and the last spectrum of the merge. In PMF studies only precursor attributes are used.';
COMMENT ON COLUMN public.spectrum.initial_id IS 'An index allowing to retrieve the order of the spectra in the input peaklist.';
COMMENT ON COLUMN public.spectrum.title IS 'The description associated to this spectrum.';
COMMENT ON COLUMN public.spectrum.precursor_moz IS 'The parent ion m/z';
COMMENT ON COLUMN public.spectrum.precursor_intensity IS 'The parent ion intensity (optional)';
COMMENT ON COLUMN public.spectrum.precursor_charge IS 'The parent ion charge which could be undefined for some spectra.';
COMMENT ON COLUMN public.spectrum.is_summed IS 'Indicates whether this spectrum is the sum of multiple spectra.';
COMMENT ON COLUMN public.spectrum.first_time IS 'The chromatographic time at which this spectrum has been acquired.';
COMMENT ON COLUMN public.spectrum.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


ALTER SEQUENCE public.spectrum_id_seq OWNED BY public.spectrum.id;

CREATE INDEX spectrum_pkl_idx
 ON public.spectrum
 ( peaklist_id ASC );

CLUSTER spectrum_pkl_idx ON spectrum;

CREATE SEQUENCE public.consensus_spectrum_id_seq;

CREATE TABLE public.consensus_spectrum (
                id BIGINT NOT NULL DEFAULT nextval('public.consensus_spectrum_id_seq'),
                precursor_charge INTEGER NOT NULL,
                precursor_calculated_moz DOUBLE PRECISION NOT NULL,
                normalized_elution_time REAL,
                is_artificial BOOLEAN NOT NULL,
                creation_mode VARCHAR(10) NOT NULL,
                serialized_properties TEXT,
                spectrum_id BIGINT NOT NULL,
                peptide_id BIGINT NOT NULL,
                CONSTRAINT consensus_spectrum_pk PRIMARY KEY (id)
);
COMMENT ON COLUMN public.consensus_spectrum.precursor_calculated_moz IS 'may be usefull for a library search engine';
COMMENT ON COLUMN public.consensus_spectrum.normalized_elution_time IS 'Value between 0 and 1';
COMMENT ON COLUMN public.consensus_spectrum.creation_mode IS 'auto => this consensus has been created by a program ; manual => this consensus has been created by a user';
COMMENT ON COLUMN public.consensus_spectrum.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


ALTER SEQUENCE public.consensus_spectrum_id_seq OWNED BY public.consensus_spectrum.id;

CREATE TABLE public.used_ptm (
                search_settings_id BIGINT NOT NULL,
                ptm_specificity_id BIGINT NOT NULL,
                short_name VARCHAR(100) NOT NULL,
                is_fixed BOOLEAN NOT NULL,
                CONSTRAINT used_ptm_pk PRIMARY KEY (search_settings_id, ptm_specificity_id)
);


CREATE TABLE public.enzyme (
                id BIGINT NOT NULL,
                name VARCHAR(100) NOT NULL,
                cleavage_regexp VARCHAR(50),
                is_independant BOOLEAN NOT NULL,
                is_semi_specific BOOLEAN NOT NULL,
                serialized_properties TEXT,
                CONSTRAINT enzyme_pk PRIMARY KEY (id)
);
COMMENT ON COLUMN public.enzyme.id IS 'IDs are generated using the UDSdb.';
COMMENT ON COLUMN public.enzyme.name IS 'MUST BE UNIQUE';
COMMENT ON COLUMN public.enzyme.cleavage_regexp IS 'The regular expression used to find cleavage site';
COMMENT ON COLUMN public.enzyme.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


CREATE UNIQUE INDEX enzyme_name_idx
 ON public.enzyme
 ( name );

CREATE TABLE public.used_enzyme (
                search_settings_id BIGINT NOT NULL,
                enzyme_id BIGINT NOT NULL,
                CONSTRAINT used_enzyme_pk PRIMARY KEY (search_settings_id, enzyme_id)
);


CREATE SEQUENCE public.msi_search_id_seq;

CREATE TABLE public.msi_search (
                id BIGINT NOT NULL DEFAULT nextval('public.msi_search_id_seq'),
                title VARCHAR(1000),
                date TIMESTAMP,
                result_file_name VARCHAR(256) NOT NULL,
                result_file_directory VARCHAR(1000),
                job_number INTEGER,
                user_name VARCHAR(100),
                user_email VARCHAR(100),
                queries_count INTEGER,
                submitted_queries_count INTEGER NOT NULL,
                searched_sequences_count INTEGER,
                serialized_properties TEXT,
                search_settings_id BIGINT NOT NULL,
                peaklist_id BIGINT NOT NULL,
                CONSTRAINT msi_search_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.msi_search IS 'An identification search performed with a search engine such as Mascot. Contains  the description of the identification search.';
COMMENT ON COLUMN public.msi_search.date IS 'the date of the search.';
COMMENT ON COLUMN public.msi_search.user_name IS 'The name of the user who submit the search to the search engine.';
COMMENT ON COLUMN public.msi_search.user_email IS 'The email of the user.';
COMMENT ON COLUMN public.msi_search.queries_count IS 'The number of queries actually associated to this msi search in the database.';
COMMENT ON COLUMN public.msi_search.submitted_queries_count IS 'The number of spectra submitted to the search engine. This count may be different from the number of queries actually associated to this identification in the database (queries_count) since only queries providing peptide identification may be stored in the database.';
COMMENT ON COLUMN public.msi_search.searched_sequences_count IS 'The total number of searched sequences. Since searches can be performed against multiple databases, this value is the sum of all associated searches_sequences_count from search_settings_seq_database table.';
COMMENT ON COLUMN public.msi_search.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


ALTER SEQUENCE public.msi_search_id_seq OWNED BY public.msi_search.id;

CREATE TABLE public.msi_search_object_tree_map (
                msi_search_id BIGINT NOT NULL,
                schema_name VARCHAR(1000) NOT NULL,
                object_tree_id BIGINT NOT NULL,
                CONSTRAINT msi_search_object_tree_map_pk PRIMARY KEY (msi_search_id, schema_name)
);


CREATE SEQUENCE public.ms_query_id_seq;

CREATE TABLE public.ms_query (
                id BIGINT NOT NULL DEFAULT nextval('public.ms_query_id_seq'),
                initial_id INTEGER NOT NULL,
                charge INTEGER NOT NULL,
                moz DOUBLE PRECISION NOT NULL,
                serialized_properties TEXT,
                spectrum_id BIGINT,
                msi_search_id BIGINT NOT NULL,
                CONSTRAINT ms_query_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.ms_query IS 'One of the queries submitted to the search engine. A query represents a spectrum contained in the submitted peaklist. Search engines such as MASCOT usually identify each spectrum with it''s own id and generates a description from some properties of the original spectrum. This table is where these id and description are stored.';
COMMENT ON COLUMN public.ms_query.initial_id IS 'The id associated to this query by the search engine.';
COMMENT ON COLUMN public.ms_query.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


ALTER SEQUENCE public.ms_query_id_seq OWNED BY public.ms_query.id;

CREATE INDEX ms_query_search_idx
 ON public.ms_query
 ( msi_search_id ASC );

CLUSTER ms_query_search_idx ON ms_query;

CREATE SEQUENCE public.result_set_id_seq;

CREATE TABLE public.result_set (
                id BIGINT NOT NULL DEFAULT nextval('public.result_set_id_seq'),
                name VARCHAR(1000),
                description VARCHAR(10000),
                type VARCHAR(50) NOT NULL,
                creation_log TEXT,
                modification_timestamp TIMESTAMP NOT NULL,
                serialized_properties TEXT,
                decoy_result_set_id BIGINT,
                merged_rsm_id BIGINT,
                msi_search_id BIGINT,
                CONSTRAINT result_set_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.result_set IS 'A result_set may correspond to results coming from a single result file (one msi_search ) or from multiple result files (result set can be organized hierarchically). The table result_set_relation is used to define the hierarchy between a grouped  result_set and its children. Peptide matches, sequences matches and protein matches are associated to a result set. The type of result_set defines if it corresponds to a native data file or to a result_set created by the user (i.e. result grouping, quantitation...).';
COMMENT ON COLUMN public.result_set.name IS 'The name of the result set';
COMMENT ON COLUMN public.result_set.description IS 'The description of the content';
COMMENT ON COLUMN public.result_set.type IS 'SEARCH for result set representing a unique search, DECOY_SEARCH for result set representing a unique decoy search or USER for user defined result set.';
COMMENT ON COLUMN public.result_set.creation_log IS 'The creation log can be used to store some user relevant information related to the creation of the result set.';
COMMENT ON COLUMN public.result_set.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';
COMMENT ON COLUMN public.result_set.merged_rsm_id IS 'The id of the merged result summary id associated to this result set. This value is only defined when the merge operation has been performed at the result summary level.';


ALTER SEQUENCE public.result_set_id_seq OWNED BY public.result_set.id;

CREATE TABLE public.peptide_readable_ptm_string (
                peptide_id BIGINT NOT NULL,
                result_set_id BIGINT NOT NULL,
                readable_ptm_string VARCHAR NOT NULL,
                CONSTRAINT peptide_readable_ptm_string_pk PRIMARY KEY (peptide_id, result_set_id)
);
COMMENT ON COLUMN public.peptide_readable_ptm_string.readable_ptm_string IS 'Human-readable PTM string.';


CREATE INDEX peptide_readable_ptm_string_rs_idx
 ON public.peptide_readable_ptm_string
 ( result_set_id );

CLUSTER peptide_readable_ptm_string_rs_idx ON peptide_readable_ptm_string;

CREATE SEQUENCE public.result_summary_id_seq;

CREATE TABLE public.result_summary (
                id BIGINT NOT NULL DEFAULT nextval('public.result_summary_id_seq'),
                description VARCHAR(10000),
                creation_log TEXT,
                modification_timestamp TIMESTAMP NOT NULL,
                is_quantified BOOLEAN NOT NULL,
                serialized_properties TEXT,
                decoy_result_summary_id BIGINT,
                result_set_id BIGINT NOT NULL,
                CONSTRAINT result_summary_pk PRIMARY KEY (id)
);
COMMENT ON COLUMN public.result_summary.description IS 'A user description for this result summary.';
COMMENT ON COLUMN public.result_summary.creation_log IS 'The creation log can be used to store some user relevant information related to the creation of the result summary.';
COMMENT ON COLUMN public.result_summary.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


ALTER SEQUENCE public.result_summary_id_seq OWNED BY public.result_summary.id;

CREATE TABLE public.result_summary_object_tree_map (
                result_summary_id BIGINT NOT NULL,
                schema_name VARCHAR(1000) NOT NULL,
                object_tree_id BIGINT NOT NULL,
                CONSTRAINT result_summary_object_tree_map_pk PRIMARY KEY (result_summary_id, schema_name)
);


CREATE TABLE public.result_summary_relation (
                parent_result_summary_id BIGINT NOT NULL,
                child_result_summary_id BIGINT NOT NULL,
                CONSTRAINT result_summary_relation_pk PRIMARY KEY (parent_result_summary_id, child_result_summary_id)
);


CREATE SEQUENCE public.master_quant_component_id_seq;

CREATE TABLE public.master_quant_component (
                id BIGINT NOT NULL DEFAULT nextval('public.master_quant_component_id_seq'),
                selection_level INTEGER NOT NULL,
                serialized_properties TEXT,
                object_tree_id BIGINT NOT NULL,
                schema_name VARCHAR(1000) NOT NULL,
                result_summary_id BIGINT NOT NULL,
                CONSTRAINT master_quant_component_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.master_quant_component IS 'A master group of quantitation components. Can be related to many items (ms_query, peptide_ion, protein_set) which could be quantified.';
COMMENT ON COLUMN public.master_quant_component.selection_level IS 'An integer coding for the selection of this quant component : 0 = manual deselection 1 = automatic deselection 2 = automatic selection 4 = manual selection';
COMMENT ON COLUMN public.master_quant_component.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';
COMMENT ON COLUMN public.master_quant_component.result_summary_id IS 'Used for indexation by result summary';


ALTER SEQUENCE public.master_quant_component_id_seq OWNED BY public.master_quant_component.id;

CREATE INDEX master_quant_component_rsm_idx
 ON public.master_quant_component
 ( result_summary_id ASC );

CLUSTER master_quant_component_rsm_idx ON master_quant_component;

CREATE TABLE public.result_set_relation (
                parent_result_set_id BIGINT NOT NULL,
                child_result_set_id BIGINT NOT NULL,
                CONSTRAINT result_set_relation_pk PRIMARY KEY (parent_result_set_id, child_result_set_id)
);


CREATE TABLE public.result_set_object_tree_map (
                result_set_id BIGINT NOT NULL,
                schema_name VARCHAR(1000) NOT NULL,
                object_tree_id BIGINT NOT NULL,
                CONSTRAINT result_set_object_tree_map_pk PRIMARY KEY (result_set_id, schema_name)
);


CREATE SEQUENCE public.protein_match_id_seq;

CREATE TABLE public.protein_match (
                id BIGINT NOT NULL DEFAULT nextval('public.protein_match_id_seq'),
                accession VARCHAR(100) NOT NULL,
                description VARCHAR(10000),
                gene_name VARCHAR(100),
                score REAL,
                coverage REAL NOT NULL,
                peptide_count INTEGER NOT NULL,
                peptide_match_count INTEGER NOT NULL,
                is_decoy BOOLEAN NOT NULL,
                is_last_bio_sequence BOOLEAN NOT NULL,
                serialized_properties TEXT,
                taxon_id BIGINT,
                bio_sequence_id BIGINT,
                scoring_id BIGINT NOT NULL,
                result_set_id BIGINT NOT NULL,
                CONSTRAINT protein_match_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.protein_match IS 'A protein sequence which has been matched by one or more peptide matches.
UNIQUE( accession, result_set_id )';
COMMENT ON COLUMN public.protein_match.accession IS 'The label used by the search engine to identify the protein.';
COMMENT ON COLUMN public.protein_match.description IS 'The protein description as provided by the search engine.';
COMMENT ON COLUMN public.protein_match.score IS 'The identification score of the protein.';
COMMENT ON COLUMN public.protein_match.coverage IS 'The percentage of the protein sequence residues covered by the sequence matches.';
COMMENT ON COLUMN public.protein_match.peptide_match_count IS 'The number of peptide matches which are related to this protein match.';
COMMENT ON COLUMN public.protein_match.is_decoy IS 'Specifies if the protein match is related to a decoy database search.';
COMMENT ON COLUMN public.protein_match.is_last_bio_sequence IS 'true if bio_sequence_id is referencing the last known bio_sequence for this accession';
COMMENT ON COLUMN public.protein_match.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details). TODO: store the frame_number here';
COMMENT ON COLUMN public.protein_match.taxon_id IS 'The NCBI taxon id corresponding to this protein match.';
COMMENT ON COLUMN public.protein_match.bio_sequence_id IS 'The id of the protein sequence which was identified by the search engine.';


ALTER SEQUENCE public.protein_match_id_seq OWNED BY public.protein_match.id;

CREATE INDEX protein_match_ac_idx
 ON public.protein_match
 ( accession );

CREATE INDEX protein_match_seq_idx
 ON public.protein_match
 ( bio_sequence_id );

CREATE INDEX protein_match_rs_idx
 ON public.protein_match
 ( result_set_id ASC );

CLUSTER protein_match_rs_idx ON protein_match;

CREATE TABLE public.protein_match_seq_database_map (
                protein_match_id BIGINT NOT NULL,
                seq_database_id BIGINT NOT NULL,
                result_set_id BIGINT NOT NULL,
                CONSTRAINT protein_match_seq_database_map_pk PRIMARY KEY (protein_match_id, seq_database_id)
);


CREATE INDEX prot_match_seq_db_map_rs_idx
 ON public.protein_match_seq_database_map
 ( result_set_id ASC );

CLUSTER prot_match_seq_db_map_rs_idx ON protein_match_seq_database_map;

CREATE SEQUENCE public.protein_set_id_seq;

CREATE TABLE public.protein_set (
                id BIGINT NOT NULL DEFAULT nextval('public.protein_set_id_seq'),
                is_decoy BOOLEAN NOT NULL,
                is_validated BOOLEAN NOT NULL,
                selection_level INTEGER NOT NULL,
                serialized_properties TEXT,
                typical_protein_match_id BIGINT NOT NULL,
                master_quant_component_id BIGINT,
                result_summary_id BIGINT NOT NULL,
                CONSTRAINT protein_set_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.protein_set IS 'Identifies a set of one or more proteins. Enable : - the annotation of this set of proteins, - the grouping of multiple protein sets. A protein set can be defined as a cluster of other protein sets0 In this case it is not linked to a peptide_set but must have mappings to protein_matches.';
COMMENT ON COLUMN public.protein_set.is_decoy IS 'Specifies if the protein set is related to a decoy database search.';
COMMENT ON COLUMN public.protein_set.is_validated IS 'The validation status of the protein set.';
COMMENT ON COLUMN public.protein_set.selection_level IS 'An integer coding for the selection of this protein set:
0 = manual deselection
1 = automatic deselection
2 = automatic selection
3 = manual selection';
COMMENT ON COLUMN public.protein_set.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';
COMMENT ON COLUMN public.protein_set.typical_protein_match_id IS 'Specifies the id of the protein match which is the most typical (i.e. representative) of the protein set.';
COMMENT ON COLUMN public.protein_set.result_summary_id IS 'Used for indexation by result summary';


ALTER SEQUENCE public.protein_set_id_seq OWNED BY public.protein_set.id;

CREATE INDEX protein_set_rsm_idx
 ON public.protein_set
 ( result_summary_id ASC );

CLUSTER protein_set_rsm_idx ON protein_set;

CREATE TABLE public.protein_set_object_tree_map (
                protein_set_id BIGINT NOT NULL,
                schema_name VARCHAR(1000) NOT NULL,
                object_tree_id BIGINT NOT NULL,
                CONSTRAINT protein_set_object_tree_map_pk PRIMARY KEY (protein_set_id, schema_name)
);


CREATE TABLE public.protein_set_protein_match_item (
                protein_set_id BIGINT NOT NULL,
                protein_match_id BIGINT NOT NULL,
                is_in_subset BOOLEAN NOT NULL,
                serialized_properties TEXT,
                result_summary_id BIGINT NOT NULL,
                CONSTRAINT protein_set_protein_match_item_pk PRIMARY KEY (protein_set_id, protein_match_id)
);
COMMENT ON TABLE public.protein_set_protein_match_item IS 'Explicits the relations between protein matches and protein sets.';
COMMENT ON COLUMN public.protein_set_protein_match_item.is_in_subset IS 'Indicates if the protein match item identifies a subset of peptides.';
COMMENT ON COLUMN public.protein_set_protein_match_item.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';
COMMENT ON COLUMN public.protein_set_protein_match_item.result_summary_id IS 'Used for indexation by result summary';


CREATE INDEX prot_set_prot_match_item_rsm_idx
 ON public.protein_set_protein_match_item
 ( result_summary_id ASC );

CLUSTER prot_set_prot_match_item_rsm_idx ON protein_set_protein_match_item;

CREATE SEQUENCE public.peptide_set_id_seq;

CREATE TABLE public.peptide_set (
                id BIGINT NOT NULL DEFAULT nextval('public.peptide_set_id_seq'),
                is_subset BOOLEAN NOT NULL,
                score REAL NOT NULL,
                peptide_count INTEGER NOT NULL,
                peptide_match_count INTEGER NOT NULL,
                serialized_properties TEXT,
                protein_set_id BIGINT,
                scoring_id BIGINT NOT NULL,
                result_summary_id BIGINT NOT NULL,
                CONSTRAINT peptide_set_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.peptide_set IS 'Identifies a set of peptides belonging to one or more proteins.';
COMMENT ON COLUMN public.peptide_set.is_subset IS 'Indicates if the peptide set is a subset or not.';
COMMENT ON COLUMN public.peptide_set.peptide_count IS 'The number of peptides contained in this set.';
COMMENT ON COLUMN public.peptide_set.peptide_match_count IS 'The number of peptide matches related to this peptide set.';
COMMENT ON COLUMN public.peptide_set.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';
COMMENT ON COLUMN public.peptide_set.result_summary_id IS 'Used for indexation by result summary';


ALTER SEQUENCE public.peptide_set_id_seq OWNED BY public.peptide_set.id;

CREATE INDEX peptide_set_rsm_idx
 ON public.peptide_set
 ( result_summary_id ASC );

CLUSTER peptide_set_rsm_idx ON peptide_set;

CREATE TABLE public.peptide_set_protein_match_map (
                peptide_set_id BIGINT NOT NULL,
                protein_match_id BIGINT NOT NULL,
                result_summary_id BIGINT NOT NULL,
                CONSTRAINT peptide_set_protein_match_map_pk PRIMARY KEY (peptide_set_id, protein_match_id)
);
COMMENT ON TABLE public.peptide_set_protein_match_map IS 'Explicits the relations between protein sequence matches and peptide sets.';
COMMENT ON COLUMN public.peptide_set_protein_match_map.result_summary_id IS 'Used for indexation by result summary.';


CREATE INDEX pep_set_prot_match_map_rsm_idx
 ON public.peptide_set_protein_match_map
 ( result_summary_id ASC );

CLUSTER pep_set_prot_match_map_rsm_idx ON peptide_set_protein_match_map;

CREATE TABLE public.peptide_set_relation (
                peptide_overset_id BIGINT NOT NULL,
                peptide_subset_id BIGINT NOT NULL,
                is_strict_subset BOOLEAN NOT NULL,
                result_summary_id BIGINT NOT NULL,
                CONSTRAINT peptide_set_relation_pk PRIMARY KEY (peptide_overset_id, peptide_subset_id)
);
COMMENT ON TABLE public.peptide_set_relation IS 'Defines the relation between a peptide overset and a peptide subset.';
COMMENT ON COLUMN public.peptide_set_relation.is_strict_subset IS 'A strict subset doesn''t contain any specific peptide regarding its related overset. In the contrary a non-strict subset has one or more specific peptides with the particularity that these peptides belongs to another overset. This kind of subset is called "subsummable subset".';
COMMENT ON COLUMN public.peptide_set_relation.result_summary_id IS 'Used for indexation by result summary';


CREATE INDEX peptide_set_relation_rsm_idx
 ON public.peptide_set_relation
 ( result_summary_id ASC );

CLUSTER peptide_set_relation_rsm_idx ON peptide_set_relation;

CREATE SEQUENCE public.peptide_match_id_seq;

CREATE TABLE public.peptide_match (
                id BIGINT NOT NULL DEFAULT nextval('public.peptide_match_id_seq'),
                charge INTEGER NOT NULL,
                experimental_moz DOUBLE PRECISION NOT NULL,
                score REAL,
                rank INTEGER,
                cd_pretty_rank INTEGER,
                sd_pretty_rank INTEGER,
                delta_moz REAL,
                missed_cleavage INTEGER NOT NULL,
                fragment_match_count INTEGER,
                is_decoy BOOLEAN NOT NULL,
                serialized_properties TEXT,
                peptide_id BIGINT NOT NULL,
                ms_query_id BIGINT NOT NULL,
                best_child_id BIGINT,
                scoring_id BIGINT NOT NULL,
                result_set_id BIGINT NOT NULL,
                CONSTRAINT peptide_match_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.peptide_match IS 'A peptide match is an amino acid (AA) sequence identified from a MS query. A peptide match can be an AA sequence that potentially match a fragmentation spectrum (called observed peptide match, cause they are experimentally "observed" through their fragmentation spectrum) or a group of observed peptide matches sharing the same caracteristics. In this later case, the observed peptide matches are called child peptide matches. Note: this constraint should be added => UNIQUE(peptide_id, ms_query_id, result_set_id)';
COMMENT ON COLUMN public.peptide_match.charge IS 'The charge state.';
COMMENT ON COLUMN public.peptide_match.experimental_moz IS 'The observed m/z. Note: this value is intentionally redundant with the one stored in the ms_query table.';
COMMENT ON COLUMN public.peptide_match.score IS 'The identification score of the peptide match provided by the search engine.';
COMMENT ON COLUMN public.peptide_match.rank IS 'It is computed by comparison of the peptide match scores obtained for a given ms_query. The score are sorted in a descending order and peptide and ranked from 1 to n. The highest the score the lowest the rank. Note: Mascot keeps only peptide matches ranking from 1 to 10.';
COMMENT ON COLUMN public.peptide_match.cd_pretty_rank IS 'Pretty rank recalculated when importing a new result_set from a concatenated database. The peptide_matches corresponding to the same query are sorted by decreasing score, peptide_matches with very close scores (less than 0.1) are considered equals and will get the same pretty rank. This pretty rank is calculated with PSMs from both target and decoy result_sets.';
COMMENT ON COLUMN public.peptide_match.sd_pretty_rank IS 'Pretty rank recalculated when importing a result_set from a separated database. The peptide_matches corresponding to the same query are sorted by decreasing score, peptide_matches with very close scores (less than 0.1) are considered equals and will get the same pretty rank. This pretty rank is calculated with peptide_matches only from target or decoy result_set.';
COMMENT ON COLUMN public.peptide_match.delta_moz IS 'It is the m/z difference between the observed m/z and a calculated m/z derived from the peptide calculated mass. Note: delta_moz = exp_moz - calc_moz';
COMMENT ON COLUMN public.peptide_match.missed_cleavage IS 'It is the number of enzyme missed cleavages that are present in the peptide sequence.';
COMMENT ON COLUMN public.peptide_match.fragment_match_count IS 'The number of observed MS2 fragments that were matched to theoretical fragments of this peptide.';
COMMENT ON COLUMN public.peptide_match.is_decoy IS 'Specifies if the peptide match is related to a decoy database search.';
COMMENT ON COLUMN public.peptide_match.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


ALTER SEQUENCE public.peptide_match_id_seq OWNED BY public.peptide_match.id;

CREATE INDEX peptide_match_ms_query_idx
 ON public.peptide_match
 ( ms_query_id );

CREATE INDEX peptide_match_peptide_idx
 ON public.peptide_match
 ( peptide_id );

CREATE INDEX peptide_match_rs_idx
 ON public.peptide_match
 ( result_set_id ASC );

CLUSTER peptide_match_rs_idx ON peptide_match;

CREATE SEQUENCE public.peptide_instance_id_seq;

CREATE TABLE public.peptide_instance (
                id BIGINT NOT NULL DEFAULT nextval('public.peptide_instance_id_seq'),
                peptide_match_count INTEGER NOT NULL,
                protein_match_count INTEGER NOT NULL,
                protein_set_count INTEGER NOT NULL,
                validated_protein_set_count INTEGER NOT NULL,
                total_leaves_match_count INTEGER DEFAULT 0 NOT NULL,
                selection_level INTEGER NOT NULL,
                elution_time REAL,
                serialized_properties TEXT,
                best_peptide_match_id BIGINT NOT NULL,
                peptide_id BIGINT NOT NULL,
                unmodified_peptide_id BIGINT,
                master_quant_component_id BIGINT,
                result_summary_id BIGINT NOT NULL,
                CONSTRAINT peptide_instance_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.peptide_instance IS 'Table to list all the distinct peptide_match. A peptide instance can be considered as a unique peptide identification related to a given result set.';
COMMENT ON COLUMN public.peptide_instance.peptide_match_count IS 'The number of peptide matches related to the same peptide instance.';
COMMENT ON COLUMN public.peptide_instance.protein_match_count IS 'The number of protein matches containaning an AA sequence corresponding to this peptide instance. Note: a peptide could be considered as proteotypic if this number equals 1.';
COMMENT ON COLUMN public.peptide_instance.protein_set_count IS 'The number of protein sets related to this peptide instance.';
COMMENT ON COLUMN public.peptide_instance.validated_protein_set_count IS 'The number of validated protein sets related to this peptide instance.';
COMMENT ON COLUMN public.peptide_instance.total_leaves_match_count IS 'The total number of leaves peptide matches related to this peptide instance. This value correspond to Spectral Count.';
COMMENT ON COLUMN public.peptide_instance.selection_level IS 'An integer coding for the selection of this peptide instance :
0 = manual deselection
1 = automatic deselection
2 = automatic selection
3 = manual selection';
COMMENT ON COLUMN public.peptide_instance.elution_time IS 'A value representing an elution time property of the peptide instance. Elution time is expressed is seconds.';
COMMENT ON COLUMN public.peptide_instance.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';
COMMENT ON COLUMN public.peptide_instance.result_summary_id IS 'Used for indexation by result summary';


ALTER SEQUENCE public.peptide_instance_id_seq OWNED BY public.peptide_instance.id;

CREATE INDEX peptide_instance_rsm_idx
 ON public.peptide_instance
 ( result_summary_id ASC );

CLUSTER peptide_instance_rsm_idx ON peptide_instance;

CREATE INDEX peptide_instance_peptide_idx
 ON public.peptide_instance
 ( peptide_id );

CREATE TABLE public.peptide_set_peptide_instance_item (
                peptide_set_id BIGINT NOT NULL,
                peptide_instance_id BIGINT NOT NULL,
                is_best_peptide_set BOOLEAN NOT NULL,
                selection_level INTEGER NOT NULL,
                serialized_properties TEXT,
                result_summary_id BIGINT NOT NULL,
                CONSTRAINT peptide_set_peptide_instance_item_pk PRIMARY KEY (peptide_set_id, peptide_instance_id)
);
COMMENT ON TABLE public.peptide_set_peptide_instance_item IS 'Defines the list of peptide instances belonging to a given peptide set.';
COMMENT ON COLUMN public.peptide_set_peptide_instance_item.selection_level IS 'An integer coding for the selection of this peptide instance in the context of this peptide set:
0 = manual deselection
1 = automatic deselection
2 = automatic selection
3 = manual selection';
COMMENT ON COLUMN public.peptide_set_peptide_instance_item.result_summary_id IS 'Used for indexation by result summary';


CREATE INDEX pep_set_pep_inst_item_rsm_idx
 ON public.peptide_set_peptide_instance_item
 ( result_summary_id ASC );

CLUSTER pep_set_pep_inst_item_rsm_idx ON peptide_set_peptide_instance_item;

CREATE INDEX pep_set_pep_inst_item_pep_inst_idx
 ON public.peptide_set_peptide_instance_item
 ( peptide_instance_id );

CREATE SEQUENCE public.master_quant_peptide_ion_id_seq;

CREATE TABLE public.master_quant_peptide_ion (
                id BIGINT NOT NULL DEFAULT nextval('public.master_quant_peptide_ion_id_seq'),
                charge INTEGER NOT NULL,
                moz DOUBLE PRECISION NOT NULL,
                elution_time REAL NOT NULL,
                scan_number INTEGER,
                peptide_match_count INTEGER NOT NULL,
                serialized_properties TEXT,
                lcms_master_feature_id BIGINT,
                peptide_id BIGINT,
                peptide_instance_id BIGINT,
                master_quant_peptide_id BIGINT NOT NULL,
                master_quant_component_id BIGINT NOT NULL,
                best_peptide_match_id BIGINT,
                unmodified_peptide_ion_id BIGINT,
                result_summary_id BIGINT NOT NULL,
                CONSTRAINT master_quant_peptide_ion_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.master_quant_peptide_ion IS 'A master quant peptide ion corresponds to an ionized peptide produced by the mass spectrometer and quantified in several quantitation channels. Its characteristics (charge, m/z, elution time) could be retrieved using LCMS analysis. The observed abundance is described by the related quanti_component. The table can also be considered as a link between peptide and quantification components.  If a peptide ion can be related to a peptide match, the peptide_instance_id and peptide_id have to be defined.';
COMMENT ON COLUMN public.master_quant_peptide_ion.charge IS 'The charge of the quantified item (example : 2+, 3+, etc...)';
COMMENT ON COLUMN public.master_quant_peptide_ion.peptide_match_count IS 'The number of peptide matches corresponding to this peptide ion. The value is zero if no match.';
COMMENT ON COLUMN public.master_quant_peptide_ion.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';
COMMENT ON COLUMN public.master_quant_peptide_ion.lcms_master_feature_id IS 'A link to a LC-MS master feature in the corresponding LC-MS database.';
COMMENT ON COLUMN public.master_quant_peptide_ion.peptide_instance_id IS 'Raccourci pour savoir si le peptide à été identifié (=si non null)';
COMMENT ON COLUMN public.master_quant_peptide_ion.result_summary_id IS 'Used for indexation by result summary';


ALTER SEQUENCE public.master_quant_peptide_ion_id_seq OWNED BY public.master_quant_peptide_ion.id;

CREATE INDEX master_quant_peptide_ion_peptide_idx
 ON public.master_quant_peptide_ion
 ( peptide_id );

CREATE INDEX master_quant_peptide_ion_rsm_idx
 ON public.master_quant_peptide_ion
 ( result_summary_id ASC );

CLUSTER master_quant_peptide_ion_rsm_idx ON master_quant_peptide_ion;

CREATE SEQUENCE public.master_quant_reporter_ion_id_seq;

CREATE TABLE public.master_quant_reporter_ion (
                id BIGINT NOT NULL DEFAULT nextval('public.master_quant_reporter_ion_id_seq'),
                serialized_properties TEXT,
                master_quant_component_id BIGINT NOT NULL,
                ms_query_id BIGINT NOT NULL,
                master_quant_peptide_ion_id BIGINT NOT NULL,
                result_summary_id BIGINT NOT NULL,
                CONSTRAINT master_quant_reporter_ion_pk PRIMARY KEY (id)
);
COMMENT ON COLUMN public.master_quant_reporter_ion.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


ALTER SEQUENCE public.master_quant_reporter_ion_id_seq OWNED BY public.master_quant_reporter_ion.id;

CREATE INDEX master_quant_reporter_ion_rsm_idx
 ON public.master_quant_reporter_ion
 ( result_summary_id ASC );

CLUSTER master_quant_reporter_ion_rsm_idx ON master_quant_reporter_ion;

CREATE TABLE public.peptide_match_object_tree_map (
                peptide_match_id BIGINT NOT NULL,
                schema_name VARCHAR(1000) NOT NULL,
                object_tree_id BIGINT NOT NULL,
                CONSTRAINT peptide_match_object_tree_map_pk PRIMARY KEY (peptide_match_id, schema_name)
);


CREATE TABLE public.peptide_instance_peptide_match_map (
                peptide_instance_id BIGINT NOT NULL,
                peptide_match_id BIGINT NOT NULL,
                serialized_properties TEXT,
                result_summary_id BIGINT NOT NULL,
                CONSTRAINT peptide_instance_peptide_match_map_pk PRIMARY KEY (peptide_instance_id, peptide_match_id)
);
COMMENT ON COLUMN public.peptide_instance_peptide_match_map.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';
COMMENT ON COLUMN public.peptide_instance_peptide_match_map.result_summary_id IS 'Used for indexation by result summary';


CREATE INDEX pep_inst_pep_match_map_rsm_idx
 ON public.peptide_instance_peptide_match_map
 ( result_summary_id ASC );

CLUSTER pep_inst_pep_match_map_rsm_idx ON peptide_instance_peptide_match_map;

CREATE TABLE public.peptide_match_relation (
                parent_peptide_match_id BIGINT NOT NULL,
                child_peptide_match_id BIGINT NOT NULL,
                parent_result_set_id BIGINT NOT NULL,
                CONSTRAINT peptide_match_relation_pk PRIMARY KEY (parent_peptide_match_id, child_peptide_match_id)
);
COMMENT ON TABLE public.peptide_match_relation IS 'Parent-child relationship between peptide matches. See peptide match description.';


CREATE INDEX peptide_match_relation_rs_idx
 ON public.peptide_match_relation
 ( parent_result_set_id ASC );

CLUSTER peptide_match_relation_rs_idx ON peptide_match_relation;

CREATE TABLE public.sequence_match (
                protein_match_id BIGINT NOT NULL,
                peptide_id BIGINT NOT NULL,
                start INTEGER NOT NULL,
                stop INTEGER NOT NULL,
                residue_before CHAR(1),
                residue_after CHAR(1),
                is_decoy BOOLEAN NOT NULL,
                serialized_properties TEXT,
                best_peptide_match_id BIGINT NOT NULL,
                result_set_id BIGINT NOT NULL,
                CONSTRAINT sequence_match_pk PRIMARY KEY (protein_match_id, peptide_id, start, stop)
);
COMMENT ON TABLE public.sequence_match IS 'A peptide sequence which matches a protein sequence. Note: start and stop are included in the PK in order to handle repeated peptide sequences in a given protein sequence.';
COMMENT ON COLUMN public.sequence_match.start IS 'The start position of the peptide in the protein.';
COMMENT ON COLUMN public.sequence_match.stop IS 'The end position of the peptide in the protein. "end" is a reserved word in Postgres so stop is used instead.';
COMMENT ON COLUMN public.sequence_match.residue_before IS 'The residue which is located before the peptide in the protein sequence.';
COMMENT ON COLUMN public.sequence_match.residue_after IS 'The residue which is located after the peptide in the protein sequence.';
COMMENT ON COLUMN public.sequence_match.is_decoy IS 'Specifies if the sequence match is related to a decoy database search.';


CREATE INDEX sequence_match_pep_idx
 ON public.sequence_match
 ( peptide_id );

CREATE INDEX sequence_match_prot_match_idx
 ON public.sequence_match
 ( protein_match_id );

CREATE INDEX sequence_match_rs_idx
 ON public.sequence_match
 ( result_set_id ASC );

CLUSTER sequence_match_rs_idx ON sequence_match;

CREATE TABLE public.admin_infos (
                model_version VARCHAR(1000) NOT NULL,
                db_creation_date TIMESTAMP,
                model_update_date TIMESTAMP,
                CONSTRAINT admin_infos_pk PRIMARY KEY (model_version)
);
COMMENT ON TABLE public.admin_infos IS 'Give information about the current database model';


ALTER TABLE public.protein_match ADD CONSTRAINT scoring_protein_match_fk
FOREIGN KEY (scoring_id)
REFERENCES public.scoring (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_match ADD CONSTRAINT scoring_peptide_match_fk
FOREIGN KEY (scoring_id)
REFERENCES public.scoring (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_set ADD CONSTRAINT scoring_peptide_set_fk
FOREIGN KEY (scoring_id)
REFERENCES public.scoring (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peaklist ADD CONSTRAINT peaklist_software_peaklist_fk
FOREIGN KEY (peaklist_software_id)
REFERENCES public.peaklist_software (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.search_settings ADD CONSTRAINT instrument_config_search_settings_fk
FOREIGN KEY (instrument_config_id)
REFERENCES public.instrument_config (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.spectrum ADD CONSTRAINT instrument_config_spectrum_fk
FOREIGN KEY (instrument_config_id)
REFERENCES public.instrument_config (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.search_settings_seq_database_map ADD CONSTRAINT seq_database_search_settings_seq_database_map_fk
FOREIGN KEY (seq_database_id)
REFERENCES public.seq_database (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.protein_match_seq_database_map ADD CONSTRAINT seq_database_protein_match_seq_database_map_fk
FOREIGN KEY (seq_database_id)
REFERENCES public.seq_database (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.used_ptm ADD CONSTRAINT ptm_specificity_used_ptm_fk
FOREIGN KEY (ptm_specificity_id)
REFERENCES public.ptm_specificity (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.sequence_match ADD CONSTRAINT peptide_sequence_match_fk
FOREIGN KEY (peptide_id)
REFERENCES public.peptide (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.consensus_spectrum ADD CONSTRAINT peptide_consensus_spectrum_fk
FOREIGN KEY (peptide_id)
REFERENCES public.peptide (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_instance ADD CONSTRAINT peptide_peptide_instance_fk
FOREIGN KEY (peptide_id)
REFERENCES public.peptide (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.master_quant_peptide_ion ADD CONSTRAINT peptide_master_quant_peptide_ion_fk
FOREIGN KEY (peptide_id)
REFERENCES public.peptide (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_match ADD CONSTRAINT peptide_peptide_match_fk
FOREIGN KEY (peptide_id)
REFERENCES public.peptide (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_instance ADD CONSTRAINT unmodified_peptide_peptide_instance_fk
FOREIGN KEY (unmodified_peptide_id)
REFERENCES public.peptide (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_readable_ptm_string ADD CONSTRAINT peptide_peptide_readable_ptm_string_fk
FOREIGN KEY (peptide_id)
REFERENCES public.peptide (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.msi_search ADD CONSTRAINT search_settings_msi_search_fk
FOREIGN KEY (search_settings_id)
REFERENCES public.search_settings (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.used_ptm ADD CONSTRAINT search_settings_used_ptm_fk
FOREIGN KEY (search_settings_id)
REFERENCES public.search_settings (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.ion_search ADD CONSTRAINT search_settings_ion_search_fk
FOREIGN KEY (id)
REFERENCES public.search_settings (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.msms_search ADD CONSTRAINT search_settings_msms_search_fk
FOREIGN KEY (id)
REFERENCES public.search_settings (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.used_enzyme ADD CONSTRAINT search_settings_used_enzyme_fk
FOREIGN KEY (search_settings_id)
REFERENCES public.search_settings (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.search_settings_seq_database_map ADD CONSTRAINT search_settings_search_settings_seq_database_map_fk
FOREIGN KEY (search_settings_id)
REFERENCES public.search_settings (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.protein_match ADD CONSTRAINT initial_bio_sequence_protein_match_fk
FOREIGN KEY (bio_sequence_id)
REFERENCES public.bio_sequence (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.result_set_object_tree_map ADD CONSTRAINT object_tree_schema_result_set_object_tree_map_fk
FOREIGN KEY (schema_name)
REFERENCES public.object_tree_schema (name)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.msi_search_object_tree_map ADD CONSTRAINT object_tree_schema_msi_search_object_tree_map_fk
FOREIGN KEY (schema_name)
REFERENCES public.object_tree_schema (name)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.object_tree ADD CONSTRAINT object_tree_schema_object_tree_map_fk
FOREIGN KEY (schema_name)
REFERENCES public.object_tree_schema (name)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.protein_set_object_tree_map ADD CONSTRAINT object_tree_schema_protein_set_object_tree_map_fk
FOREIGN KEY (schema_name)
REFERENCES public.object_tree_schema (name)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.result_summary_object_tree_map ADD CONSTRAINT object_tree_schema_result_summary_object_tree_map_fk
FOREIGN KEY (schema_name)
REFERENCES public.object_tree_schema (name)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_match_object_tree_map ADD CONSTRAINT object_tree_schema_peptide_match_object_tree_map_fk
FOREIGN KEY (schema_name)
REFERENCES public.object_tree_schema (name)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.master_quant_component ADD CONSTRAINT object_tree_schema_master_quant_component_fk
FOREIGN KEY (schema_name)
REFERENCES public.object_tree_schema (name)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.result_set_object_tree_map ADD CONSTRAINT object_tree_result_set_object_tree_map_fk
FOREIGN KEY (object_tree_id)
REFERENCES public.object_tree (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.msi_search_object_tree_map ADD CONSTRAINT object_tree_msi_search_object_tree_map_fk
FOREIGN KEY (object_tree_id)
REFERENCES public.object_tree (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.result_summary_object_tree_map ADD CONSTRAINT object_tree_result_summary_object_tree_map_fk
FOREIGN KEY (object_tree_id)
REFERENCES public.object_tree (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.protein_set_object_tree_map ADD CONSTRAINT object_tree_protein_set_object_tree_map_fk
FOREIGN KEY (object_tree_id)
REFERENCES public.object_tree (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_match_object_tree_map ADD CONSTRAINT object_tree_peptide_match_object_tree_map_fk
FOREIGN KEY (object_tree_id)
REFERENCES public.object_tree (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.master_quant_component ADD CONSTRAINT object_tree_master_quant_component_fk
FOREIGN KEY (object_tree_id)
REFERENCES public.object_tree (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.msi_search ADD CONSTRAINT peaklist_msi_search_fk
FOREIGN KEY (peaklist_id)
REFERENCES public.peaklist (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.spectrum ADD CONSTRAINT peaklist_spectrum_fk
FOREIGN KEY (peaklist_id)
REFERENCES public.peaklist (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peaklist_relation ADD CONSTRAINT parent_peaklist_peaklist_merge_fk
FOREIGN KEY (parent_peaklist_id)
REFERENCES public.peaklist (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peaklist_relation ADD CONSTRAINT child_peaklist_peaklist_merge_fk
FOREIGN KEY (child_peaklist_id)
REFERENCES public.peaklist (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.ms_query ADD CONSTRAINT spectrum_ms_query_fk
FOREIGN KEY (spectrum_id)
REFERENCES public.spectrum (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.consensus_spectrum ADD CONSTRAINT spectrum_consensus_spectrum_fk
FOREIGN KEY (spectrum_id)
REFERENCES public.spectrum (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.used_enzyme ADD CONSTRAINT enzyme_used_enzyme_fk
FOREIGN KEY (enzyme_id)
REFERENCES public.enzyme (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.ms_query ADD CONSTRAINT msi_search_ms_query_fk
FOREIGN KEY (msi_search_id)
REFERENCES public.msi_search (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.msi_search_object_tree_map ADD CONSTRAINT msi_search_msi_search_object_tree_map_fk
FOREIGN KEY (msi_search_id)
REFERENCES public.msi_search (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.result_set ADD CONSTRAINT msi_search_result_set_fk
FOREIGN KEY (msi_search_id)
REFERENCES public.msi_search (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_match ADD CONSTRAINT ms_query_peptide_match_fk
FOREIGN KEY (ms_query_id)
REFERENCES public.ms_query (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.master_quant_reporter_ion ADD CONSTRAINT ms_query_master_quant_reporter_ion_fk
FOREIGN KEY (ms_query_id)
REFERENCES public.ms_query (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_match ADD CONSTRAINT result_set_peptide_match_fk
FOREIGN KEY (result_set_id)
REFERENCES public.result_set (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.sequence_match ADD CONSTRAINT result_set_sequence_match_fk
FOREIGN KEY (result_set_id)
REFERENCES public.result_set (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.result_set ADD CONSTRAINT decoy_result_set_result_set_fk
FOREIGN KEY (decoy_result_set_id)
REFERENCES public.result_set (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.protein_match ADD CONSTRAINT result_set_protein_match_fk
FOREIGN KEY (result_set_id)
REFERENCES public.result_set (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.result_set_object_tree_map ADD CONSTRAINT result_set_result_set_object_tree_map_fk
FOREIGN KEY (result_set_id)
REFERENCES public.result_set (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.result_set_relation ADD CONSTRAINT parent_result_set_result_set_relation_fk
FOREIGN KEY (parent_result_set_id)
REFERENCES public.result_set (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.result_set_relation ADD CONSTRAINT child_result_set_result_set_relation_fk
FOREIGN KEY (child_result_set_id)
REFERENCES public.result_set (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.result_summary ADD CONSTRAINT result_set_result_summary_fk
FOREIGN KEY (result_set_id)
REFERENCES public.result_set (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_match_relation ADD CONSTRAINT result_set_peptide_match_relation_fk
FOREIGN KEY (parent_result_set_id)
REFERENCES public.result_set (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.protein_match_seq_database_map ADD CONSTRAINT result_set_protein_match_seq_database_map_fk
FOREIGN KEY (result_set_id)
REFERENCES public.result_set (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_readable_ptm_string ADD CONSTRAINT result_set_peptide_readable_ptm_string_fk
FOREIGN KEY (result_set_id)
REFERENCES public.result_set (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_set ADD CONSTRAINT result_summary_peptide_set_fk
FOREIGN KEY (result_summary_id)
REFERENCES public.result_summary (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.protein_set ADD CONSTRAINT result_summary_protein_set_fk
FOREIGN KEY (result_summary_id)
REFERENCES public.result_summary (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_set_relation ADD CONSTRAINT result_summary_peptide_set_relation_fk
FOREIGN KEY (result_summary_id)
REFERENCES public.result_summary (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_instance ADD CONSTRAINT result_summary_peptide_instance_fk
FOREIGN KEY (result_summary_id)
REFERENCES public.result_summary (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.master_quant_component ADD CONSTRAINT result_summary_master_quant_component_fk
FOREIGN KEY (result_summary_id)
REFERENCES public.result_summary (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.master_quant_peptide_ion ADD CONSTRAINT result_summary_master_quant_peptide_ion_fk
FOREIGN KEY (result_summary_id)
REFERENCES public.result_summary (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_set_peptide_instance_item ADD CONSTRAINT result_summary_peptide_set_peptide_instance_item_fk
FOREIGN KEY (result_summary_id)
REFERENCES public.result_summary (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.result_summary_relation ADD CONSTRAINT parent_result_summary_result_summary_relation_fk
FOREIGN KEY (parent_result_summary_id)
REFERENCES public.result_summary (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.result_summary_relation ADD CONSTRAINT child_result_summary_result_summary_relation_fk
FOREIGN KEY (child_result_summary_id)
REFERENCES public.result_summary (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.result_summary_object_tree_map ADD CONSTRAINT result_summary_result_summary_object_tree_map_fk
FOREIGN KEY (result_summary_id)
REFERENCES public.result_summary (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_instance_peptide_match_map ADD CONSTRAINT result_summary_peptide_instance_peptide_match_map_fk
FOREIGN KEY (result_summary_id)
REFERENCES public.result_summary (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_set_protein_match_map ADD CONSTRAINT result_summary_peptide_set_protein_match_map_fk
FOREIGN KEY (result_summary_id)
REFERENCES public.result_summary (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.protein_set_protein_match_item ADD CONSTRAINT result_summary_protein_set_protein_match_item_fk
FOREIGN KEY (result_summary_id)
REFERENCES public.result_summary (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.result_summary ADD CONSTRAINT decoy_result_summary_result_summary_fk
FOREIGN KEY (decoy_result_summary_id)
REFERENCES public.result_summary (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.master_quant_reporter_ion ADD CONSTRAINT result_summary_master_quant_reporter_ion_fk
FOREIGN KEY (result_summary_id)
REFERENCES public.result_summary (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.master_quant_peptide_ion ADD CONSTRAINT master_quant_component_master_quant_peptide_ion_fk
FOREIGN KEY (master_quant_component_id)
REFERENCES public.master_quant_component (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.protein_set ADD CONSTRAINT master_quant_component_protein_set_fk
FOREIGN KEY (master_quant_component_id)
REFERENCES public.master_quant_component (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_instance ADD CONSTRAINT master_quant_component_peptide_instance_fk
FOREIGN KEY (master_quant_component_id)
REFERENCES public.master_quant_component (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.master_quant_reporter_ion ADD CONSTRAINT master_quant_component_master_quant_reporter_ion_fk
FOREIGN KEY (master_quant_component_id)
REFERENCES public.master_quant_component (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.master_quant_peptide_ion ADD CONSTRAINT master_quant_peptide_master_quant_peptide_ion_fk
FOREIGN KEY (master_quant_peptide_id)
REFERENCES public.master_quant_component (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.sequence_match ADD CONSTRAINT protein_match_sequence_match_fk
FOREIGN KEY (protein_match_id)
REFERENCES public.protein_match (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.protein_set ADD CONSTRAINT protein_match_protein_set_fk
FOREIGN KEY (typical_protein_match_id)
REFERENCES public.protein_match (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.protein_set_protein_match_item ADD CONSTRAINT protein_match_protein_set_protein_match_item_fk
FOREIGN KEY (protein_match_id)
REFERENCES public.protein_match (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_set_protein_match_map ADD CONSTRAINT protein_match_peptide_set_protein_match_map_fk
FOREIGN KEY (protein_match_id)
REFERENCES public.protein_match (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.protein_match_seq_database_map ADD CONSTRAINT protein_match_protein_match_seq_database_map_fk
FOREIGN KEY (protein_match_id)
REFERENCES public.protein_match (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_set ADD CONSTRAINT protein_set_peptide_set_fk
FOREIGN KEY (protein_set_id)
REFERENCES public.protein_set (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.protein_set_protein_match_item ADD CONSTRAINT protein_set_protein_set_protein_match_item_fk
FOREIGN KEY (protein_set_id)
REFERENCES public.protein_set (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.protein_set_object_tree_map ADD CONSTRAINT protein_set_protein_set_object_tree_map_fk
FOREIGN KEY (protein_set_id)
REFERENCES public.protein_set (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_set_relation ADD CONSTRAINT peptide_overset_peptide_set_map_fk
FOREIGN KEY (peptide_overset_id)
REFERENCES public.peptide_set (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_set_relation ADD CONSTRAINT peptide_subset_peptide_set_map_fk
FOREIGN KEY (peptide_subset_id)
REFERENCES public.peptide_set (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_set_peptide_instance_item ADD CONSTRAINT peptide_set_peptide_set_peptide_instance_item_fk
FOREIGN KEY (peptide_set_id)
REFERENCES public.peptide_set (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_set_protein_match_map ADD CONSTRAINT peptide_set_peptide_set_protein_match_map_fk
FOREIGN KEY (peptide_set_id)
REFERENCES public.peptide_set (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.sequence_match ADD CONSTRAINT peptide_match_sequence_match_fk
FOREIGN KEY (best_peptide_match_id)
REFERENCES public.peptide_match (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_match_relation ADD CONSTRAINT parent_peptide_match_peptide_match_relation_fk
FOREIGN KEY (parent_peptide_match_id)
REFERENCES public.peptide_match (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_match_relation ADD CONSTRAINT child_peptide_match_peptide_match_relation_fk
FOREIGN KEY (child_peptide_match_id)
REFERENCES public.peptide_match (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_match ADD CONSTRAINT peptide_match_peptide_match_fk
FOREIGN KEY (best_child_id)
REFERENCES public.peptide_match (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_instance_peptide_match_map ADD CONSTRAINT peptide_match_peptide_instance_peptide_match_map_fk
FOREIGN KEY (peptide_match_id)
REFERENCES public.peptide_match (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_match_object_tree_map ADD CONSTRAINT peptide_match_peptide_match_object_tree_map_fk
FOREIGN KEY (peptide_match_id)
REFERENCES public.peptide_match (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.master_quant_peptide_ion ADD CONSTRAINT peptide_match_master_quant_peptide_ion_fk
FOREIGN KEY (best_peptide_match_id)
REFERENCES public.peptide_match (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_instance ADD CONSTRAINT peptide_match_peptide_instance_fk
FOREIGN KEY (best_peptide_match_id)
REFERENCES public.peptide_match (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_set_peptide_instance_item ADD CONSTRAINT peptide_instance_peptide_set_peptide_instance_item_fk
FOREIGN KEY (peptide_instance_id)
REFERENCES public.peptide_instance (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.master_quant_peptide_ion ADD CONSTRAINT peptide_instance_master_quant_peptide_ion_fk
FOREIGN KEY (peptide_instance_id)
REFERENCES public.peptide_instance (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_instance_peptide_match_map ADD CONSTRAINT peptide_instance_peptide_instance_peptide_match_map_fk
FOREIGN KEY (peptide_instance_id)
REFERENCES public.peptide_instance (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.master_quant_peptide_ion ADD CONSTRAINT master_quant_peptide_ion_unmodified_peptide_ion_fk
FOREIGN KEY (unmodified_peptide_ion_id)
REFERENCES public.master_quant_peptide_ion (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.master_quant_reporter_ion ADD CONSTRAINT master_quant_peptide_ion_master_quant_reporter_ion_fk
FOREIGN KEY (master_quant_peptide_ion_id)
REFERENCES public.master_quant_peptide_ion (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;
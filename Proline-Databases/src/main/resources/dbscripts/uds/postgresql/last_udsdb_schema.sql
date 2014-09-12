
CREATE SEQUENCE public.aggregation_id_seq;

CREATE TABLE public.aggregation (
                id BIGINT NOT NULL DEFAULT nextval('public.aggregation_id_seq'),
                child_nature VARCHAR NOT NULL,
                CONSTRAINT aggregation_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.aggregation IS 'The enumeration of the different child natures that could be used to describe a given AGGREGATE data_set.';
COMMENT ON COLUMN public.aggregation.child_nature IS 'Describes the nature of the corresponding level of aggregation. Valid values are: SAMPLE_FRACTION, SAMPLE_ANALYSIS, BIOLOGICAL_SAMPLE, BIOLOGICAL_GROUP, OTHER.';


ALTER SEQUENCE public.aggregation_id_seq OWNED BY public.aggregation.id;

CREATE UNIQUE INDEX aggregation_child_nature_idx
 ON public.aggregation
 ( child_nature );

CREATE SEQUENCE public.fractionation_id_seq;

CREATE TABLE public.fractionation (
                id BIGINT NOT NULL DEFAULT nextval('public.fractionation_id_seq'),
                type VARCHAR NOT NULL,
                CONSTRAINT fractionation_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.fractionation IS 'The enumeration of the different fractionation type that could be used to describe an AGGREGATE data_set corresponding to fractionation step.';
COMMENT ON COLUMN public.fractionation.type IS 'Describes the type of the separation used for the analysis of the sample.
Valid values for this field are: PROTEIN, PEPTIDE, OTHER, NONE.';


ALTER SEQUENCE public.fractionation_id_seq OWNED BY public.fractionation.id;

CREATE UNIQUE INDEX fractionation_type_idx
 ON public.fractionation
 ( type );

CREATE SEQUENCE public.protein_match_decoy_rule_id_seq;

CREATE TABLE public.protein_match_decoy_rule (
                id BIGINT NOT NULL DEFAULT nextval('public.protein_match_decoy_rule_id_seq'),
                name VARCHAR(100) NOT NULL,
                ac_decoy_tag VARCHAR(100) NOT NULL,
                CONSTRAINT protein_match_decoy_rule_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.protein_match_decoy_rule IS 'Stores rules that can be used to determine if a protein_match is decoy or not. If the accession number of the protein_match contains the tag (ac_decoy_tag) it is considered as "decoy".';
COMMENT ON COLUMN public.protein_match_decoy_rule.name IS 'The name of the rule.';
COMMENT ON COLUMN public.protein_match_decoy_rule.ac_decoy_tag IS 'A string that is used to make the distinction between decoy and target protein matches. This string is only added to the accesion number of decoy protein matches.';


ALTER SEQUENCE public.protein_match_decoy_rule_id_seq OWNED BY public.protein_match_decoy_rule.id;

CREATE SEQUENCE public.spec_title_parsing_rule_id_seq;

CREATE TABLE public.spec_title_parsing_rule (
                id BIGINT NOT NULL DEFAULT nextval('public.spec_title_parsing_rule_id_seq'),
                raw_file_name VARCHAR(100),
                first_cycle VARCHAR(100),
                last_cycle VARCHAR(100),
                first_scan VARCHAR(100),
                last_scan VARCHAR(100),
                first_time VARCHAR(100),
                last_time VARCHAR(100),
                CONSTRAINT spec_title_parsing_rule_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.spec_title_parsing_rule IS 'Describe rules used to parse the content of the MS2 spectrum title.';
COMMENT ON COLUMN public.spec_title_parsing_rule.raw_file_name IS 'A regular expression matching the raw file name.';
COMMENT ON COLUMN public.spec_title_parsing_rule.first_cycle IS 'A regular expression matching the first cycle.';
COMMENT ON COLUMN public.spec_title_parsing_rule.last_cycle IS 'A regular expression matching the last cycle.';
COMMENT ON COLUMN public.spec_title_parsing_rule.first_scan IS 'A regular expression matching the first scan.';
COMMENT ON COLUMN public.spec_title_parsing_rule.last_scan IS 'A regular expression matching the last scan.';
COMMENT ON COLUMN public.spec_title_parsing_rule.first_time IS 'A regular expression matching the first time.';
COMMENT ON COLUMN public.spec_title_parsing_rule.last_time IS 'A regular expression matching the last time.';


ALTER SEQUENCE public.spec_title_parsing_rule_id_seq OWNED BY public.spec_title_parsing_rule.id;

CREATE SEQUENCE public.peaklist_software_id_seq;

CREATE TABLE public.peaklist_software (
                id BIGINT NOT NULL DEFAULT nextval('public.peaklist_software_id_seq'),
                name VARCHAR(100) NOT NULL,
                version VARCHAR(100),
                serialized_properties TEXT,
                spec_title_parsing_rule_id BIGINT NOT NULL,
                CONSTRAINT peaklist_software_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.peaklist_software IS 'Describes software that can be used to generate MS/MS peaklists.';
COMMENT ON COLUMN public.peaklist_software.name IS 'The name of the software used to generate the peaklist. Examples: extract_msn, Mascot Distiller, mascot.dll.';
COMMENT ON COLUMN public.peaklist_software.version IS 'The version number of the software.';
COMMENT ON COLUMN public.peaklist_software.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';
COMMENT ON COLUMN public.peaklist_software.spec_title_parsing_rule_id IS 'The rule used to parse spectra title in the peaklists generated by this software.';


ALTER SEQUENCE public.peaklist_software_id_seq OWNED BY public.peaklist_software.id;

CREATE SEQUENCE public.enzyme_id_seq;

CREATE TABLE public.enzyme (
                id BIGINT NOT NULL DEFAULT nextval('public.enzyme_id_seq'),
                name VARCHAR(100) NOT NULL,
                cleavage_regexp VARCHAR(50),
                is_independant BOOLEAN NOT NULL,
                is_semi_specific BOOLEAN NOT NULL,
                serialized_properties TEXT,
                CONSTRAINT enzyme_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.enzyme IS 'The enumeration of the different enzymes that could be used.';
COMMENT ON COLUMN public.enzyme.name IS 'The unique name of the enzyme.';
COMMENT ON COLUMN public.enzyme.cleavage_regexp IS 'The regular expression used to find the cleavage site.';
COMMENT ON COLUMN public.enzyme.is_independant IS 'Specifies the independence of the enzyme cleavages. If false and if there are multiple cleavages, these are combined, as if multiple enzymes had been applied simultaneously or serially to a single sample aliquot. If true, the cleavages are treated as if independent digests had been performed on separate sample aliquots and the resulting peptide mixtures combined.';
COMMENT ON COLUMN public.enzyme.is_semi_specific IS 'Specifies the specificity of the enzyme.
If true, any given peptide need only conform to the cleavage specificity at one end.';
COMMENT ON COLUMN public.enzyme.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


ALTER SEQUENCE public.enzyme_id_seq OWNED BY public.enzyme.id;

CREATE UNIQUE INDEX enzyme_name_idx
 ON public.enzyme
 ( name );

CREATE SEQUENCE public.enzyme_cleavage_id_seq;

CREATE TABLE public.enzyme_cleavage (
                id BIGINT NOT NULL DEFAULT nextval('public.enzyme_cleavage_id_seq'),
                site VARCHAR(6) NOT NULL,
                residues VARCHAR(20) NOT NULL,
                restrictive_residues VARCHAR(20),
                enzyme_id BIGINT NOT NULL,
                CONSTRAINT enzyme_cleavage_pk PRIMARY KEY (id)
);
COMMENT ON COLUMN public.enzyme_cleavage.site IS 'This indicates whether cleavage occurs on the C terminal or N terminal side of the residues.
Valid values for this field are:
N-term, C-term.';
COMMENT ON COLUMN public.enzyme_cleavage.residues IS 'A list of one letter code residues at which cleavage occurs.';
COMMENT ON COLUMN public.enzyme_cleavage.restrictive_residues IS 'A string which main contains one or more residue symbols restricting enzyme cleavage.';
COMMENT ON COLUMN public.enzyme_cleavage.enzyme_id IS 'The enzyme this cleavage corresponds to.';


ALTER SEQUENCE public.enzyme_cleavage_id_seq OWNED BY public.enzyme_cleavage.id;

CREATE SEQUENCE public.fragmentation_series_id_seq;

CREATE TABLE public.fragmentation_series (
                id BIGINT NOT NULL DEFAULT nextval('public.fragmentation_series_id_seq'),
                name VARCHAR(9),
                neutral_loss VARCHAR(5),
                serialized_properties TEXT,
                CONSTRAINT fragmentation_series_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.fragmentation_series IS 'The types of fragment ion series that can be observed in an MS/MS spectrum.';
COMMENT ON COLUMN public.fragmentation_series.name IS 'The conventional name of this fragmentation series.
Valid values for this field are: a b c d v w x y z z+1 z+2 ya yb immonium precursor';
COMMENT ON COLUMN public.fragmentation_series.neutral_loss IS 'The optional neutral loss associated with this fragmentation series.
Valid values for this field are: H2O, NH3, H3PO4.';
COMMENT ON COLUMN public.fragmentation_series.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


ALTER SEQUENCE public.fragmentation_series_id_seq OWNED BY public.fragmentation_series.id;

CREATE SEQUENCE public.fragmentation_rule_id_seq;

CREATE TABLE public.fragmentation_rule (
                id BIGINT NOT NULL DEFAULT nextval('public.fragmentation_rule_id_seq'),
                description VARCHAR(1000),
                precursor_min_charge INTEGER,
                fragment_charge INTEGER,
                fragment_max_moz REAL,
                fragment_residue_constraint VARCHAR(20),
                required_series_quality_level VARCHAR(15),
                serialized_properties TEXT,
                fragment_series_id BIGINT,
                required_series_id BIGINT,
                CONSTRAINT fragmentation_rule_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.fragmentation_rule IS 'The fragmentation rules help to describe the behavior of a given instrument used in a given configuration. Each rule describes fragment ion series that can be observed on an instrument and that are used by the search engine to generate theoretical spectra in order to score experimental spectra.';
COMMENT ON COLUMN public.fragmentation_rule.description IS 'A description for this fragmentation rule.
Here is a list of Mascot fragmenration rules descriptions:
singly charged
doubly charged if precursor 2+ or higher
doubly charged if precursor 3+ or higher
immonium
a series
a - NH3 if a significant and fragment includes RKNQ
a - H2O if a significant and fragment includes STED
b series
b - NH3 if b significant and fragment includes RKNQ
b - H2O if b significant and fragment includes STED
c series
x series
y series
y - NH3 if y significant and fragment includes RKNQ
y - H2O if y significant and fragment includes STED
z series
internal yb < 700 Da
internal ya < 700 Da
y or y++ must be significant
y or y++ must be highest scoring series
z+1 series
d and d'' series
v series
w and w'' series
z+2 series';
COMMENT ON COLUMN public.fragmentation_rule.precursor_min_charge IS 'The minimum charge of the precursor required to observe this fragment type.';
COMMENT ON COLUMN public.fragmentation_rule.fragment_charge IS 'The fragment ion charge state.';
COMMENT ON COLUMN public.fragmentation_rule.fragment_max_moz IS 'The maximum observable fragment m/z.';
COMMENT ON COLUMN public.fragmentation_rule.fragment_residue_constraint IS 'A list of residues the fragment ion must contain in order to be considered. This list is encoded by a string containing consecutive residue symbols.
Example: y-NH3 series can be observed only if fragment includes RKNQ or y-H2O only if fragment includes STED.';
COMMENT ON COLUMN public.fragmentation_rule.required_series_quality_level IS 'Indicates the quality level required to consider to corresponding ion series.
Valid values for this field are:
significant, highest_scoring.';
COMMENT ON COLUMN public.fragmentation_rule.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';
COMMENT ON COLUMN public.fragmentation_rule.fragment_series_id IS 'The corresponding and specific ion series.';
COMMENT ON COLUMN public.fragmentation_rule.required_series_id IS 'The ion series familly (a, b, c, x, y, z) required to be observed for this fragmentation rule.';


ALTER SEQUENCE public.fragmentation_rule_id_seq OWNED BY public.fragmentation_rule.id;

CREATE TABLE public.activation (
                type VARCHAR(100) NOT NULL,
                CONSTRAINT activation_pk PRIMARY KEY (type)
);
COMMENT ON TABLE public.activation IS 'Activation Method. The fragmentation method used for ion dissociation or fragmentation. See PSI:1000044';
COMMENT ON COLUMN public.activation.type IS 'HCD CID ETD.';


CREATE SEQUENCE public.instrument_id_seq;

CREATE TABLE public.instrument (
                id BIGINT NOT NULL DEFAULT nextval('public.instrument_id_seq'),
                name VARCHAR(100) NOT NULL,
                source VARCHAR(100) NOT NULL,
                serialized_properties TEXT,
                CONSTRAINT instrument_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.instrument IS 'This table lists the user mass spectrometers.';
COMMENT ON COLUMN public.instrument.name IS 'The instrument name.';
COMMENT ON COLUMN public.instrument.source IS 'The name of the instrument source.
Valid values for this field are:
ESI, MALDI.';
COMMENT ON COLUMN public.instrument.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


ALTER SEQUENCE public.instrument_id_seq OWNED BY public.instrument.id;

CREATE UNIQUE INDEX instrument_idx
 ON public.instrument
 ( name, source );

CREATE SEQUENCE public.instrument_config_id_seq;

CREATE TABLE public.instrument_config (
                id BIGINT NOT NULL DEFAULT nextval('public.instrument_config_id_seq'),
                name VARCHAR(100) NOT NULL,
                ms1_analyzer VARCHAR(100) NOT NULL,
                msn_analyzer VARCHAR(100),
                serialized_properties TEXT,
                instrument_id BIGINT NOT NULL,
                activation_type VARCHAR(100) NOT NULL,
                CONSTRAINT instrument_config_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.instrument_config IS 'This table stores the description of a mass spectrometer configuration.';
COMMENT ON COLUMN public.instrument_config.name IS 'The name of instrument configuration.';
COMMENT ON COLUMN public.instrument_config.ms1_analyzer IS 'The name of the MS analyzer used in this configuration.
Valid values for this field are:
4SECTOR, FTICR, FTMS, ISD, QIT, QUAD, TOF, TRAP.';
COMMENT ON COLUMN public.instrument_config.msn_analyzer IS 'The name of the MSn analyzer used in this configuration.
Valid values for this field are:
4SECTOR, FTICR, FTMS, ISD, QIT, QUAD, TOF, TRAP.';
COMMENT ON COLUMN public.instrument_config.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';
COMMENT ON COLUMN public.instrument_config.instrument_id IS 'The instrument this configuration refers to.';
COMMENT ON COLUMN public.instrument_config.activation_type IS 'The activation type corresponding to this instrument configuration.';


ALTER SEQUENCE public.instrument_config_id_seq OWNED BY public.instrument_config.id;

CREATE UNIQUE INDEX instrument_config_name_idx
 ON public.instrument_config
 ( name );

CREATE TABLE public.instrument_config_fragmentation_rule_map (
                instrument_config_id BIGINT NOT NULL,
                fragmentation_rule_id BIGINT NOT NULL,
                CONSTRAINT instrument_config_fragmentation_rule_map_pk PRIMARY KEY (instrument_config_id, fragmentation_rule_id)
);
COMMENT ON TABLE public.instrument_config_fragmentation_rule_map IS 'The set of fragmentation rules associated with this instrument configuration.';


CREATE SEQUENCE public.user_account_id_seq;

CREATE TABLE public.user_account (
                id BIGINT NOT NULL DEFAULT nextval('public.user_account_id_seq'),
                login VARCHAR(50) NOT NULL,
                password_hash VARCHAR NOT NULL,
                creation_mode VARCHAR(10) NOT NULL,
                serialized_properties TEXT,
                CONSTRAINT user_account_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.user_account IS 'User account information.
UNIQUE(login)';
COMMENT ON COLUMN public.user_account.login IS 'User login. The login must be unique within the database.';
COMMENT ON COLUMN public.user_account.password_hash IS 'hash of user password, using sha-256.';
COMMENT ON COLUMN public.user_account.creation_mode IS 'The mode used to create the account. It may be a
manual creation (from the application) or an automatic creation (i.e. LDAP import).
Valid values for this field are:
MANUAL, AUTO.';
COMMENT ON COLUMN public.user_account.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


ALTER SEQUENCE public.user_account_id_seq OWNED BY public.user_account.id;

CREATE UNIQUE INDEX user_account_login_idx
 ON public.user_account
 ( login );

CREATE TABLE public.raw_file (
                name VARCHAR(250) NOT NULL,
                extension VARCHAR(10) NOT NULL,
                directory VARCHAR(500),
                creation_timestamp TIMESTAMP,
                serialized_properties TEXT,
                instrument_id BIGINT NOT NULL,
                owner_id BIGINT NOT NULL,
                CONSTRAINT raw_file_pk PRIMARY KEY (name)
);
COMMENT ON TABLE public.raw_file IS 'Stores information about raw files that will be analyzed by Proline.';
COMMENT ON COLUMN public.raw_file.name IS 'The name of the raw file which serves as its identifier.
It should not contain an extension and be unique across all the database.';
COMMENT ON COLUMN public.raw_file.extension IS 'The raw file extension.';
COMMENT ON COLUMN public.raw_file.directory IS 'The path of the directory that contains the raw file.';
COMMENT ON COLUMN public.raw_file.creation_timestamp IS 'The timestamp corresponding to the creation date of the raw file.';
COMMENT ON COLUMN public.raw_file.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';
COMMENT ON COLUMN public.raw_file.instrument_id IS 'The instrument that has performed the raw file acquisition.';
COMMENT ON COLUMN public.raw_file.owner_id IS 'The owner of this raw file. The owner may have  permissions higher than other user accounts.';


CREATE SEQUENCE public.run_id_seq;

CREATE TABLE public.run (
                id BIGINT NOT NULL DEFAULT nextval('public.run_id_seq'),
                number INTEGER NOT NULL,
                run_start REAL NOT NULL,
                run_stop REAL NOT NULL,
                duration REAL NOT NULL,
                lc_method VARCHAR(250),
                ms_method VARCHAR(250),
                analyst VARCHAR(50),
                serialized_properties TEXT,
                raw_file_name VARCHAR(250) NOT NULL,
                CONSTRAINT run_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.run IS 'Stores information about a mass spectrometer acquisition also called "run".';
COMMENT ON COLUMN public.run.number IS 'The run number inside a given raw file.
Default is one because in the main case a raw file contains a single run.';
COMMENT ON COLUMN public.run.run_start IS 'The sarting time of the run in seconds.';
COMMENT ON COLUMN public.run.run_stop IS 'The ending time of the run in seconds.';
COMMENT ON COLUMN public.run.duration IS 'The duration of the run in seconds.';
COMMENT ON COLUMN public.run.lc_method IS 'The optional name of the Liquid Chromatography method which may have been used.';
COMMENT ON COLUMN public.run.ms_method IS 'The name of the Mass Spectrometry method which have been used.';
COMMENT ON COLUMN public.run.analyst IS 'The name of the analyst which launched the acquisition.';
COMMENT ON COLUMN public.run.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';
COMMENT ON COLUMN public.run.raw_file_name IS 'The raw file this acquisition belongs to.';


ALTER SEQUENCE public.run_id_seq OWNED BY public.run.id;

CREATE SEQUENCE public.project_id_seq;

CREATE TABLE public.project (
                id BIGINT NOT NULL DEFAULT nextval('public.project_id_seq'),
                name VARCHAR(250) NOT NULL,
                description VARCHAR(1000),
                creation_timestamp TIMESTAMP NOT NULL,
                serialized_properties TEXT,
                owner_id BIGINT NOT NULL,
                CONSTRAINT project_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.project IS 'A project contains multiple experiments relative to the same study or topic. Files associated with a project are stored in the repository in "/root/project_@{project_id}".';
COMMENT ON COLUMN public.project.name IS 'The name of the project as provided by the user.';
COMMENT ON COLUMN public.project.description IS 'The description of the project as provided by the user.';
COMMENT ON COLUMN public.project.creation_timestamp IS 'The timestamp corresponding to the creation date of the project.';
COMMENT ON COLUMN public.project.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';
COMMENT ON COLUMN public.project.owner_id IS 'The owner of this project. The owner is also a member of the project and then is represented in "project_user_account_map".';


ALTER SEQUENCE public.project_id_seq OWNED BY public.project.id;

CREATE UNIQUE INDEX project_name_owner_idx
 ON public.project
 ( name, owner_id );

CREATE TABLE public.raw_file_project_map (
                raw_file_name VARCHAR(250) NOT NULL,
                project_id BIGINT NOT NULL,
                serialized_properties TEXT,
                CONSTRAINT raw_file_project_map_pk PRIMARY KEY (raw_file_name, project_id)
);
COMMENT ON COLUMN public.raw_file_project_map.raw_file_name IS 'The name of the raw file which serves as its identifier.
It should not contain an extension and be unique across all the database.';
COMMENT ON COLUMN public.raw_file_project_map.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


CREATE SEQUENCE public.virtual_folder_id_seq;

CREATE TABLE public.virtual_folder (
                id BIGINT NOT NULL DEFAULT nextval('public.virtual_folder_id_seq'),
                name VARCHAR(250) NOT NULL,
                path VARCHAR(500),
                serialized_properties TEXT,
                parent_virtual_folder_id BIGINT,
                project_id BIGINT NOT NULL,
                CONSTRAINT virtual_folder_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.virtual_folder IS 'A virtual folder organize documents in the database. This documents are virtual documents created only in the database.';
COMMENT ON COLUMN public.virtual_folder.name IS 'The folder name.';
COMMENT ON COLUMN public.virtual_folder.path IS 'NOT YET USED : the path to this folder. This path can be created from the parent relationship.';
COMMENT ON COLUMN public.virtual_folder.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';
COMMENT ON COLUMN public.virtual_folder.parent_virtual_folder_id IS 'The parent folder. Null if this folder is rooted in the project folder.';
COMMENT ON COLUMN public.virtual_folder.project_id IS 'The project this virtual folder is related to.';


ALTER SEQUENCE public.virtual_folder_id_seq OWNED BY public.virtual_folder.id;

CREATE TABLE public.project_user_account_map (
                project_id BIGINT NOT NULL,
                user_account_id BIGINT NOT NULL,
                serialized_properties TEXT,
                CONSTRAINT project_user_account_map_pk PRIMARY KEY (project_id, user_account_id)
);
COMMENT ON TABLE public.project_user_account_map IS 'The mapping between project and user_account records.';
COMMENT ON COLUMN public.project_user_account_map.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


CREATE SEQUENCE public.quant_method_id_seq;

CREATE TABLE public.quant_method (
                id BIGINT NOT NULL DEFAULT nextval('public.quant_method_id_seq'),
                name VARCHAR(1000) NOT NULL,
                type VARCHAR(20) NOT NULL,
                abundance_unit VARCHAR(30) NOT NULL,
                serialized_properties TEXT,
                CONSTRAINT quant_method_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.quant_method IS 'An enumeration of available quantitative methods.';
COMMENT ON COLUMN public.quant_method.name IS 'A unique name for this quantitative method as defined by the system.';
COMMENT ON COLUMN public.quant_method.type IS 'Indicates the type of the molecular labeling which may have been used for quantitation.
Valid values for this field are:
isobaric_labeling, residue_labeling, atom_labeling, label_free.';
COMMENT ON COLUMN public.quant_method.abundance_unit IS 'The unit corresponding to the measured abundance.
Valid values for this field are:
spectral_counting, reporter_ion, feature, xic (mrm), mixed.';
COMMENT ON COLUMN public.quant_method.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


ALTER SEQUENCE public.quant_method_id_seq OWNED BY public.quant_method.id;

CREATE UNIQUE INDEX quant_method_name_idx
 ON public.quant_method
 ( name );

CREATE SEQUENCE public.data_set_id_seq;

CREATE TABLE public.data_set (
                id BIGINT NOT NULL DEFAULT nextval('public.data_set_id_seq'),
                number INTEGER NOT NULL,
                name VARCHAR NOT NULL,
                description VARCHAR(10000),
                type VARCHAR NOT NULL,
                keywords VARCHAR,
                creation_timestamp TIMESTAMP NOT NULL,
                modification_log TEXT,
                children_count INTEGER DEFAULT 0 NOT NULL,
                serialized_properties TEXT,
                result_set_id BIGINT,
                result_summary_id BIGINT,
                aggregation_id BIGINT,
                fractionation_id BIGINT,
                quant_method_id BIGINT,
                parent_dataset_id BIGINT,
                project_id BIGINT NOT NULL,
                CONSTRAINT data_set_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.data_set IS 'A data_set is an abstract entity which can describe identification and quantitative data or an aggregation of such data_sets (see type column for more information). Each data_set provides references to the corresponding result_set/result_summary in the MSIdb of the related project.';
COMMENT ON COLUMN public.data_set.number IS 'The data_set number which is unique for the children of a given parent data_set.';
COMMENT ON COLUMN public.data_set.name IS 'The name of the data_set as defined by the user.';
COMMENT ON COLUMN public.data_set.description IS 'The description of the data_set as defined by the user.';
COMMENT ON COLUMN public.data_set.type IS 'There are two concrete types which can only be defined for the leaves of data_set tree: IDENTIFICATION and QUANTITATION. Each node of the data_set tree has to be typed as AGGREGATE.
Valid values are for this field are:
IDENTIFICATION, QUANTITATION, AGGREGATE, TRASH.';
COMMENT ON COLUMN public.data_set.keywords IS 'A list of comma separated keywords that are provided by the user in order to tag the data_sets. These keywords can be used at the application level to search/filter data_sets. For instance one can set a keyword as the sub-project name of the related project, and then retrieve quickly all the data_sets corresponding to this sub-project.';
COMMENT ON COLUMN public.data_set.creation_timestamp IS 'The timestamp corresponding to the creation date of the data_set.';
COMMENT ON COLUMN public.data_set.modification_log IS 'This field can be used to store an history/log of the changes/processings performed on this data_set. Such changes could be validation algorithms, filters, manual user selection...';
COMMENT ON COLUMN public.data_set.children_count IS 'The number of fractions associated with this data_set.';
COMMENT ON COLUMN public.data_set.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';
COMMENT ON COLUMN public.data_set.result_set_id IS 'The corresponding result_set in the MSIdb of the same project.';
COMMENT ON COLUMN public.data_set.result_summary_id IS 'The corresponding result_summary in the MSIdb of the same project.';
COMMENT ON COLUMN public.data_set.aggregation_id IS 'Defines the child nature of AGGREGATION data_sets.';
COMMENT ON COLUMN public.data_set.fractionation_id IS 'If the data_set is associated with a fractionatin step, this field allows to define its type.';
COMMENT ON COLUMN public.data_set.quant_method_id IS 'An optional quantitative method associated with data_sets of QUANTITATION type.';
COMMENT ON COLUMN public.data_set.parent_dataset_id IS 'An optional parent data_set which has to be of AGGREGATION type.';
COMMENT ON COLUMN public.data_set.project_id IS 'The project this data_set belongs to.';


ALTER SEQUENCE public.data_set_id_seq OWNED BY public.data_set.id;

CREATE TABLE public.run_identification (
                id BIGINT NOT NULL,
                serialized_properties TEXT,
                run_id BIGINT,
                raw_file_name VARCHAR(250),
                CONSTRAINT run_identification_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.run_identification IS 'Defines the run that corresponds to the identification of a set of MS/MS spectra.';
COMMENT ON COLUMN public.run_identification.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';
COMMENT ON COLUMN public.run_identification.run_id IS 'The run this identification refers to.';
COMMENT ON COLUMN public.run_identification.raw_file_name IS 'The raw file whose the peaklist has been generated from.';


CREATE SEQUENCE public.sample_analysis_id_seq;

CREATE TABLE public.sample_analysis (
                id BIGINT NOT NULL DEFAULT nextval('public.sample_analysis_id_seq'),
                number INTEGER NOT NULL,
                serialized_properties TEXT,
                quantitation_id BIGINT NOT NULL,
                CONSTRAINT sample_analysis_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.sample_analysis IS 'Represents each analytical replicates of the associated biological sample. analytical replicates does not necessarily means MS run since labelled samples are analysed in MS in a unique run.';
COMMENT ON COLUMN public.sample_analysis.number IS 'The sample analysis number which is unique for a given biological sample.
TODO: move this column to the biological_sample_sample_analysis_map table and check the UNIQUE constraint there';
COMMENT ON COLUMN public.sample_analysis.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';
COMMENT ON COLUMN public.sample_analysis.quantitation_id IS 'The quantitation this sample analysis is related to.';


ALTER SEQUENCE public.sample_analysis_id_seq OWNED BY public.sample_analysis.id;

CREATE SEQUENCE public.master_quant_channel_id_seq;

CREATE TABLE public.master_quant_channel (
                id BIGINT NOT NULL DEFAULT nextval('public.master_quant_channel_id_seq'),
                number INTEGER NOT NULL,
                name VARCHAR(100) NOT NULL,
                serialized_properties TEXT,
                lcms_map_set_id BIGINT,
                quant_result_summary_id BIGINT,
                quantitation_id BIGINT NOT NULL,
                CONSTRAINT master_quant_channel_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.master_quant_channel IS 'A master quant channel is a consensus over multiple quant channels (merged results). In the context of LC-MS analysis it may correspond to the master map of a given map set.';
COMMENT ON COLUMN public.master_quant_channel.number IS 'The master quant channel number which is unique for a given quantitation.';
COMMENT ON COLUMN public.master_quant_channel.name IS 'A name for this master quant channel as defined by the user.';
COMMENT ON COLUMN public.master_quant_channel.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';
COMMENT ON COLUMN public.master_quant_channel.lcms_map_set_id IS 'The corresponding LC-MS map set in the context of quantitations based on LC-MS feature extraction.';
COMMENT ON COLUMN public.master_quant_channel.quant_result_summary_id IS 'The corresponding quant result summary which stores the data of this master quant channel.';
COMMENT ON COLUMN public.master_quant_channel.quantitation_id IS 'The quantitation this master quant channel belongs to.';


ALTER SEQUENCE public.master_quant_channel_id_seq OWNED BY public.master_quant_channel.id;

CREATE UNIQUE INDEX master_quant_channel_number_idx
 ON public.master_quant_channel
 ( quantitation_id, number );

CREATE SEQUENCE public.quant_label_id_seq;

CREATE TABLE public.quant_label (
                id BIGINT NOT NULL DEFAULT nextval('public.quant_label_id_seq'),
                type VARCHAR(16) NOT NULL,
                name VARCHAR(10) NOT NULL,
                serialized_properties TEXT,
                quant_method_id BIGINT NOT NULL,
                CONSTRAINT quant_label_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.quant_label IS 'An enumeration of existing quantitation labels.';
COMMENT ON COLUMN public.quant_label.type IS 'The type of the label.
Valid values for this field are:
isobaric, residue_isotopic, atom_isotopic.';
COMMENT ON COLUMN public.quant_label.name IS 'A name identifying the label.
For instance
- isobaric labels => 114/115/116/117
- isotopic labels => ICAT_C12/ICAT_C13';
COMMENT ON COLUMN public.quant_label.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';
COMMENT ON COLUMN public.quant_label.quant_method_id IS 'The quantitative method this label refers to.';


ALTER SEQUENCE public.quant_label_id_seq OWNED BY public.quant_label.id;

CREATE SEQUENCE public.biological_sample_id_seq;

CREATE TABLE public.biological_sample (
                id BIGINT NOT NULL DEFAULT nextval('public.biological_sample_id_seq'),
                number INTEGER NOT NULL,
                name VARCHAR(100) NOT NULL,
                serialized_properties TEXT,
                quantitation_id BIGINT NOT NULL,
                CONSTRAINT biological_sample_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.biological_sample IS 'A biological sample under study.';
COMMENT ON COLUMN public.biological_sample.number IS 'The biological sample number which is unique for a given quantitation.';
COMMENT ON COLUMN public.biological_sample.name IS 'A name for this biological sample as defined by the user.';
COMMENT ON COLUMN public.biological_sample.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';
COMMENT ON COLUMN public.biological_sample.quantitation_id IS 'The quantitation this biologicial sample is related to.';


ALTER SEQUENCE public.biological_sample_id_seq OWNED BY public.biological_sample.id;

CREATE UNIQUE INDEX biological_sample_number_idx
 ON public.biological_sample
 ( quantitation_id, number );

CREATE TABLE public.biological_sample_sample_analysis_map (
                biological_sample_id BIGINT NOT NULL,
                sample_analysis_id BIGINT NOT NULL,
                CONSTRAINT biological_sample_sample_analysis_map_pk PRIMARY KEY (biological_sample_id, sample_analysis_id)
);
COMMENT ON TABLE public.biological_sample_sample_analysis_map IS 'The list of sample analyses performed for this biological sample.';


CREATE SEQUENCE public.group_setup_id_seq;

CREATE TABLE public.group_setup (
                id BIGINT NOT NULL DEFAULT nextval('public.group_setup_id_seq'),
                number INTEGER NOT NULL,
                name VARCHAR(100) NOT NULL,
                serialized_properties TEXT,
                quantitation_id BIGINT NOT NULL,
                CONSTRAINT group_setup_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.group_setup IS 'A group setup is a user defined entity allowing to define the way biological groups are compared.';
COMMENT ON COLUMN public.group_setup.number IS 'The group setup number which is unique for a given quantitation.';
COMMENT ON COLUMN public.group_setup.name IS 'A name for this group setup as defined by the user.';
COMMENT ON COLUMN public.group_setup.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';
COMMENT ON COLUMN public.group_setup.quantitation_id IS 'The quantitation this group setup is related to.';


ALTER SEQUENCE public.group_setup_id_seq OWNED BY public.group_setup.id;

CREATE UNIQUE INDEX group_setup_number_idx
 ON public.group_setup
 ( quantitation_id, number );

CREATE SEQUENCE public.biological_group_id_seq;

CREATE TABLE public.biological_group (
                id BIGINT NOT NULL DEFAULT nextval('public.biological_group_id_seq'),
                number INTEGER NOT NULL,
                name VARCHAR(100) NOT NULL,
                serialized_properties TEXT,
                quantitation_id BIGINT NOT NULL,
                CONSTRAINT biological_group_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.biological_group IS 'A group of related biological samples. A biological group is a generic concept that can be used to represents physiological conditions, pool or sample preparation conditions.';
COMMENT ON COLUMN public.biological_group.number IS 'The biological group number which is unique for a given quantitation.';
COMMENT ON COLUMN public.biological_group.name IS 'A name for this biological group as defined by the user.';
COMMENT ON COLUMN public.biological_group.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';
COMMENT ON COLUMN public.biological_group.quantitation_id IS 'The quantitation this biologicial group is related to.';


ALTER SEQUENCE public.biological_group_id_seq OWNED BY public.biological_group.id;

CREATE UNIQUE INDEX biological_group_number_idx
 ON public.biological_group
 ( quantitation_id, number );

CREATE TABLE public.group_setup_biological_group_map (
                group_setup_id BIGINT NOT NULL,
                biological_group_id BIGINT NOT NULL,
                CONSTRAINT group_setup_biological_group_map_pk PRIMARY KEY (group_setup_id, biological_group_id)
);
COMMENT ON TABLE public.group_setup_biological_group_map IS 'The list of biological groups associated with this group setup.';


CREATE SEQUENCE public.ratio_definition_id_seq;

CREATE TABLE public.ratio_definition (
                id BIGINT NOT NULL DEFAULT nextval('public.ratio_definition_id_seq'),
                number INTEGER NOT NULL,
                numerator_id BIGINT NOT NULL,
                denominator_id BIGINT NOT NULL,
                group_setup_id BIGINT NOT NULL,
                CONSTRAINT ratio_definition_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.ratio_definition IS 'The definition of a quantitative ratio. A quantitative ratio is calculated from two biological groups that are considered as the numerator and denominator of the ratio formula.';
COMMENT ON COLUMN public.ratio_definition.number IS 'The ratio definition number which is unique for a given group setup. Allows representation of sequence of ratios.';
COMMENT ON COLUMN public.ratio_definition.numerator_id IS 'The biological group whose the abundances are used as numerator in the ratio computation.';
COMMENT ON COLUMN public.ratio_definition.denominator_id IS 'The biological group whose the abundances are used as denominator in the ratio computation.';
COMMENT ON COLUMN public.ratio_definition.group_setup_id IS 'The group setup this ratio definition belongs to.';


ALTER SEQUENCE public.ratio_definition_id_seq OWNED BY public.ratio_definition.id;

CREATE UNIQUE INDEX ratio_definition_number_idx
 ON public.ratio_definition
 ( group_setup_id, number );

CREATE TABLE public.biological_group_biological_sample_item (
                biological_group_id BIGINT NOT NULL,
                biological_sample_id BIGINT NOT NULL,
                CONSTRAINT biological_group_biological_sample_item_pk PRIMARY KEY (biological_group_id, biological_sample_id)
);
COMMENT ON TABLE public.biological_group_biological_sample_item IS 'The list of biological samples associated with this biological group.';


CREATE SEQUENCE public.external_db_id_seq;

CREATE TABLE public.external_db (
                id BIGINT NOT NULL DEFAULT nextval('public.external_db_id_seq'),
                name VARCHAR(500) NOT NULL,
                connection_mode VARCHAR(50) NOT NULL,
                username VARCHAR(50),
                password VARCHAR(50),
                host VARCHAR(100),
                port INTEGER,
                type VARCHAR(100) NOT NULL,
                version VARCHAR(50) NOT NULL,
                is_busy BOOLEAN NOT NULL,
                serialized_properties TEXT,
                CONSTRAINT external_db_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.external_db IS 'Contains connexion properties for databases associated with projects.
Databases allowing multiple instances are necessarily associated with projects.
Singleton databases (PDIdb, PSdb, ePims, ...) are also define through this table without any specific association to any projects.';
COMMENT ON COLUMN public.external_db.name IS 'The name of the database on the DBMS server.
Could be the path/name of the database in case of embedded file DB engine (H2 / sqlite...)';
COMMENT ON COLUMN public.external_db.connection_mode IS 'Specify type of connection used for this DB. Possible values: HOST, MEMORY or FILE';
COMMENT ON COLUMN public.external_db.username IS 'The user name to use for database connection.';
COMMENT ON COLUMN public.external_db.password IS 'The password to use for database connection.';
COMMENT ON COLUMN public.external_db.host IS 'The hostname of the DBMS server.';
COMMENT ON COLUMN public.external_db.port IS 'The port number of the DBMS server.';
COMMENT ON COLUMN public.external_db.type IS 'Type of database schema.
Valid values for this field are:
MSI, LCMS, PDI, PS.';
COMMENT ON COLUMN public.external_db.version IS 'Indicates the schema version of the referenced db. For instance, it could correspond to admin_infos.model_version of an MSIdb.';
COMMENT ON COLUMN public.external_db.is_busy IS 'Informs about the busy status of the corresponding external DB. If set to true then it tells that the external DB is busy and should not be used at the moment. Could be useful if the external DB is implemented using an embedded technology like SQLite.';
COMMENT ON COLUMN public.external_db.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).
Note: it could store the driver name and other connection properties needed by the driver.';


ALTER SEQUENCE public.external_db_id_seq OWNED BY public.external_db.id;

CREATE TABLE public.project_db_map (
                project_id BIGINT NOT NULL,
                external_db_id BIGINT NOT NULL,
                CONSTRAINT project_db_map_pk PRIMARY KEY (project_id, external_db_id)
);
COMMENT ON TABLE public.project_db_map IS 'The mapping between project and external_db records.';


CREATE TABLE public.object_tree_schema (
                name VARCHAR(1000) NOT NULL,
                type VARCHAR(10) NOT NULL,
                version VARCHAR(100),
                schema TEXT NOT NULL,
                description VARCHAR(1000),
                serialized_properties TEXT,
                CONSTRAINT object_tree_schema_pk PRIMARY KEY (name)
);
COMMENT ON COLUMN public.object_tree_schema.type IS 'XSD or JSON or TSV (tabulated separated values, in this case schema column contains column header)';
COMMENT ON COLUMN public.object_tree_schema.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).
i.e. for document table a schema property may be the document file extension (.tsv, .protML, .pepML)';


CREATE SEQUENCE public.object_tree_id_seq;

CREATE TABLE public.object_tree (
                id BIGINT NOT NULL DEFAULT nextval('public.object_tree_id_seq'),
                serialized_data TEXT NOT NULL,
                serialized_properties TEXT,
                schema_name VARCHAR(1000) NOT NULL,
                CONSTRAINT object_tree_pk PRIMARY KEY (id)
);


ALTER SEQUENCE public.object_tree_id_seq OWNED BY public.object_tree.id;

CREATE SEQUENCE public.document_id_seq;

CREATE TABLE public.document (
                id BIGINT NOT NULL DEFAULT nextval('public.document_id_seq'),
                name VARCHAR(250) NOT NULL,
                description VARCHAR(1000),
                keywords VARCHAR(250),
                creation_timestamp TIMESTAMP NOT NULL,
                modification_timestamp TIMESTAMP,
                creation_log TEXT,
                modification_log TEXT,
                serialized_properties TEXT,
                object_tree_id BIGINT NOT NULL,
                virtual_folder_id BIGINT NOT NULL,
                project_id BIGINT NOT NULL,
                schema_name VARCHAR(1000) NOT NULL,
                CONSTRAINT document_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.document IS 'A virtual document stored on the database. The content of the document is stored in the ''''object_tree'''' associated with the document.';
COMMENT ON COLUMN public.document.name IS 'The name of the document.';
COMMENT ON COLUMN public.document.description IS 'The optional description of the document.';
COMMENT ON COLUMN public.document.keywords IS 'A list of comma separated keywords that are provided by the user in order to tag the document.';
COMMENT ON COLUMN public.document.creation_timestamp IS 'The timestamp corresponding to the creation date of the document.';
COMMENT ON COLUMN public.document.modification_timestamp IS 'The timestamp corresponding to the modification date of the document.';
COMMENT ON COLUMN public.document.creation_log IS 'A description provided by the system relative to the operation that generated the document.';
COMMENT ON COLUMN public.document.modification_log IS 'A description relative to the processings applied to the docuement after its ceation.';
COMMENT ON COLUMN public.document.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';
COMMENT ON COLUMN public.document.object_tree_id IS 'The object tree that corresponds to the document content.';
COMMENT ON COLUMN public.document.virtual_folder_id IS 'The folder this document belongs to.';
COMMENT ON COLUMN public.document.project_id IS 'The project this document belongs to.';
COMMENT ON COLUMN public.document.schema_name IS 'The name of the schema describing the content of this document. It may be displayed to the user as the document name extension.';


ALTER SEQUENCE public.document_id_seq OWNED BY public.document.id;

CREATE SEQUENCE public.quant_channel_id_seq;

CREATE TABLE public.quant_channel (
                id BIGINT NOT NULL DEFAULT nextval('public.quant_channel_id_seq'),
                number INTEGER NOT NULL,
                name VARCHAR(100),
                context_key VARCHAR(100) NOT NULL,
                serialized_properties TEXT,
                lcms_map_id BIGINT,
                ident_result_summary_id BIGINT NOT NULL,
                run_id BIGINT,
                quant_label_id BIGINT,
                sample_analysis_id BIGINT NOT NULL,
                biological_sample_id BIGINT NOT NULL,
                master_quant_channel_id BIGINT NOT NULL,
                quantitation_id BIGINT NOT NULL,
                CONSTRAINT quant_channel_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.quant_channel IS 'A quant channel represents all quantified peptides from a single replicate of a single fraction of a biological sample.';
COMMENT ON COLUMN public.quant_channel.number IS 'The quant channel number which is unique for a given master quant channel.';
COMMENT ON COLUMN public.quant_channel.name IS 'A name for this quant channel as defined by the user.';
COMMENT ON COLUMN public.quant_channel.context_key IS 'A string which serves as a unique key for the quant channel in the context of a given quantitation. It is obtained by the concatenation of
biological_sample.number and sample_analysis.number (separated by a dot character). This field is UNIQUE for a given master_quant_channel and a given quant_label.';
COMMENT ON COLUMN public.quant_channel.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';
COMMENT ON COLUMN public.quant_channel.lcms_map_id IS 'The optional corresponding LC-MS map in the LCMSdb of the same project.';
COMMENT ON COLUMN public.quant_channel.ident_result_summary_id IS 'The corresponding identification summary in the MSIdb of the same project.';
COMMENT ON COLUMN public.quant_channel.run_id IS 'The run this quant channel refers to.';
COMMENT ON COLUMN public.quant_channel.quant_label_id IS 'The optional label used in the quantiation process.';
COMMENT ON COLUMN public.quant_channel.sample_analysis_id IS 'The sample analysis corresponding to this quant channel.';
COMMENT ON COLUMN public.quant_channel.biological_sample_id IS 'The biological sample corresponding to this quant channel.';
COMMENT ON COLUMN public.quant_channel.master_quant_channel_id IS 'The master quant channel this quant channel belongs to.';
COMMENT ON COLUMN public.quant_channel.quantitation_id IS 'The quantitation this quant channel belongs to.';


ALTER SEQUENCE public.quant_channel_id_seq OWNED BY public.quant_channel.id;

CREATE UNIQUE INDEX quant_channel_context_idx
 ON public.quant_channel
 ( master_quant_channel_id, context_key, quant_label_id );

CREATE UNIQUE INDEX quant_channel_number_idx
 ON public.quant_channel
 ( master_quant_channel_id, number );

CREATE TABLE public.admin_infos (
                model_version VARCHAR(50) NOT NULL,
                db_creation_date TIMESTAMP,
                model_update_date TIMESTAMP,
                configuration TEXT NOT NULL,
                CONSTRAINT admin_infos_pkey PRIMARY KEY (model_version)
);
COMMENT ON TABLE public.admin_infos IS 'This table gives information about the current database model.';
COMMENT ON COLUMN public.admin_infos.model_version IS 'The version number of the database schema.';
COMMENT ON COLUMN public.admin_infos.db_creation_date IS 'The creation date of the database.';
COMMENT ON COLUMN public.admin_infos.model_update_date IS 'The modification date of the database schema.';
COMMENT ON COLUMN public.admin_infos.configuration IS 'The configuration properties. configuration contains :
  * absolute root path for shared documents, organized by projects';


ALTER TABLE public.data_set ADD CONSTRAINT aggregation_dataset_fk
FOREIGN KEY (aggregation_id)
REFERENCES public.aggregation (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.data_set ADD CONSTRAINT fractionation_dataset_fk
FOREIGN KEY (fractionation_id)
REFERENCES public.fractionation (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peaklist_software ADD CONSTRAINT spec_title_parsing_rule_peaklist_software_fk
FOREIGN KEY (spec_title_parsing_rule_id)
REFERENCES public.spec_title_parsing_rule (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.enzyme_cleavage ADD CONSTRAINT enzyme_enzyme_cleavage_fk
FOREIGN KEY (enzyme_id)
REFERENCES public.enzyme (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.fragmentation_rule ADD CONSTRAINT required_series_fragmentation_rule_fk
FOREIGN KEY (required_series_id)
REFERENCES public.fragmentation_series (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.fragmentation_rule ADD CONSTRAINT fragmentation_series_fragmentation_rule_fk
FOREIGN KEY (fragment_series_id)
REFERENCES public.fragmentation_series (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.instrument_config_fragmentation_rule_map ADD CONSTRAINT fragmentation_rule_instrument_config_fragmentation_rule_map_fk
FOREIGN KEY (fragmentation_rule_id)
REFERENCES public.fragmentation_rule (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.instrument_config ADD CONSTRAINT activation_instrument_config_fk
FOREIGN KEY (activation_type)
REFERENCES public.activation (type)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.instrument_config ADD CONSTRAINT instrument_instrument_config_fk
FOREIGN KEY (instrument_id)
REFERENCES public.instrument (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.raw_file ADD CONSTRAINT instrument_raw_file_fk
FOREIGN KEY (instrument_id)
REFERENCES public.instrument (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.instrument_config_fragmentation_rule_map ADD CONSTRAINT instrument_config_instrument_config_fragmentation_rule_map_fk
FOREIGN KEY (instrument_config_id)
REFERENCES public.instrument_config (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.project ADD CONSTRAINT user_account_project_fk
FOREIGN KEY (owner_id)
REFERENCES public.user_account (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.project_user_account_map ADD CONSTRAINT user_account_project_user_map_fk
FOREIGN KEY (user_account_id)
REFERENCES public.user_account (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.raw_file ADD CONSTRAINT user_account_raw_file_fk
FOREIGN KEY (owner_id)
REFERENCES public.user_account (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.run ADD CONSTRAINT raw_file_run_fk
FOREIGN KEY (raw_file_name)
REFERENCES public.raw_file (name)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.run_identification ADD CONSTRAINT raw_file_run_identification_fk
FOREIGN KEY (raw_file_name)
REFERENCES public.raw_file (name)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.raw_file_project_map ADD CONSTRAINT raw_file_raw_file_project_map_fk
FOREIGN KEY (raw_file_name)
REFERENCES public.raw_file (name)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.run_identification ADD CONSTRAINT run_run_identification_fk
FOREIGN KEY (run_id)
REFERENCES public.run (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.quant_channel ADD CONSTRAINT run_quant_channel_fk
FOREIGN KEY (run_id)
REFERENCES public.run (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.document ADD CONSTRAINT project_document_fk
FOREIGN KEY (project_id)
REFERENCES public.project (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.project_user_account_map ADD CONSTRAINT project_project_user_map_fk
FOREIGN KEY (project_id)
REFERENCES public.project (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.virtual_folder ADD CONSTRAINT project_virtual_folder_fk
FOREIGN KEY (project_id)
REFERENCES public.project (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.project_db_map ADD CONSTRAINT project_project_db_map_fk
FOREIGN KEY (project_id)
REFERENCES public.project (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.data_set ADD CONSTRAINT project_dataset_fk
FOREIGN KEY (project_id)
REFERENCES public.project (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.raw_file_project_map ADD CONSTRAINT project_raw_file_project_map_fk
FOREIGN KEY (project_id)
REFERENCES public.project (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.document ADD CONSTRAINT virtual_folder_document_fk
FOREIGN KEY (virtual_folder_id)
REFERENCES public.virtual_folder (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.virtual_folder ADD CONSTRAINT virtual_folder_virtual_folder_fk
FOREIGN KEY (parent_virtual_folder_id)
REFERENCES public.virtual_folder (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.quant_label ADD CONSTRAINT quant_method_quant_label_fk
FOREIGN KEY (quant_method_id)
REFERENCES public.quant_method (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.data_set ADD CONSTRAINT quant_method_dataset_fk
FOREIGN KEY (quant_method_id)
REFERENCES public.quant_method (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.master_quant_channel ADD CONSTRAINT dataset_master_quant_channel_fk
FOREIGN KEY (quantitation_id)
REFERENCES public.data_set (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.group_setup ADD CONSTRAINT dataset_group_setup_fk
FOREIGN KEY (quantitation_id)
REFERENCES public.data_set (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.quant_channel ADD CONSTRAINT dataset_quant_channel_fk
FOREIGN KEY (quantitation_id)
REFERENCES public.data_set (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.biological_sample ADD CONSTRAINT dataset_biological_sample_fk
FOREIGN KEY (quantitation_id)
REFERENCES public.data_set (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.sample_analysis ADD CONSTRAINT dataset_sample_analysis_fk
FOREIGN KEY (quantitation_id)
REFERENCES public.data_set (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.run_identification ADD CONSTRAINT dataset_run_identification_fk
FOREIGN KEY (id)
REFERENCES public.data_set (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.data_set ADD CONSTRAINT dataset_dataset_fk
FOREIGN KEY (parent_dataset_id)
REFERENCES public.data_set (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.biological_group ADD CONSTRAINT data_set_biological_group_fk
FOREIGN KEY (quantitation_id)
REFERENCES public.data_set (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.quant_channel ADD CONSTRAINT sample_analysis_quant_channel_fk
FOREIGN KEY (sample_analysis_id)
REFERENCES public.sample_analysis (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.biological_sample_sample_analysis_map ADD CONSTRAINT sample_analysis_biological_sample_sample_analysis_map_fk
FOREIGN KEY (sample_analysis_id)
REFERENCES public.sample_analysis (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.quant_channel ADD CONSTRAINT master_quant_channel_quant_channel_fk
FOREIGN KEY (master_quant_channel_id)
REFERENCES public.master_quant_channel (id)
ON DELETE CASCADE
ON UPDATE CASCADE
NOT DEFERRABLE;

ALTER TABLE public.quant_channel ADD CONSTRAINT quant_label_quant_channel_fk
FOREIGN KEY (quant_label_id)
REFERENCES public.quant_label (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.biological_group_biological_sample_item ADD CONSTRAINT biological_sample_biological_group_biological_sample_item_fk
FOREIGN KEY (biological_sample_id)
REFERENCES public.biological_sample (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.quant_channel ADD CONSTRAINT biological_sample_quant_channel_fk
FOREIGN KEY (biological_sample_id)
REFERENCES public.biological_sample (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.biological_sample_sample_analysis_map ADD CONSTRAINT biological_sample_biological_sample_sample_analysis_map_fk
FOREIGN KEY (biological_sample_id)
REFERENCES public.biological_sample (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.ratio_definition ADD CONSTRAINT group_setup_ratio_definition_fk
FOREIGN KEY (group_setup_id)
REFERENCES public.group_setup (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.group_setup_biological_group_map ADD CONSTRAINT group_setup_group_setup_biological_group_map_fk
FOREIGN KEY (group_setup_id)
REFERENCES public.group_setup (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.biological_group_biological_sample_item ADD CONSTRAINT biological_group_biological_group_biological_sample_item_fk
FOREIGN KEY (biological_group_id)
REFERENCES public.biological_group (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.ratio_definition ADD CONSTRAINT biological_group_ratio_numerator_fk
FOREIGN KEY (numerator_id)
REFERENCES public.biological_group (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.ratio_definition ADD CONSTRAINT biological_group_ratio_denominator_fk
FOREIGN KEY (denominator_id)
REFERENCES public.biological_group (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.group_setup_biological_group_map ADD CONSTRAINT biological_group_group_setup_biological_group_map_fk
FOREIGN KEY (biological_group_id)
REFERENCES public.biological_group (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.project_db_map ADD CONSTRAINT external_db_project_db_map_fk
FOREIGN KEY (external_db_id)
REFERENCES public.external_db (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.object_tree ADD CONSTRAINT object_tree_schema_object_tree_fk
FOREIGN KEY (schema_name)
REFERENCES public.object_tree_schema (name)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.document ADD CONSTRAINT object_tree_schema_document_fk
FOREIGN KEY (schema_name)
REFERENCES public.object_tree_schema (name)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.document ADD CONSTRAINT object_tree_document_fk
FOREIGN KEY (object_tree_id)
REFERENCES public.object_tree (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;
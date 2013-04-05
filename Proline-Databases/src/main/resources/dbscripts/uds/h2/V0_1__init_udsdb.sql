
CREATE TABLE public.aggregation (
                id IDENTITY NOT NULL,
                child_nature VARCHAR NOT NULL,
                CONSTRAINT aggregation_pk PRIMARY KEY (id)
);
COMMENT ON COLUMN public.aggregation.child_nature IS 'Describes the nature of the corresponding level of aggregation. Valid values are: SAMPLE_ANALYSIS, QUANTITATION_FRACTION, BIOLOGICAL_SAMPLE, BIOLOGICAL_GROUP, OTHER.';


CREATE UNIQUE INDEX public.aggregation_child_nature_idx
 ON public.aggregation
 ( child_nature );

CREATE TABLE public.fractionation (
                id IDENTITY NOT NULL,
                type VARCHAR NOT NULL,
                CONSTRAINT fractionation_pk PRIMARY KEY (id)
);
COMMENT ON COLUMN public.fractionation.type IS 'Describes the type of the separation used for the analysis of the sample. Valid values are: PROTEIN, PEPTIDE, OTHER, NONE.';


CREATE UNIQUE INDEX public.fractionation_type_idx
 ON public.fractionation
 ( type );

CREATE TABLE public.protein_match_decoy_rule (
                id IDENTITY NOT NULL,
                name VARCHAR(100) NOT NULL,
                ac_decoy_tag VARCHAR(100) NOT NULL,
                CONSTRAINT protein_match_decoy_rule_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.protein_match_decoy_rule IS 'Stores rules that can be used to determine if a protein_match is decoy or not. If the accession number of the protein_match contains the tag (ac_decoy_tag) it is considered as "decoy".';
COMMENT ON COLUMN public.protein_match_decoy_rule.name IS 'The name of the rule.';
COMMENT ON COLUMN public.protein_match_decoy_rule.ac_decoy_tag IS 'A string which is used to make the distinction between decoy and target protein matches. This string is only added to the accesion number of decoy protein matches.';


CREATE TABLE public.spec_title_parsing_rule (
                id IDENTITY NOT NULL,
                raw_file_name VARCHAR(100),
                first_cycle VARCHAR(100),
                last_cycle VARCHAR(100),
                first_scan VARCHAR(100),
                last_scan VARCHAR(100),
                first_time VARCHAR(100),
                last_time VARCHAR(100),
                name VARCHAR(100) NOT NULL,
                CONSTRAINT spec_title_parsing_rule_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.spec_title_parsing_rule IS 'Describe rules used to parse the content of the MS2 spectrum description. Note: using the attribute names of  the spectrum table enables an easier implementation.';


CREATE TABLE public.peaklist_software (
                id IDENTITY NOT NULL,
                name VARCHAR(100) NOT NULL,
                version VARCHAR(100),
                serialized_properties LONGVARCHAR,
                spec_title_parsing_rule_id INTEGER NOT NULL,
                CONSTRAINT peaklist_software_pk PRIMARY KEY (id)
);
COMMENT ON COLUMN public.peaklist_software.name IS 'The name of the software used to generate the peaklist. Examples: extract_msn, Mascot Distiller, mascot.dll';
COMMENT ON COLUMN public.peaklist_software.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


CREATE TABLE public.enzyme (
                id IDENTITY NOT NULL,
                name VARCHAR(100) NOT NULL,
                cleavage_regexp VARCHAR(50),
                is_independant BOOLEAN NOT NULL,
                is_semi_specific BOOLEAN NOT NULL,
                CONSTRAINT enzyme_pk PRIMARY KEY (id)
);
COMMENT ON COLUMN public.enzyme.name IS 'MUST BE UNIQUE';
COMMENT ON COLUMN public.enzyme.cleavage_regexp IS 'The regular expression used to find cleavage site';


CREATE TABLE public.enzyme_cleavage (
                id IDENTITY NOT NULL,
                site VARCHAR(6) NOT NULL,
                residues VARCHAR(20) NOT NULL,
                restrictive_residues VARCHAR(20),
                enzyme_id INTEGER NOT NULL,
                CONSTRAINT enzyme_cleavage_pk PRIMARY KEY (id)
);
COMMENT ON COLUMN public.enzyme_cleavage.site IS 'Must be N-term or C-term (cleave before or after the residue)';
COMMENT ON COLUMN public.enzyme_cleavage.restrictive_residues IS 'A string which main contains one or more symbols of amino acids restricting enzyme cleavage.';


CREATE TABLE public.fragmentation_series (
                id IDENTITY NOT NULL,
                name VARCHAR(9),
                neutral_loss VARCHAR(5),
                serialized_properties LONGVARCHAR,
                CONSTRAINT fragmentation_series_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.fragmentation_series IS 'The types of fragment ion series that can be observed in an MS/MS spectrum.';
COMMENT ON COLUMN public.fragmentation_series.name IS 'Must be one of : a b c d v w x y z z+1 z+2 ya yb immonium precursor';
COMMENT ON COLUMN public.fragmentation_series.neutral_loss IS 'must be one of H2O, NH3, H3PO4';
COMMENT ON COLUMN public.fragmentation_series.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


CREATE TABLE public.fragmentation_rule (
                id IDENTITY NOT NULL,
                description VARCHAR(1000),
                precursor_min_charge INTEGER,
                fragment_charge INTEGER,
                fragment_max_moz REAL,
                fragment_residue_constraint VARCHAR(20),
                required_series_quality_level VARCHAR(15),
                serialized_properties LONGVARCHAR,
                theoretical_fragment_id INTEGER,
                required_series_id INTEGER,
                CONSTRAINT fragmentation_rule_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.fragmentation_rule IS 'Each instrument can have one or more of  fragment ion / rules. This rules describes ion fragment series that can be observed on an instrument and that are used by serach engine to generate theoritical spectrum and for scoring spectrum_peptide match';
COMMENT ON COLUMN public.fragmentation_rule.description IS 'Encoded fragmentation rule description. 
|code|description|comments|
| 1 | singly charged ||
| 2 | doubly charged if precursor 2+ or higher | (not internal or immonium) |
| 3 | doubly charged if precursor 3+ or higher | (not internal or immonium) |
| 4 | immonium ||
| 5 | a series ||
| 6 | a - NH3 if a significant and fragment includes RKNQ ||
| 7 | a - H2O if a significant and fragment includes STED ||
| 8 | b series ||
| 9 | b - NH3 if b significant and fragment includes RKNQ ||
| 10 | b - H2O if b significant and fragment includes STED ||
| 11 | c series ||
| 12 | x series ||
| 13 | y series ||
| 14 | y - NH3 if y significant and fragment includes RKNQ ||
| 15 | y - H2O if y significant and fragment includes STED ||
| 16 | z series ||
| 17 | internal yb < 700 Da ||
| 18 | internal ya < 700 Da ||
| 19 | y or y++ must be significant ||
| 20 | y or y++ must be highest scoring series ||
| 21 | z+1 series ||
| 22 | d and d'' series ||
| 23 | v series ||
| 24 | w and w'' series ||
| 25 | z+2 series ||';
COMMENT ON COLUMN public.fragmentation_rule.precursor_min_charge IS 'The minimum charge of the precursor required to observe this fragment type. Optional';
COMMENT ON COLUMN public.fragmentation_rule.fragment_charge IS 'The fragment charge state.';
COMMENT ON COLUMN public.fragmentation_rule.fragment_residue_constraint IS 'The fragment must contain one of the residues described here. exemple : y-NH3 series can be observed only if fragment includes RKNQ or y-H2O only if fragment includes STED. Optional';
COMMENT ON COLUMN public.fragmentation_rule.required_series_quality_level IS ':?: significant or highest_scoring';
COMMENT ON COLUMN public.fragmentation_rule.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';
COMMENT ON COLUMN public.fragmentation_rule.theoretical_fragment_id IS 'The corresponding and specific ion series.';
COMMENT ON COLUMN public.fragmentation_rule.required_series_id IS 'The ion series familly (a, b, c, x, y, z) required to be observed for this fragmentation rule.';


CREATE TABLE public.activation (
                type VARCHAR(100) NOT NULL,
                CONSTRAINT activation_pk PRIMARY KEY (type)
);
COMMENT ON TABLE public.activation IS 'Activation Method. The fragmentation method used for ion dissociation or fragmentation. See PSI:1000044';
COMMENT ON COLUMN public.activation.type IS 'HCD CID ETD.';


CREATE TABLE public.instrument (
                id IDENTITY NOT NULL,
                name VARCHAR(100) NOT NULL,
                source VARCHAR(100) NOT NULL,
                serialized_properties LONGVARCHAR,
                CONSTRAINT instrument_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.instrument IS 'The identification of a Mass Spectrometer. Properties (name,source) must be unique.';
COMMENT ON COLUMN public.instrument.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


CREATE TABLE public.instrument_config (
                id IDENTITY NOT NULL,
                name VARCHAR(100) NOT NULL,
                ms1_analyzer VARCHAR(100) NOT NULL,
                msn_analyzer VARCHAR(100),
                serialized_properties LONGVARCHAR,
                instrument_id INTEGER NOT NULL,
                activation_type VARCHAR(100) NOT NULL,
                CONSTRAINT instrument_config_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.instrument_config IS 'The description of a mass spectrometer instrument configuration.';
COMMENT ON COLUMN public.instrument_config.name IS 'MUST BE UNIQUE';
COMMENT ON COLUMN public.instrument_config.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


CREATE TABLE public.instrument_config_fragmentation_rule_map (
                instrument_config_id INTEGER NOT NULL,
                fragmentation_rule_id INTEGER NOT NULL,
                CONSTRAINT instrument_config_fragmentation_rule_map_pk PRIMARY KEY (instrument_config_id, fragmentation_rule_id)
);
COMMENT ON TABLE public.instrument_config_fragmentation_rule_map IS 'The set of fragmentation rules associated with this instrument configuration';


CREATE TABLE public.user_account (
                id IDENTITY NOT NULL,
                login VARCHAR(50) NOT NULL,
                creation_mode VARCHAR(10) NOT NULL,
                serialized_properties LONGVARCHAR,
                CONSTRAINT user_account_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.user_account IS 'User account information.
UNIQUE(login)';
COMMENT ON COLUMN public.user_account.login IS 'User login. The login must be unique within the database.';
COMMENT ON COLUMN public.user_account.creation_mode IS 'manual creation (from the interface) or automatic creation (LDAP import).';
COMMENT ON COLUMN public.user_account.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


CREATE TABLE public.raw_file (
                name VARCHAR(250) NOT NULL,
                extension VARCHAR(10) NOT NULL,
                directory VARCHAR(500),
                creation_timestamp TIMESTAMP,
                serialized_properties LONGVARCHAR,
                instrument_id INTEGER NOT NULL,
                owner_id INTEGER NOT NULL,
                CONSTRAINT raw_file_pk PRIMARY KEY (name)
);


CREATE TABLE public.run (
                id IDENTITY NOT NULL,
                number INTEGER NOT NULL,
                run_start REAL NOT NULL,
                run_stop REAL NOT NULL,
                duration REAL NOT NULL,
                lc_method VARCHAR(250),
                ms_method VARCHAR(250),
                analyst VARCHAR(50),
                serialized_properties LONGVARCHAR,
                raw_file_name VARCHAR(250) NOT NULL,
                CONSTRAINT run_pk PRIMARY KEY (id)
);
COMMENT ON COLUMN public.run.number IS 'The run number inside a given raw file.
Default is one because in the main case a raw file contains a single run.';
COMMENT ON COLUMN public.run.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


CREATE TABLE public.project (
                id IDENTITY NOT NULL,
                name VARCHAR(250) NOT NULL,
                description VARCHAR(1000),
                creation_timestamp TIMESTAMP NOT NULL,
                serialized_properties LONGVARCHAR,
                owner_id INTEGER NOT NULL,
                CONSTRAINT project_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.project IS 'A project contains multiple experiments relative to the same study or topic. Files associated to a project are stored in the repository in ''''/root/project_${project_id}''''.';
COMMENT ON COLUMN public.project.name IS 'The name of the project as provided by the user.';
COMMENT ON COLUMN public.project.description IS 'The description of the project as provided by the user.';
COMMENT ON COLUMN public.project.creation_timestamp IS 'The timestamp corresponding to the creation date of the project.';
COMMENT ON COLUMN public.project.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';
COMMENT ON COLUMN public.project.owner_id IS 'The owner of this project. The owner is also a member of the project and then is represented in ''''project_user_account_map''''';


CREATE TABLE public.virtual_folder (
                id IDENTITY NOT NULL,
                name VARCHAR(250) NOT NULL,
                path VARCHAR(500),
                serialized_properties LONGVARCHAR,
                parent_virtual_folder_id INTEGER,
                project_id INTEGER NOT NULL,
                CONSTRAINT virtual_folder_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.virtual_folder IS 'A virtual folder organize documents in the database. This documents are virtual documents created only in the database.';
COMMENT ON COLUMN public.virtual_folder.name IS 'The folder name.';
COMMENT ON COLUMN public.virtual_folder.path IS 'NOT YET USED : the path to this folder. This path can be created from the parent relationship.';
COMMENT ON COLUMN public.virtual_folder.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';
COMMENT ON COLUMN public.virtual_folder.parent_virtual_folder_id IS 'The parent folder. Null if this folder is rooted in the project folder.';


CREATE TABLE public.project_user_account_map (
                project_id INTEGER NOT NULL,
                user_account_id INTEGER NOT NULL,
                serialized_properties LONGVARCHAR,
                CONSTRAINT project_user_account_map_pk PRIMARY KEY (project_id, user_account_id)
);
COMMENT ON TABLE public.project_user_account_map IS 'Mappinng table between user_account and project table';
COMMENT ON COLUMN public.project_user_account_map.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


CREATE TABLE public.quant_method (
                id IDENTITY NOT NULL,
                name VARCHAR(1000) NOT NULL,
                type VARCHAR(20) NOT NULL,
                abundance_unit VARCHAR(30) NOT NULL,
                serialized_properties LONGVARCHAR,
                CONSTRAINT quant_method_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.quant_method IS 'The quantificatin method description.';
COMMENT ON COLUMN public.quant_method.type IS 'isobaric_labeling, residue_labeling, atom_labeling, label_free, spectral_counting';
COMMENT ON COLUMN public.quant_method.abundance_unit IS 'spectral_counting, reporter_ion,  feature, xic (mrm), mixed';
COMMENT ON COLUMN public.quant_method.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


CREATE TABLE public.data_set (
                id IDENTITY NOT NULL,
                number INTEGER NOT NULL,
                name VARCHAR NOT NULL,
                description VARCHAR(10000),
                type VARCHAR NOT NULL,
                keywords VARCHAR,
                creation_timestamp TIMESTAMP NOT NULL,
                modification_log LONGVARCHAR,
                fraction_count INTEGER DEFAULT 0 NOT NULL,
                serialized_properties LONGVARCHAR,
                result_set_id INTEGER,
                result_summary_id INTEGER,
                aggregation_id INTEGER,
                fractionation_id INTEGER,
                quant_method_id INTEGER,
                parent_dataset_id INTEGER,
                project_id INTEGER NOT NULL,
                CONSTRAINT data_set_pk PRIMARY KEY (id)
);
COMMENT ON COLUMN public.data_set.name IS 'Could be the sample name.';
COMMENT ON COLUMN public.data_set.type IS 'Valid values are:
IDENTIFICATION, QUANTITATION, AGGREGATE.';


CREATE TABLE public.run_identification (
                id INTEGER NOT NULL,
                serialized_properties LONGVARCHAR,
                run_id INTEGER,
                raw_file_name VARCHAR(250),
                CONSTRAINT run_identification_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.run_identification IS 'The identification of a run.';


CREATE TABLE public.sample_analysis (
                id IDENTITY NOT NULL,
                number INTEGER NOT NULL,
                serialized_properties LONGVARCHAR,
                quantitation_id INTEGER NOT NULL,
                CONSTRAINT sample_analysis_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.sample_analysis IS 'Represents each analytical replicates of the associated biological sample. analytical replicates does not necessarily means MS run since labelled samples are analysed in MS in a unique run.';
COMMENT ON COLUMN public.sample_analysis.number IS 'Number of the technological replicate.';
COMMENT ON COLUMN public.sample_analysis.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


CREATE TABLE public.master_quant_channel (
                id IDENTITY NOT NULL,
                number INTEGER NOT NULL,
                name VARCHAR(100) NOT NULL,
                serialized_properties LONGVARCHAR,
                lcms_map_set_id INTEGER,
                quant_result_summary_id INTEGER,
                quantitation_id INTEGER NOT NULL,
                CONSTRAINT master_quant_channel_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.master_quant_channel IS 'Store the quantitation profiles and ratios. May correspond to a quantitation overview (one unique fraction).';
COMMENT ON COLUMN public.master_quant_channel.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


CREATE TABLE public.quant_label (
                id IDENTITY NOT NULL,
                type VARCHAR(16) NOT NULL,
                name VARCHAR(10) NOT NULL,
                serialized_properties LONGVARCHAR,
                quant_method_id INTEGER NOT NULL,
                CONSTRAINT quant_label_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.quant_label IS 'TODO: rename to quantitative_labels or quant_labels ? (same semantic than quantitation_method ???)';
COMMENT ON COLUMN public.quant_label.type IS 'isobaric residue_isotopic atom_isotopic';
COMMENT ON COLUMN public.quant_label.name IS 'isobaric => 114/115/116/117 isotopic => light/heavy';
COMMENT ON COLUMN public.quant_label.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


CREATE TABLE public.biological_sample (
                id IDENTITY NOT NULL,
                number INTEGER NOT NULL,
                name VARCHAR(100) NOT NULL,
                serialized_properties LONGVARCHAR,
                quantitation_id INTEGER NOT NULL,
                CONSTRAINT biological_sample_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.biological_sample IS 'A biological sample under study.';
COMMENT ON COLUMN public.biological_sample.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


CREATE TABLE public.biological_sample_sample_analysis_map (
                biological_sample_id INTEGER NOT NULL,
                sample_analysis_id INTEGER NOT NULL,
                CONSTRAINT biological_sample_sample_analysis_map_pk PRIMARY KEY (biological_sample_id, sample_analysis_id)
);


CREATE TABLE public.group_setup (
                id IDENTITY NOT NULL,
                name VARCHAR(100) NOT NULL,
                serialized_properties LONGVARCHAR,
                quantitation_id INTEGER NOT NULL,
                CONSTRAINT group_setup_pk PRIMARY KEY (id)
);
COMMENT ON COLUMN public.group_setup.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


CREATE TABLE public.biological_group (
                id IDENTITY NOT NULL,
                number INTEGER NOT NULL,
                name VARCHAR(100) NOT NULL,
                serialized_properties LONGVARCHAR,
                CONSTRAINT biological_group_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.biological_group IS 'A group of related biological sample. A group is a generic concept that can be used to represents physiological conditions, pool or sample preparation conditions.';
COMMENT ON COLUMN public.biological_group.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


CREATE TABLE public.group_setup_biological_group_map (
                group_setup_id INTEGER NOT NULL,
                biological_group_id INTEGER NOT NULL,
                CONSTRAINT group_setup_biological_group_map_pk PRIMARY KEY (group_setup_id, biological_group_id)
);


CREATE TABLE public.ratio_definition (
                id IDENTITY NOT NULL,
                number INTEGER NOT NULL,
                numerator_id INTEGER NOT NULL,
                denominator_id INTEGER NOT NULL,
                group_setup_id INTEGER NOT NULL,
                CONSTRAINT ratio_definition_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.ratio_definition IS 'The definition of a quantitative ratio. A quantitative ratio is calculated from two biological groups that are considered as the numerator and denominator of the ratio formula.';
COMMENT ON COLUMN public.ratio_definition.number IS 'Allows representation of sequence of ratios.';


CREATE TABLE public.biological_group_biological_sample_item (
                biological_group_id INTEGER NOT NULL,
                biological_sample_id INTEGER NOT NULL,
                CONSTRAINT biological_group_biological_sample_item_pk PRIMARY KEY (biological_group_id, biological_sample_id)
);


CREATE TABLE public.external_db (
                id IDENTITY NOT NULL,
                name VARCHAR(500) NOT NULL,
                connection_mode VARCHAR(50) NOT NULL,
                username VARCHAR(50),
                password VARCHAR(50),
                host VARCHAR(100),
                port INTEGER,
                type VARCHAR(100) NOT NULL,
                version VARCHAR(50) NOT NULL,
                is_busy BOOLEAN NOT NULL,
                serialized_properties LONGVARCHAR,
                CONSTRAINT external_db_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.external_db IS 'Contains connexion properties for databases associated to projects. 
Databases allowing multiple instances are necessarily associated to projects.
Singleton databases (PDIdb, PSdb, ePims, ...) are also define through this table without any specific association to any projects';
COMMENT ON COLUMN public.external_db.name IS 'The name of the database on the DBMS server.
Could be the path/name of the database in case of embedded file DB engine (H2 / sqlite...)';
COMMENT ON COLUMN public.external_db.connection_mode IS 'Specify type of connection used for this DB. Possible values: HOST, MEMORY or FILE';
COMMENT ON COLUMN public.external_db.username IS 'The user name to use for database connection.';
COMMENT ON COLUMN public.external_db.password IS 'The password to use for database connection.';
COMMENT ON COLUMN public.external_db.host IS 'The hostname of the DBMS server.';
COMMENT ON COLUMN public.external_db.port IS 'The hostname of the DBMS server.';
COMMENT ON COLUMN public.external_db.type IS 'Type of database schema. Allowed values : msi, lcms, ps, pdi...';
COMMENT ON COLUMN public.external_db.version IS 'Indicates the schema version of the referenced db. For instance, it could correspond to admin_infos.model_version of an MSIdb';
COMMENT ON COLUMN public.external_db.is_busy IS 'Informs about the busy status of the corresponding external DB. If set to true then it tells that the external DB is busy and should not be used at the moment. Could be usefull if the external DB is implemented using an embedded technology like SQLite.';
COMMENT ON COLUMN public.external_db.serialized_properties IS 'Could store the driver name and other connection properties needed by the driver.';


CREATE TABLE public.project_db_map (
                project_id INTEGER NOT NULL,
                external_db_id INTEGER NOT NULL,
                CONSTRAINT project_db_map_pk PRIMARY KEY (project_id, external_db_id)
);
COMMENT ON TABLE public.project_db_map IS 'Mapping table between the project and external_db tables.';


CREATE TABLE public.object_tree_schema (
                name VARCHAR(1000) NOT NULL,
                type VARCHAR(10) NOT NULL,
                version VARCHAR(100),
                schema LONGVARCHAR NOT NULL,
                description VARCHAR(1000),
                serialized_properties LONGVARCHAR,
                CONSTRAINT object_tree_schema_pk PRIMARY KEY (name)
);
COMMENT ON COLUMN public.object_tree_schema.type IS 'XSD or JSON or TSV (tabulated separated values, in this case schema column contains column header)';
COMMENT ON COLUMN public.object_tree_schema.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).
i.e. for document table a schema property may be the document file extension (.tsv, .protML, .pepML)';


CREATE TABLE public.object_tree (
                id IDENTITY NOT NULL,
                serialized_data LONGVARCHAR NOT NULL,
                serialized_properties LONGVARCHAR,
                schema_name VARCHAR(1000) NOT NULL,
                CONSTRAINT object_tree_pk PRIMARY KEY (id)
);


CREATE TABLE public.document (
                id IDENTITY NOT NULL,
                name VARCHAR(250) NOT NULL,
                description VARCHAR(1000),
                keywords VARCHAR(250),
                creation_timestamp TIMESTAMP NOT NULL,
                modification_timestamp TIMESTAMP,
                creation_log LONGVARCHAR,
                modification_log LONGVARCHAR,
                serialized_properties LONGVARCHAR,
                object_tree_id INTEGER NOT NULL,
                virtual_folder_id INTEGER NOT NULL,
                project_id INTEGER NOT NULL,
                schema_name VARCHAR(1000) NOT NULL,
                CONSTRAINT document_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.document IS 'A virtual document stored on the database. The content of the document is stored in the ''''object_tree'''' associated with the document.';
COMMENT ON COLUMN public.document.creation_log IS 'A description provided by the system relative to the operation which as generated the document';
COMMENT ON COLUMN public.document.modification_log IS 'A description relative to the processings applied to the docuement after its ceation.';
COMMENT ON COLUMN public.document.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


CREATE TABLE public.quant_channel (
                id IDENTITY NOT NULL,
                number INTEGER NOT NULL,
                name VARCHAR(100) NOT NULL,
                context_key VARCHAR(100) NOT NULL,
                serialized_properties LONGVARCHAR,
                lcms_map_id INTEGER,
                ident_result_summary_id INTEGER NOT NULL,
                quant_result_summary_id INTEGER,
                run_id INTEGER,
                quant_label_id INTEGER,
                sample_analysis_id INTEGER NOT NULL,
                biological_sample_id INTEGER NOT NULL,
                master_quant_channel_id INTEGER NOT NULL,
                dataset_id INTEGER NOT NULL,
                CONSTRAINT quant_channel_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.quant_channel IS 'A quanti channel represents all quantified peptides from a single replicate of a single fraction of a biological sample. UNIQUE(context_key, quantitation_fraction_id).';
COMMENT ON COLUMN public.quant_channel.name IS 'TODO: allows NULL ?';
COMMENT ON COLUMN public.quant_channel.context_key IS 'string representation of sample_number.replicate_number. This string is obtained by the concatenation of 
biological_sample.number and sample_analysis_replicate.number';
COMMENT ON COLUMN public.quant_channel.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


CREATE TABLE public.admin_infos (
                model_version VARCHAR(50) NOT NULL,
                db_creation_date TIMESTAMP,
                model_update_date TIMESTAMP,
                configuration LONGVARCHAR NOT NULL,
                CONSTRAINT admin_infos_pkey PRIMARY KEY (model_version)
);
COMMENT ON TABLE public.admin_infos IS 'This table gives information about the current database model.';
COMMENT ON COLUMN public.admin_infos.model_version IS 'The version number of the database schema.';
COMMENT ON COLUMN public.admin_infos.configuration IS 'The configuration properties. configuration contains :
  * absolute root path for shared documents, organized by projects';


ALTER TABLE public.data_set ADD CONSTRAINT aggregation_dataset_fk
FOREIGN KEY (aggregation_id)
REFERENCES public.aggregation (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE public.data_set ADD CONSTRAINT fractionation_dataset_fk
FOREIGN KEY (fractionation_id)
REFERENCES public.fractionation (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE public.peaklist_software ADD CONSTRAINT spec_title_parsing_rule_peaklist_software_fk
FOREIGN KEY (spec_title_parsing_rule_id)
REFERENCES public.spec_title_parsing_rule (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE public.enzyme_cleavage ADD CONSTRAINT enzyme_enzyme_cleavage_fk
FOREIGN KEY (enzyme_id)
REFERENCES public.enzyme (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE public.fragmentation_rule ADD CONSTRAINT fragmentation_series_fragmentation_rule_fk
FOREIGN KEY (required_series_id)
REFERENCES public.fragmentation_series (id)
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE public.fragmentation_rule ADD CONSTRAINT fragmentation_series_fragmentation_rule_fk1
FOREIGN KEY (theoretical_fragment_id)
REFERENCES public.fragmentation_series (id)
ON UPDATE NO ACTION;

ALTER TABLE public.instrument_config_fragmentation_rule_map ADD CONSTRAINT fragmentation_rule_instrument_config_fragmentation_rule_map_fk
FOREIGN KEY (fragmentation_rule_id)
REFERENCES public.fragmentation_rule (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE public.instrument_config ADD CONSTRAINT activation_instrument_config_fk
FOREIGN KEY (activation_type)
REFERENCES public.activation (type)
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE public.instrument_config ADD CONSTRAINT instrument_instrument_config_fk
FOREIGN KEY (instrument_id)
REFERENCES public.instrument (id)
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE public.raw_file ADD CONSTRAINT instrument_raw_file_fk
FOREIGN KEY (instrument_id)
REFERENCES public.instrument (id)
ON UPDATE NO ACTION;

ALTER TABLE public.instrument_config_fragmentation_rule_map ADD CONSTRAINT instrument_config_instrument_config_fragmentation_rule_map_fk
FOREIGN KEY (instrument_config_id)
REFERENCES public.instrument_config (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE public.project ADD CONSTRAINT user_account_project_fk
FOREIGN KEY (owner_id)
REFERENCES public.user_account (id)
ON UPDATE NO ACTION;

ALTER TABLE public.project_user_account_map ADD CONSTRAINT user_account_project_user_map_fk
FOREIGN KEY (user_account_id)
REFERENCES public.user_account (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.raw_file ADD CONSTRAINT user_account_raw_file_fk
FOREIGN KEY (owner_id)
REFERENCES public.user_account (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.run ADD CONSTRAINT raw_file_run_fk
FOREIGN KEY (raw_file_name)
REFERENCES public.raw_file (name)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.run_identification ADD CONSTRAINT raw_file_run_identification_fk
FOREIGN KEY (raw_file_name)
REFERENCES public.raw_file (name)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE public.run_identification ADD CONSTRAINT run_run_identification_fk
FOREIGN KEY (run_id)
REFERENCES public.run (id)
ON DELETE SET NULL
ON UPDATE NO ACTION;

ALTER TABLE public.quant_channel ADD CONSTRAINT run_quant_channel_fk
FOREIGN KEY (run_id)
REFERENCES public.run (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE public.document ADD CONSTRAINT project_document_fk
FOREIGN KEY (project_id)
REFERENCES public.project (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE public.project_user_account_map ADD CONSTRAINT project_project_user_map_fk
FOREIGN KEY (project_id)
REFERENCES public.project (id)
ON UPDATE NO ACTION;

ALTER TABLE public.virtual_folder ADD CONSTRAINT project_virtual_folder_fk
FOREIGN KEY (project_id)
REFERENCES public.project (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.project_db_map ADD CONSTRAINT project_project_db_map_fk
FOREIGN KEY (project_id)
REFERENCES public.project (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.data_set ADD CONSTRAINT project_dataset_fk
FOREIGN KEY (project_id)
REFERENCES public.project (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE public.document ADD CONSTRAINT virtual_folder_document_fk
FOREIGN KEY (virtual_folder_id)
REFERENCES public.virtual_folder (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.virtual_folder ADD CONSTRAINT virtual_folder_virtual_folder_fk
FOREIGN KEY (parent_virtual_folder_id)
REFERENCES public.virtual_folder (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.quant_label ADD CONSTRAINT quant_method_quant_label_fk
FOREIGN KEY (quant_method_id)
REFERENCES public.quant_method (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.data_set ADD CONSTRAINT quant_method_dataset_fk
FOREIGN KEY (quant_method_id)
REFERENCES public.quant_method (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE public.master_quant_channel ADD CONSTRAINT dataset_master_quant_channel_fk
FOREIGN KEY (quantitation_id)
REFERENCES public.data_set (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE public.group_setup ADD CONSTRAINT dataset_group_setup_fk
FOREIGN KEY (quantitation_id)
REFERENCES public.data_set (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE public.quant_channel ADD CONSTRAINT dataset_quant_channel_fk
FOREIGN KEY (dataset_id)
REFERENCES public.data_set (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE public.biological_sample ADD CONSTRAINT dataset_biological_sample_fk
FOREIGN KEY (quantitation_id)
REFERENCES public.data_set (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE public.sample_analysis ADD CONSTRAINT dataset_sample_analysis_fk
FOREIGN KEY (quantitation_id)
REFERENCES public.data_set (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE public.run_identification ADD CONSTRAINT dataset_run_identification_fk
FOREIGN KEY (id)
REFERENCES public.data_set (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE public.data_set ADD CONSTRAINT dataset_dataset_fk
FOREIGN KEY (parent_dataset_id)
REFERENCES public.data_set (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE public.quant_channel ADD CONSTRAINT sample_analysis_quant_channel_fk
FOREIGN KEY (sample_analysis_id)
REFERENCES public.sample_analysis (id)
ON UPDATE NO ACTION;

ALTER TABLE public.biological_sample_sample_analysis_map ADD CONSTRAINT sample_analysis_biological_sample_sample_analysis_map_fk
FOREIGN KEY (sample_analysis_id)
REFERENCES public.sample_analysis (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE public.quant_channel ADD CONSTRAINT master_quant_channel_quant_channel_fk
FOREIGN KEY (master_quant_channel_id)
REFERENCES public.master_quant_channel (id)
ON DELETE NO ACTION
ON UPDATE CASCADE;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE public.quant_channel ADD CONSTRAINT quant_label_quant_channel_fk
FOREIGN KEY (quant_label_id)
REFERENCES public.quant_label (id)
ON UPDATE NO ACTION;

ALTER TABLE public.biological_group_biological_sample_item ADD CONSTRAINT biological_sample_biological_group_biological_sample_item_fk
FOREIGN KEY (biological_sample_id)
REFERENCES public.biological_sample (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE public.quant_channel ADD CONSTRAINT biological_sample_quant_channel_fk
FOREIGN KEY (biological_sample_id)
REFERENCES public.biological_sample (id)
ON UPDATE NO ACTION;

ALTER TABLE public.biological_sample_sample_analysis_map ADD CONSTRAINT biological_sample_biological_sample_sample_analysis_map_fk
FOREIGN KEY (biological_sample_id)
REFERENCES public.biological_sample (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE public.ratio_definition ADD CONSTRAINT group_setup_ratio_definition_fk
FOREIGN KEY (group_setup_id)
REFERENCES public.group_setup (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.group_setup_biological_group_map ADD CONSTRAINT group_setup_group_setup_biological_group_map_fk
FOREIGN KEY (group_setup_id)
REFERENCES public.group_setup (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE public.biological_group_biological_sample_item ADD CONSTRAINT biological_group_biological_group_biological_sample_item_fk
FOREIGN KEY (biological_group_id)
REFERENCES public.biological_group (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.ratio_definition ADD CONSTRAINT biological_group_ratio_numerator_fk
FOREIGN KEY (numerator_id)
REFERENCES public.biological_group (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE public.ratio_definition ADD CONSTRAINT biological_group_ratio_denominator_fk
FOREIGN KEY (denominator_id)
REFERENCES public.biological_group (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE public.group_setup_biological_group_map ADD CONSTRAINT biological_group_group_setup_biological_group_map_fk
FOREIGN KEY (biological_group_id)
REFERENCES public.biological_group (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE public.project_db_map ADD CONSTRAINT external_db_project_db_map_fk
FOREIGN KEY (external_db_id)
REFERENCES public.external_db (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE public.object_tree ADD CONSTRAINT object_tree_schema_object_tree_fk
FOREIGN KEY (schema_name)
REFERENCES public.object_tree_schema (name)
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE public.document ADD CONSTRAINT object_tree_schema_document_fk
FOREIGN KEY (schema_name)
REFERENCES public.object_tree_schema (name)
ON UPDATE NO ACTION;

ALTER TABLE public.document ADD CONSTRAINT object_tree_document_fk
FOREIGN KEY (object_tree_id)
REFERENCES public.object_tree (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;


CREATE TABLE public.admin_infos (
                model_version VARCHAR(1000) NOT NULL,
                db_creation_date TIMESTAMP,
                model_update_date TIMESTAMP,
                chr_location_update_timestamp TIMESTAMP,
                serialized_properties TEXT,
                CONSTRAINT admin_infos_pk PRIMARY KEY (model_version)
);


CREATE TABLE public.object_tree_schema (
                name VARCHAR(1000) NOT NULL,
                type VARCHAR(10) NOT NULL,
                version VARCHAR(100) NOT NULL,
                schema TEXT NOT NULL,
                description VARCHAR(1000),
                serialized_properties TEXT,
                CONSTRAINT object_tree_schema_pk PRIMARY KEY (name)
);


CREATE SEQUENCE public.object_tree_id_seq;

CREATE TABLE public.object_tree (
                id BIGINT NOT NULL DEFAULT nextval('public.object_tree_id_seq'),
                serialized_data TEXT NOT NULL,
                serialized_properties TEXT,
                schema_name VARCHAR(1000) NOT NULL,
                CONSTRAINT object_tree_pk PRIMARY KEY (id)
);


ALTER SEQUENCE public.object_tree_id_seq OWNED BY public.object_tree.id;

CREATE SEQUENCE public.seq_db_release_id_seq;

CREATE TABLE public.seq_db_release (
                id BIGINT NOT NULL DEFAULT nextval('public.seq_db_release_id_seq'),
                date VARCHAR(50) NOT NULL,
                version VARCHAR(10),
                serialized_properties TEXT,
                CONSTRAINT seq_db_release_pk PRIMARY KEY (id)
);
COMMENT ON COLUMN public.seq_db_release.date IS 'Expected date format: yyyymmdd';


ALTER SEQUENCE public.seq_db_release_id_seq OWNED BY public.seq_db_release.id;

CREATE SEQUENCE public.fasta_parsing_rule_id_seq;

CREATE TABLE public.fasta_parsing_rule (
                id BIGINT NOT NULL DEFAULT nextval('public.fasta_parsing_rule_id_seq'),
                db_type VARCHAR(100),
                entry_id VARCHAR(100) NOT NULL,
                entry_ac VARCHAR(100),
                entry_name VARCHAR(100) NOT NULL,
                gene_name VARCHAR(100),
                organism_name VARCHAR(100),
                taxon_id VARCHAR(100),
                CONSTRAINT fasta_parsing_rule_pk PRIMARY KEY (id)
);


ALTER SEQUENCE public.fasta_parsing_rule_id_seq OWNED BY public.fasta_parsing_rule.id;

CREATE SEQUENCE public.seq_db_config_id_seq;

CREATE TABLE public.seq_db_config (
                id BIGINT NOT NULL DEFAULT nextval('public.seq_db_config_id_seq'),
                name VARCHAR(100) NOT NULL,
                alphabet VARCHAR(3) NOT NULL,
                ref_entry_format VARCHAR(10) NOT NULL,
                serialized_properties TEXT,
                fasta_parsing_rule_id BIGINT NOT NULL,
                is_native BOOLEAN NOT NULL,
                CONSTRAINT seq_db_config_pk PRIMARY KEY (id)
);
COMMENT ON COLUMN public.seq_db_config.name IS 'Some native databases must be named using the following convention : ipi, sprot, trembl, ncbi';
COMMENT ON COLUMN public.seq_db_config.alphabet IS 'Java enum SequenceDbConfig.Alphabet {AA, DNA }';
COMMENT ON COLUMN public.seq_db_config.ref_entry_format IS 'swiss/genebank/gff TODO: add support for gff format';
COMMENT ON COLUMN public.seq_db_config.is_native IS 'A native DB is a public DB which is neither a subset of database neither a database with additionnal or modified sequences (i.e. decoy sequences).';


ALTER SEQUENCE public.seq_db_config_id_seq OWNED BY public.seq_db_config.id;

CREATE SEQUENCE public.seq_db_instance_id_seq;

CREATE TABLE public.seq_db_instance (
                id BIGINT NOT NULL DEFAULT nextval('public.seq_db_instance_id_seq'),
                fasta_file_path VARCHAR(500) NOT NULL,
                ref_file_path VARCHAR(500),
                is_indexed BOOLEAN NOT NULL,
                is_deleted BOOLEAN NOT NULL,
                revision INTEGER NOT NULL,
                creation_timestamp TIMESTAMP NOT NULL,
                sequence_count INTEGER NOT NULL,
                residue_count INTEGER,
                serialized_properties TEXT,
                seq_db_release_id BIGINT,
                seq_db_config_id BIGINT NOT NULL,
                CONSTRAINT seq_db_instance_pk PRIMARY KEY (id)
);
COMMENT ON COLUMN public.seq_db_instance.revision IS 'The revision number is incremented each time a new instance of a specified seq_db_config is created.';
COMMENT ON COLUMN public.seq_db_instance.seq_db_release_id IS 'database release information are created whenever possible, but some dabase don''t have any structured release naming convention.';


ALTER SEQUENCE public.seq_db_instance_id_seq OWNED BY public.seq_db_instance.id;

CREATE TABLE public.taxon (
                id BIGINT NOT NULL,
                scientific_name VARCHAR(512) NOT NULL,
                rank VARCHAR(30) NOT NULL,
                is_active BOOLEAN NOT NULL,
                serialized_properties TEXT,
                parent_taxon_id BIGINT NOT NULL,
                CONSTRAINT taxon_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.taxon IS 'Describes the NCBI taxononmy.';
COMMENT ON COLUMN public.taxon.id IS 'The NCBI taxon id';
COMMENT ON COLUMN public.taxon.scientific_name IS 'From NCBI: Every node in the database is required to have exactly one "scientific name".';


CREATE SEQUENCE public.gene_id_seq;

CREATE TABLE public.gene (
                id BIGINT NOT NULL DEFAULT nextval('public.gene_id_seq'),
                name VARCHAR(100) NOT NULL,
                name_type VARCHAR(20) NOT NULL,
                synonyms TEXT,
                is_active BOOLEAN NOT NULL,
                serialized_properties TEXT,
                taxon_id BIGINT NOT NULL,
                CONSTRAINT gene_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.gene IS 'UNIQUE(name, taxon_id)';
COMMENT ON COLUMN public.gene.name_type IS 'The type of the name and synonyms for this gene.
Allowed values for this field are: GENE, ORDERED_LOCUS, ORF.';
COMMENT ON COLUMN public.gene.synonyms IS 'A list of synonyms separated by spaces';
COMMENT ON COLUMN public.gene.taxon_id IS 'The NCBI taxon id';


ALTER SEQUENCE public.gene_id_seq OWNED BY public.gene.id;

CREATE UNIQUE INDEX gene_name_taxon_idx
 ON public.gene
 ( name, taxon_id );

CREATE INDEX gene_taxon_idx
 ON public.gene
 ( taxon_id );

CLUSTER gene_taxon_idx ON gene;

CREATE SEQUENCE public.chromosome_location_id_seq;

CREATE TABLE public.chromosome_location (
                id BIGINT NOT NULL DEFAULT nextval('public.chromosome_location_id_seq'),
                chromosome_identifier VARCHAR(10) NOT NULL,
                location VARCHAR(250),
                serialized_properties TEXT,
                gene_id BIGINT NOT NULL,
                taxon_id BIGINT NOT NULL,
                CONSTRAINT chromosome_location_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.chromosome_location IS 'paralogues, isoformes This table is deleted for active genes before each update. It contains the last known information about gene chromosome location.';
COMMENT ON COLUMN public.chromosome_location.serialized_properties IS 'TODO: put in schema exon, intron, strand';
COMMENT ON COLUMN public.chromosome_location.taxon_id IS 'The NCBI taxon id';


ALTER SEQUENCE public.chromosome_location_id_seq OWNED BY public.chromosome_location.id;

CREATE INDEX chromosome_location_taxon_idx
 ON public.chromosome_location
 ( taxon_id );

CLUSTER chromosome_location_taxon_idx ON chromosome_location;

CREATE SEQUENCE public.taxon_extra_name_id_seq;

CREATE TABLE public.taxon_extra_name (
                id BIGINT NOT NULL DEFAULT nextval('public.taxon_extra_name_id_seq'),
                class VARCHAR(256) NOT NULL,
                value VARCHAR(512) NOT NULL,
                serialized_properties TEXT,
                taxon_id BIGINT NOT NULL,
                CONSTRAINT taxon_extra_name_pk PRIMARY KEY (id)
);
COMMENT ON COLUMN public.taxon_extra_name.taxon_id IS 'The NCBI taxon id';


ALTER SEQUENCE public.taxon_extra_name_id_seq OWNED BY public.taxon_extra_name.id;

CREATE SEQUENCE public.bio_sequence_id_seq;

CREATE TABLE public.bio_sequence (
                id BIGINT NOT NULL DEFAULT nextval('public.bio_sequence_id_seq'),
                alphabet VARCHAR(3) NOT NULL,
                sequence TEXT NOT NULL,
                length INTEGER NOT NULL,
                mass INTEGER NOT NULL,
                pi REAL,
                crc64 VARCHAR(32) NOT NULL,
                serialized_properties TEXT,
                CONSTRAINT bio_sequence_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.bio_sequence IS 'Like Uniparc, it  is a non-redundant protein sequence archive, containing both active and dead sequences, and it is species-merged since sequences are handled just as strings - all sequences 100% identical over the whole length of the sequence between species are merged. A sequence that exists in many copies in different databases is represented as a single entry which allows to identify the same protein from different sources. UNIQUE(alphabet,mass,crc64)';
COMMENT ON COLUMN public.bio_sequence.alphabet IS 'dna, rna or aa';
COMMENT ON COLUMN public.bio_sequence.sequence IS 'The sequence of the protein. It can contains amino acids or nucleic acids depending on the used alphabet.';
COMMENT ON COLUMN public.bio_sequence.length IS 'The length of the sequence.';
COMMENT ON COLUMN public.bio_sequence.mass IS 'The approximated molecular mass of the protein or of the nucleic acid strand.';
COMMENT ON COLUMN public.bio_sequence.pi IS 'The isoelectric point of the protein. Only for protein sequences (alphabet=aa).';
COMMENT ON COLUMN public.bio_sequence.crc64 IS 'A numerical signature of the protein sequence built by a CRC64 algorithm.';


ALTER SEQUENCE public.bio_sequence_id_seq OWNED BY public.bio_sequence.id;

CREATE UNIQUE INDEX bio_sequence_crc_alphabet_mass_idx
 ON public.bio_sequence
 ( crc64, alphabet, mass );

CREATE SEQUENCE public.bio_sequence_annotation_id_seq;

CREATE TABLE public.bio_sequence_annotation (
                id BIGINT NOT NULL DEFAULT nextval('public.bio_sequence_annotation_id_seq'),
                version VARCHAR(50) NOT NULL,
                serialized_properties TEXT,
                bio_sequence_id BIGINT NOT NULL,
                taxon_id BIGINT NOT NULL,
                object_tree_id BIGINT NOT NULL,
                schema_name VARCHAR(1000) NOT NULL,
                CONSTRAINT bio_sequence_annotation_pk PRIMARY KEY (id)
);
COMMENT ON COLUMN public.bio_sequence_annotation.taxon_id IS 'The NCBI taxon id';


ALTER SEQUENCE public.bio_sequence_annotation_id_seq OWNED BY public.bio_sequence_annotation.id;

CREATE TABLE public.bio_sequence_gene_map (
                bio_sequence_id BIGINT NOT NULL,
                gene_id BIGINT NOT NULL,
                serialized_properties TEXT,
                taxon_id BIGINT NOT NULL,
                CONSTRAINT bio_sequence_gene_map_pk PRIMARY KEY (bio_sequence_id, gene_id)
);
COMMENT ON COLUMN public.bio_sequence_gene_map.taxon_id IS 'The NCBI taxon id';


CREATE INDEX bio_sequence_gene_map_gene_id_idx
 ON public.bio_sequence_gene_map
 ( gene_id );

CREATE TABLE public.bio_sequence_relation (
                na_sequence_id BIGINT NOT NULL,
                aa_sequence_id BIGINT NOT NULL,
                frame_number INTEGER NOT NULL,
                CONSTRAINT bio_sequence_relation_pk PRIMARY KEY (na_sequence_id, aa_sequence_id)
);
COMMENT ON COLUMN public.bio_sequence_relation.frame_number IS 'The frame used to translate the nucleic acid strand 1,2,3 or -1,-2,-3 Must only be defined for nucleic acid sequences (alphabet equals dna/rna).';


CREATE SEQUENCE public.protein_identifier_id_seq;

CREATE TABLE public.protein_identifier (
                id BIGINT NOT NULL DEFAULT nextval('public.protein_identifier_id_seq'),
                value VARCHAR(30) NOT NULL,
                is_ac_number BOOLEAN NOT NULL,
                is_active BOOLEAN NOT NULL,
                serialized_properties TEXT,
                bio_sequence_id BIGINT NOT NULL,
                taxon_id BIGINT NOT NULL,
                seq_db_config_id BIGINT NOT NULL,
                CONSTRAINT protein_identifier_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.protein_identifier IS 'An entry in a protein database identified by an accession number. UNIQUE( value, taxon_id )';
COMMENT ON COLUMN public.protein_identifier.is_ac_number IS 'true for accession numbers if the value corresponds to entry ID (ALB_HUMAN for instance) then this BOOLEAN will be false';
COMMENT ON COLUMN public.protein_identifier.taxon_id IS 'The NCBI taxon id';


ALTER SEQUENCE public.protein_identifier_id_seq OWNED BY public.protein_identifier.id;

CREATE INDEX protein_identifier_bio_sequence_id_idx
 ON public.protein_identifier
 ( bio_sequence_id );

CREATE INDEX protein_identifier_seq_db_config_idx
 ON public.protein_identifier
 ( seq_db_config_id );

CREATE UNIQUE INDEX protein_identifier_value_taxon_idx
 ON public.protein_identifier
 ( value, taxon_id );

CREATE SEQUENCE public.seq_db_entry_id_seq;

CREATE TABLE public.seq_db_entry (
                id BIGINT NOT NULL DEFAULT nextval('public.seq_db_entry_id_seq'),
                identifier VARCHAR(50) NOT NULL,
                name VARCHAR(1000) NOT NULL,
                version VARCHAR(100),
                ref_file_block_start BIGINT,
                ref_file_block_length INTEGER,
                is_active BOOLEAN NOT NULL,
                serialized_properties TEXT,
                bio_sequence_id BIGINT NOT NULL,
                taxon_id BIGINT NOT NULL,
                seq_db_instance_id BIGINT NOT NULL,
                seq_db_config_id BIGINT NOT NULL,
                CONSTRAINT seq_db_entry_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.seq_db_entry IS 'Note: only inactive entries should kept is the previous instance when updated a sequence database to a newset release.';
COMMENT ON COLUMN public.seq_db_entry.identifier IS 'The ID of the sequence entry in the database instance. EX: ALB_HUMAN';
COMMENT ON COLUMN public.seq_db_entry.ref_file_block_start IS 'May be NULL if no available ref file.';
COMMENT ON COLUMN public.seq_db_entry.ref_file_block_length IS 'May be NULL if no available ref file.';
COMMENT ON COLUMN public.seq_db_entry.taxon_id IS 'The NCBI taxon id';


ALTER SEQUENCE public.seq_db_entry_id_seq OWNED BY public.seq_db_entry.id;

CREATE INDEX seq_db_entry_identifier_idx
 ON public.seq_db_entry
 ( identifier );

CREATE INDEX seq_db_entry_is_active_idx
 ON public.seq_db_entry
 ( is_active );

CREATE INDEX seq_db_entry_bio_sequence_idx
 ON public.seq_db_entry
 ( bio_sequence_id );

CREATE INDEX seq_db_entry_taxon_idx
 ON public.seq_db_entry
 ( taxon_id );

CREATE INDEX seq_db_entry_seq_db_config_idx
 ON public.seq_db_entry
 ( seq_db_config_id );

CREATE INDEX seq_db_entry_seq_db_instance_idx
 ON public.seq_db_entry
 ( seq_db_instance_id );

CLUSTER seq_db_entry_seq_db_instance_idx ON seq_db_entry;

CREATE SEQUENCE public.fasta_file_entry_index_id_seq;

CREATE TABLE public.fasta_file_entry_index (
                id BIGINT NOT NULL DEFAULT nextval('public.fasta_file_entry_index_id_seq'),
                block_start BIGINT NOT NULL,
                block_length INTEGER NOT NULL,
                serialized_properties TEXT,
                bio_sequence_id BIGINT NOT NULL,
                seq_db_entry_id BIGINT NOT NULL,
                seq_db_instance_id BIGINT NOT NULL,
                CONSTRAINT fasta_file_entry_index_pk PRIMARY KEY (id)
);
COMMENT ON COLUMN public.fasta_file_entry_index.bio_sequence_id IS 'May be used to specify a sequence variant of the main seq db entry.';


ALTER SEQUENCE public.fasta_file_entry_index_id_seq OWNED BY public.fasta_file_entry_index.id;

CREATE INDEX fasta_file_entry_index_bio_sequence_idx
 ON public.fasta_file_entry_index
 ( bio_sequence_id );

CREATE INDEX fasta_file_entry_index_seq_db_entry_idx
 ON public.fasta_file_entry_index
 ( seq_db_entry_id );

CREATE INDEX fasta_file_entry_index_seq_db_instance_idx
 ON public.fasta_file_entry_index
 ( seq_db_instance_id );

CLUSTER fasta_file_entry_index_seq_db_instance_idx ON fasta_file_entry_index;

CREATE TABLE public.seq_db_entry_object_tree_map (
                seq_db_entry_id BIGINT NOT NULL,
                object_tree_id BIGINT NOT NULL,
                schema_name VARCHAR(1000) NOT NULL,
                CONSTRAINT seq_db_entry_object_tree_map_pk PRIMARY KEY (seq_db_entry_id, object_tree_id)
);


CREATE TABLE public.seq_db_entry_gene_map (
                seq_db_entry_id BIGINT NOT NULL,
                gene_id BIGINT NOT NULL,
                seq_db_instance_id BIGINT NOT NULL,
                CONSTRAINT seq_db_entry_gene_map_pk PRIMARY KEY (seq_db_entry_id, gene_id)
);


CREATE TABLE public.seq_db_entry_protein_identifier_map (
                seq_db_entry_id BIGINT NOT NULL,
                protein_identifier_id BIGINT NOT NULL,
                seq_db_instance_id BIGINT NOT NULL,
                CONSTRAINT seq_db_entry_protein_identifier_map_pk PRIMARY KEY (seq_db_entry_id, protein_identifier_id)
);
COMMENT ON TABLE public.seq_db_entry_protein_identifier_map IS 'Note: the same protein identifier shouldn''t be find in multiple instancesof the same seq database.';


CREATE INDEX seq_db_entry_protein_identifier_map_prot_identifier_idx
 ON public.seq_db_entry_protein_identifier_map
 ( protein_identifier_id );

ALTER TABLE public.bio_sequence_annotation ADD CONSTRAINT object_tree_schema_bio_sequence_annotation_fk
FOREIGN KEY (schema_name)
REFERENCES public.object_tree_schema (name)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.object_tree ADD CONSTRAINT object_tree_schema_object_tree_fk
FOREIGN KEY (schema_name)
REFERENCES public.object_tree_schema (name)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.seq_db_entry_object_tree_map ADD CONSTRAINT object_tree_schema_seq_db_entry_object_tree_map_fk
FOREIGN KEY (schema_name)
REFERENCES public.object_tree_schema (name)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.bio_sequence_annotation ADD CONSTRAINT object_tree_bio_sequence_instance_fk
FOREIGN KEY (object_tree_id)
REFERENCES public.object_tree (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.seq_db_entry_object_tree_map ADD CONSTRAINT object_tree_seq_db_entry_object_tree_map_fk
FOREIGN KEY (object_tree_id)
REFERENCES public.object_tree (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.seq_db_instance ADD CONSTRAINT seq_db_release_seq_db_instance_fk
FOREIGN KEY (seq_db_release_id)
REFERENCES public.seq_db_release (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.seq_db_config ADD CONSTRAINT fasta_parsing_rule_seq_database_fk
FOREIGN KEY (fasta_parsing_rule_id)
REFERENCES public.fasta_parsing_rule (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.seq_db_entry ADD CONSTRAINT seq_db_config_seq_db_entry_fk1
FOREIGN KEY (seq_db_config_id)
REFERENCES public.seq_db_config (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.seq_db_instance ADD CONSTRAINT seq_db_config_seq_db_instance_fk
FOREIGN KEY (seq_db_config_id)
REFERENCES public.seq_db_config (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.protein_identifier ADD CONSTRAINT seq_db_config_protein_identifier_fk
FOREIGN KEY (seq_db_config_id)
REFERENCES public.seq_db_config (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.seq_db_entry ADD CONSTRAINT seq_db_release_seq_db_entry_fk
FOREIGN KEY (seq_db_instance_id)
REFERENCES public.seq_db_instance (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.fasta_file_entry_index ADD CONSTRAINT seq_db_instance_db_entry_index_fk
FOREIGN KEY (seq_db_instance_id)
REFERENCES public.seq_db_instance (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.seq_db_entry_gene_map ADD CONSTRAINT seq_db_instance_seq_db_entry_gene_identifier_map_fk
FOREIGN KEY (seq_db_instance_id)
REFERENCES public.seq_db_instance (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.seq_db_entry_protein_identifier_map ADD CONSTRAINT seq_db_instance_seq_db_entry_protein_identifier_map_fk
FOREIGN KEY (seq_db_instance_id)
REFERENCES public.seq_db_instance (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.protein_identifier ADD CONSTRAINT taxon_protein_identifier_fk
FOREIGN KEY (taxon_id)
REFERENCES public.taxon (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.taxon ADD CONSTRAINT taxon_taxon_fk
FOREIGN KEY (parent_taxon_id)
REFERENCES public.taxon (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.seq_db_entry ADD CONSTRAINT taxon_seq_db_entry_fk
FOREIGN KEY (taxon_id)
REFERENCES public.taxon (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.taxon_extra_name ADD CONSTRAINT taxon_taxon_name_fk
FOREIGN KEY (taxon_id)
REFERENCES public.taxon (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.gene ADD CONSTRAINT taxon_gene_identifier_fk
FOREIGN KEY (taxon_id)
REFERENCES public.taxon (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.chromosome_location ADD CONSTRAINT taxon_chromosome_location_fk
FOREIGN KEY (taxon_id)
REFERENCES public.taxon (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.bio_sequence_gene_map ADD CONSTRAINT taxon_bio_sequence_chromosome_location_map_fk
FOREIGN KEY (taxon_id)
REFERENCES public.taxon (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.bio_sequence_annotation ADD CONSTRAINT taxon_bio_sequence_annotation_fk
FOREIGN KEY (taxon_id)
REFERENCES public.taxon (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.seq_db_entry_gene_map ADD CONSTRAINT gene_identifier_seq_db_entry_gene_identifier_map_fk
FOREIGN KEY (gene_id)
REFERENCES public.gene (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.chromosome_location ADD CONSTRAINT gene_identifier_gene_location_fk
FOREIGN KEY (gene_id)
REFERENCES public.gene (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.bio_sequence_gene_map ADD CONSTRAINT gene_bio_sequence_gene_map_fk
FOREIGN KEY (gene_id)
REFERENCES public.gene (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.seq_db_entry ADD CONSTRAINT protein_seq_db_entry_fk
FOREIGN KEY (bio_sequence_id)
REFERENCES public.bio_sequence (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.protein_identifier ADD CONSTRAINT protein_protein_identifier_fk
FOREIGN KEY (bio_sequence_id)
REFERENCES public.bio_sequence (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.bio_sequence_relation ADD CONSTRAINT bio_sequence_na_sequence_map_fk
FOREIGN KEY (na_sequence_id)
REFERENCES public.bio_sequence (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.bio_sequence_relation ADD CONSTRAINT bio_sequence_aa_sequence_map_fk
FOREIGN KEY (aa_sequence_id)
REFERENCES public.bio_sequence (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.bio_sequence_gene_map ADD CONSTRAINT bio_sequence_bio_sequence_gene_location_map_fk
FOREIGN KEY (bio_sequence_id)
REFERENCES public.bio_sequence (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.bio_sequence_annotation ADD CONSTRAINT bio_sequence_bio_sequence_annotation_fk
FOREIGN KEY (bio_sequence_id)
REFERENCES public.bio_sequence (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.fasta_file_entry_index ADD CONSTRAINT bio_sequence_fasta_db_entry_index_fk
FOREIGN KEY (bio_sequence_id)
REFERENCES public.bio_sequence (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.seq_db_entry_protein_identifier_map ADD CONSTRAINT protein_identifier_seq_db_entry_protein_identifier_map_fk
FOREIGN KEY (protein_identifier_id)
REFERENCES public.protein_identifier (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.seq_db_entry_protein_identifier_map ADD CONSTRAINT seq_db_entry_seq_db_entry_protein_identifier_map_fk
FOREIGN KEY (seq_db_entry_id)
REFERENCES public.seq_db_entry (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.seq_db_entry_gene_map ADD CONSTRAINT seq_db_entry_seq_db_entry_gene_identifier_map_fk
FOREIGN KEY (seq_db_entry_id)
REFERENCES public.seq_db_entry (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.seq_db_entry_object_tree_map ADD CONSTRAINT seq_db_entry_seq_db_entry_object_tree_map_fk
FOREIGN KEY (seq_db_entry_id)
REFERENCES public.seq_db_entry (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.fasta_file_entry_index ADD CONSTRAINT seq_db_entry_fasta_db_entry_index_fk
FOREIGN KEY (seq_db_entry_id)
REFERENCES public.seq_db_entry (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;
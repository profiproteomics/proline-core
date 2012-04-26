
CREATE TABLE admin_infos (
                model_version VARCHAR(1000) NOT NULL,
                db_creation_date DATE,
                model_update_date DATE,
                chr_location_update_timestamp TIMESTAMP,
                serialized_properties LONGVARCHAR,
                CONSTRAINT admin_infos_pk PRIMARY KEY (model_version)
);


CREATE TABLE object_tree_schema (
                name VARCHAR(1000) NOT NULL,
                type VARCHAR(10) NOT NULL,
                version VARCHAR(100) NOT NULL,
                schema LONGVARCHAR NOT NULL,
                description VARCHAR(1000),
                serialized_properties LONGVARCHAR,
                CONSTRAINT object_tree_schema_pk PRIMARY KEY (name)
);


CREATE TABLE object_tree (
                id IDENTITY NOT NULL,
                serialized_data LONGVARCHAR NOT NULL,
                serialized_properties LONGVARCHAR,
                schema_name VARCHAR(1000) NOT NULL,
                CONSTRAINT object_tree_pk PRIMARY KEY (id)
);


CREATE TABLE fasta_parsing_rule (
                id IDENTITY NOT NULL,
                db_type VARCHAR(100),
                entry_id VARCHAR(100) NOT NULL,
                entry_ac VARCHAR(100),
                entry_name VARCHAR(100) NOT NULL,
                gene_name VARCHAR(100),
                organism_name VARCHAR(100),
                taxon_id VARCHAR(100),
                CONSTRAINT fasta_parsing_rule_pk PRIMARY KEY (id)
);


CREATE TABLE seq_db_config (
                type VARCHAR(50) NOT NULL,
                alphabet CHAR(3) NOT NULL,
                ref_entry_format VARCHAR(10) NOT NULL,
                serialized_properties LONGVARCHAR,
                fasta_parsing_rule_id INTEGER NOT NULL,
                CONSTRAINT seq_db_config_pk PRIMARY KEY (type)
);
COMMENT ON COLUMN seq_db_config.type IS 'ipi, sprot, trembl, ncbi';
COMMENT ON COLUMN seq_db_config.ref_entry_format IS 'swiss/genebank/gff TODO: add support for gff format';


CREATE TABLE seq_db_release (
                id IDENTITY NOT NULL,
                date VARCHAR(50) NOT NULL,
                version VARCHAR(10),
                serialized_properties LONGVARCHAR,
                type VARCHAR(50) NOT NULL,
                CONSTRAINT seq_db_release_pk PRIMARY KEY (id)
);
COMMENT ON COLUMN seq_db_release.type IS 'ipi, uniprot.sprot, uniprot.trembl, ncbi';


CREATE TABLE seq_db_instance (
                id IDENTITY NOT NULL,
                name VARCHAR(100) NOT NULL,
                fasta_file_path VARCHAR(500) NOT NULL,
                ref_file_path VARCHAR(500),
                is_native BOOLEAN NOT NULL,
                is_indexed BOOLEAN NOT NULL,
                is_deleted BOOLEAN NOT NULL,
                creation_timestamp TIMESTAMP NOT NULL,
                sequence_count INTEGER NOT NULL,
                residue_count INTEGER,
                serialized_properties LONGVARCHAR,
                seq_db_release_id INTEGER NOT NULL,
                CONSTRAINT seq_db_instance_pk PRIMARY KEY (id)
);
COMMENT ON COLUMN seq_db_instance.is_native IS 'A native DB is a public DB which is neither a subset of database neither a database with additionnal or modified sequences (i.e. decoy sequences).';


CREATE TABLE taxon (
                id IDENTITY NOT NULL,
                scientific_name VARCHAR(512) NOT NULL,
                rank VARCHAR(30) NOT NULL,
                serialized_properties LONGVARCHAR,
                parent_taxon_id INTEGER NOT NULL,
                CONSTRAINT taxon_pk PRIMARY KEY (id)
);
COMMENT ON TABLE taxon IS 'Describes the NCBI taxononmy. TODO: add a is_active column';
COMMENT ON COLUMN taxon.id IS 'The NCBI taxon id';
COMMENT ON COLUMN taxon.scientific_name IS 'From NCBI: Every node in the database is required to have exactly one "scientific name".';


CREATE TABLE gene (
                id IDENTITY NOT NULL,
                name VARCHAR(100) NOT NULL,
                synonyms LONGVARCHAR,
                orf_names LONGVARCHAR,
                is_active BOOLEAN NOT NULL,
                serialized_properties LONGVARCHAR,
                taxon_id INTEGER NOT NULL,
                CONSTRAINT gene_pk PRIMARY KEY (id)
);
COMMENT ON TABLE gene IS 'UNIQUE(name, taxon_id)';
COMMENT ON COLUMN gene.synonyms IS 'A list of synonyms separated by spaces';
COMMENT ON COLUMN gene.orf_names IS 'A list of orf names separated by spaces';
COMMENT ON COLUMN gene.taxon_id IS 'The NCBI taxon id';


CREATE TABLE chromosome_location (
                id IDENTITY NOT NULL,
                chromosome_identifier VARCHAR(10) NOT NULL,
                location VARCHAR(250),
                serialized_properties LONGVARCHAR,
                gene_id INTEGER NOT NULL,
                taxon_id INTEGER NOT NULL,
                CONSTRAINT chromosome_location_pk PRIMARY KEY (id)
);
COMMENT ON TABLE chromosome_location IS 'paralogues, isoformes This table is deleted for active genes before each update. It contains the last known information about gene chromosome location.';
COMMENT ON COLUMN chromosome_location.serialized_properties IS 'TODO: put in schema exon, intron, strand';
COMMENT ON COLUMN chromosome_location.id IS 'The NCBI taxon id';


CREATE TABLE taxon_extra_name (
                id IDENTITY NOT NULL,
                class VARCHAR(256) NOT NULL,
                value INTEGER NOT NULL,
                serialized_properties LONGVARCHAR,
                taxon_id INTEGER NOT NULL,
                CONSTRAINT taxon_extra_name_pk PRIMARY KEY (id)
);
COMMENT ON COLUMN taxon_extra_name.taxon_id IS 'The NCBI taxon id';


CREATE TABLE bio_sequence (
                id IDENTITY NOT NULL,
                alphabet CHAR(3) NOT NULL,
                sequence LONGVARCHAR NOT NULL,
                length INTEGER,
                mass DOUBLE NOT NULL,
                pi REAL,
                crc64 VARCHAR(32) NOT NULL,
                serialized_properties LONGVARCHAR,
                CONSTRAINT bio_sequence_pk PRIMARY KEY (id)
);
COMMENT ON TABLE bio_sequence IS 'Like Uniparc, it  is a non-redundant protein sequence archive, containing both active and dead sequences, and it is species-merged since sequences are handled just as strings - all sequences 100% identical over the whole length of the sequence between species are merged. A sequence that exists in many copies in different databases is represented as a single entry which allows to identify the same protein from different sources. UNIQUE(alphabet,mass,crc64)';
COMMENT ON COLUMN bio_sequence.alphabet IS 'dna, rna or aa';
COMMENT ON COLUMN bio_sequence.sequence IS 'The sequence of the protein. It can contains amino acids or nucleic acids depending on the used alphabet.';
COMMENT ON COLUMN bio_sequence.length IS 'The length of the sequence.';
COMMENT ON COLUMN bio_sequence.mass IS 'The molecular mass of the protein or of the nucleic acid strand.';
COMMENT ON COLUMN bio_sequence.pi IS 'The isoelectric point of the protein. Only for protein sequences (alphabet=aa).';
COMMENT ON COLUMN bio_sequence.crc64 IS 'The numerical signature of the protein sequence';


CREATE TABLE bio_sequence_annotation (
                id IDENTITY NOT NULL,
                version VARCHAR(50) NOT NULL,
                serialized_properties LONGVARCHAR,
                bio_sequence_id INTEGER NOT NULL,
                taxon_id INTEGER NOT NULL,
                object_tree_id INTEGER NOT NULL,
                schema_name VARCHAR(1000) NOT NULL,
                CONSTRAINT bio_sequence_annotation_pk PRIMARY KEY (id)
);
COMMENT ON COLUMN bio_sequence_annotation.taxon_id IS 'The NCBI taxon id';


CREATE TABLE bio_sequence_gene_map (
                bio_sequence_id INTEGER NOT NULL,
                gene_id INTEGER NOT NULL,
                serialized_properties LONGVARCHAR,
                taxon_id INTEGER NOT NULL,
                CONSTRAINT bio_sequence_gene_map_pk PRIMARY KEY (bio_sequence_id, gene_id)
);
COMMENT ON COLUMN bio_sequence_gene_map.taxon_id IS 'The NCBI taxon id';


CREATE TABLE bio_sequence_relation (
                na_sequence_id INTEGER NOT NULL,
                aa_sequence_id INTEGER NOT NULL,
                frame_number INTEGER NOT NULL,
                CONSTRAINT bio_sequence_relation_pk PRIMARY KEY (na_sequence_id, aa_sequence_id)
);
COMMENT ON COLUMN bio_sequence_relation.frame_number IS 'The frame used to translate the nucleic acid strand 1,2,3 or -1,-2,-3 Must only be defined for nucleic acid sequences (alphabet equals dna/rna).';


CREATE TABLE protein_identifier (
                id IDENTITY NOT NULL,
                value VARCHAR(30) NOT NULL,
                is_ac_number BOOLEAN NOT NULL,
                is_active BOOLEAN NOT NULL,
                serialized_properties LONGVARCHAR,
                bio_sequence_id INTEGER NOT NULL,
                taxon_id INTEGER NOT NULL,
                database_type VARCHAR(50) NOT NULL,
                CONSTRAINT protein_identifier_pk PRIMARY KEY (id)
);
COMMENT ON TABLE protein_identifier IS 'An entry in a protein database identified by an accession number. UNIQUE( value, taxon_id )';
COMMENT ON COLUMN protein_identifier.value IS 'MUST be unique';
COMMENT ON COLUMN protein_identifier.is_ac_number IS 'true for accession numbers if the value corresponds to entry ID (ALB_HUMAN for instance) then this BOOLEAN will be false';
COMMENT ON COLUMN protein_identifier.taxon_id IS 'The NCBI taxon id';
COMMENT ON COLUMN protein_identifier.database_type IS 'ipi, uniprot.sprot, uniprot.trembl, ncbi';


CREATE TABLE seq_db_entry (
                id IDENTITY NOT NULL,
                identifier VARCHAR(50) NOT NULL,
                name VARCHAR(1000) NOT NULL,
                version VARCHAR(100),
                ref_file_block_start INTEGER,
                ref_file_block_length INTEGER,
                is_active BOOLEAN NOT NULL,
                serialized_properties LONGVARCHAR,
                bio_sequence_id INTEGER NOT NULL,
                taxon_id INTEGER NOT NULL,
                seq_db_instance_id INTEGER NOT NULL,
                database_type VARCHAR(50) NOT NULL,
                CONSTRAINT seq_db_entry_pk PRIMARY KEY (id)
);
COMMENT ON TABLE seq_db_entry IS 'Note: only inactive entries should kept is the previous instance when updated a sequence database to a newset release.';
COMMENT ON COLUMN seq_db_entry.identifier IS 'The ID of the sequence entry in the database instance. EX: ALB_HUMAN';
COMMENT ON COLUMN seq_db_entry.ref_file_block_start IS 'May be NULL if no available ref file.';
COMMENT ON COLUMN seq_db_entry.ref_file_block_length IS 'May be NULL if no available ref file.';
COMMENT ON COLUMN seq_db_entry.taxon_id IS 'The NCBI taxon id';
COMMENT ON COLUMN seq_db_entry.database_type IS 'ipi, sprot, trembl, ncbi';


CREATE TABLE fasta_file_entry_index (
                id IDENTITY NOT NULL,
                block_start INTEGER NOT NULL,
                block_length INTEGER NOT NULL,
                serialized_properties LONGVARCHAR,
                bio_sequence_id INTEGER NOT NULL,
                seq_db_entry_id INTEGER NOT NULL,
                seq_db_instance_id INTEGER NOT NULL,
                CONSTRAINT fasta_file_entry_index_pk PRIMARY KEY (id)
);
COMMENT ON COLUMN fasta_file_entry_index.bio_sequence_id IS 'May be used to specify a sequence variant of the main seq db entry.';


CREATE TABLE seq_db_entry_object_tree_map (
                seq_db_entry_id INTEGER NOT NULL,
                object_tree_id INTEGER NOT NULL,
                schema_name VARCHAR(1000) NOT NULL,
                CONSTRAINT seq_db_entry_object_tree_map_pk PRIMARY KEY (seq_db_entry_id, object_tree_id)
);


CREATE TABLE seq_db_entry_gene_map (
                seq_db_entry_id INTEGER NOT NULL,
                gene_id INTEGER NOT NULL,
                seq_db_instance_id INTEGER NOT NULL,
                CONSTRAINT seq_db_entry_gene_map_pk PRIMARY KEY (seq_db_entry_id, gene_id)
);


CREATE TABLE seq_db_entry_protein_identifier_map (
                seq_db_entry_id INTEGER NOT NULL,
                protein_identifier_id INTEGER NOT NULL,
                seq_db_instance_id INTEGER NOT NULL,
                CONSTRAINT seq_db_entry_protein_identifier_map_pk PRIMARY KEY (seq_db_entry_id, protein_identifier_id)
);
COMMENT ON TABLE seq_db_entry_protein_identifier_map IS 'Note: the same protein identifier shouldn''t be find in multiple instancesof the same seq database.';


/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE bio_sequence_annotation ADD CONSTRAINT object_tree_schema_bio_sequence_annotation_fk
FOREIGN KEY (schema_name)
REFERENCES object_tree_schema (name)
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE object_tree ADD CONSTRAINT object_tree_schema_object_tree_fk
FOREIGN KEY (schema_name)
REFERENCES object_tree_schema (name)
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE seq_db_entry_object_tree_map ADD CONSTRAINT object_tree_schema_seq_db_entry_object_tree_map_fk
FOREIGN KEY (schema_name)
REFERENCES object_tree_schema (name)
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE bio_sequence_annotation ADD CONSTRAINT object_tree_bio_sequence_instance_fk
FOREIGN KEY (object_tree_id)
REFERENCES object_tree (id)
ON UPDATE SET NULL;

ALTER TABLE seq_db_entry_object_tree_map ADD CONSTRAINT object_tree_seq_db_entry_object_tree_map_fk
FOREIGN KEY (object_tree_id)
REFERENCES object_tree (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE seq_db_config ADD CONSTRAINT fasta_parsing_rule_seq_database_fk
FOREIGN KEY (fasta_parsing_rule_id)
REFERENCES fasta_parsing_rule (id)
ON DELETE NO ACTION
ON UPDATE SET NULL;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE protein_identifier ADD CONSTRAINT seq_database_protein_identifier_fk
FOREIGN KEY (database_type)
REFERENCES seq_db_config (type)
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE seq_db_entry ADD CONSTRAINT seq_database_seq_db_entry_fk
FOREIGN KEY (database_type)
REFERENCES seq_db_config (type)
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE seq_db_release ADD CONSTRAINT seq_database_seq_db_release_fk
FOREIGN KEY (type)
REFERENCES seq_db_config (type)
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE seq_db_instance ADD CONSTRAINT seq_db_release_seq_db_instance_fk
FOREIGN KEY (seq_db_release_id)
REFERENCES seq_db_release (id)
ON UPDATE NO ACTION;

ALTER TABLE seq_db_entry ADD CONSTRAINT seq_db_release_seq_db_entry_fk
FOREIGN KEY (seq_db_instance_id)
REFERENCES seq_db_instance (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE fasta_file_entry_index ADD CONSTRAINT seq_db_instance_db_entry_index_fk
FOREIGN KEY (seq_db_instance_id)
REFERENCES seq_db_instance (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE seq_db_entry_gene_map ADD CONSTRAINT seq_db_instance_seq_db_entry_gene_identifier_map_fk
FOREIGN KEY (seq_db_instance_id)
REFERENCES seq_db_instance (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

ALTER TABLE seq_db_entry_protein_identifier_map ADD CONSTRAINT seq_db_instance_seq_db_entry_protein_identifier_map_fk
FOREIGN KEY (seq_db_instance_id)
REFERENCES seq_db_instance (id)
ON DELETE NO ACTION
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE protein_identifier ADD CONSTRAINT taxon_protein_identifier_fk
FOREIGN KEY (taxon_id)
REFERENCES taxon (id)
ON UPDATE NO ACTION;

ALTER TABLE taxon ADD CONSTRAINT taxon_taxon_fk
FOREIGN KEY (parent_taxon_id)
REFERENCES taxon (id)
ON DELETE SET NULL
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE seq_db_entry ADD CONSTRAINT taxon_seq_db_entry_fk
FOREIGN KEY (taxon_id)
REFERENCES taxon (id)
ON UPDATE NO ACTION;

ALTER TABLE taxon_extra_name ADD CONSTRAINT taxon_taxon_name_fk
FOREIGN KEY (taxon_id)
REFERENCES taxon (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE gene ADD CONSTRAINT taxon_gene_identifier_fk
FOREIGN KEY (taxon_id)
REFERENCES taxon (id)
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE chromosome_location ADD CONSTRAINT taxon_chromosome_location_fk
FOREIGN KEY (id)
REFERENCES taxon (id)
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE bio_sequence_gene_map ADD CONSTRAINT taxon_bio_sequence_chromosome_location_map_fk
FOREIGN KEY (taxon_id)
REFERENCES taxon (id)
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE bio_sequence_annotation ADD CONSTRAINT taxon_bio_sequence_annotation_fk
FOREIGN KEY (taxon_id)
REFERENCES taxon (id)
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE seq_db_entry_gene_map ADD CONSTRAINT gene_identifier_seq_db_entry_gene_identifier_map_fk
FOREIGN KEY (gene_id)
REFERENCES gene (id)
ON UPDATE NO ACTION;

ALTER TABLE chromosome_location ADD CONSTRAINT gene_identifier_gene_location_fk
FOREIGN KEY (gene_id)
REFERENCES gene (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE bio_sequence_gene_map ADD CONSTRAINT gene_bio_sequence_gene_map_fk
FOREIGN KEY (gene_id)
REFERENCES gene (id)
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE seq_db_entry ADD CONSTRAINT protein_seq_db_entry_fk
FOREIGN KEY (bio_sequence_id)
REFERENCES bio_sequence (id)
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE protein_identifier ADD CONSTRAINT protein_protein_identifier_fk
FOREIGN KEY (bio_sequence_id)
REFERENCES bio_sequence (id)
ON UPDATE NO ACTION;

ALTER TABLE bio_sequence_relation ADD CONSTRAINT bio_sequence_na_sequence_map_fk
FOREIGN KEY (na_sequence_id)
REFERENCES bio_sequence (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE bio_sequence_relation ADD CONSTRAINT bio_sequence_aa_sequence_map_fk
FOREIGN KEY (aa_sequence_id)
REFERENCES bio_sequence (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE bio_sequence_gene_map ADD CONSTRAINT bio_sequence_bio_sequence_gene_location_map_fk
FOREIGN KEY (bio_sequence_id)
REFERENCES bio_sequence (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE bio_sequence_annotation ADD CONSTRAINT bio_sequence_bio_sequence_annotation_fk
FOREIGN KEY (bio_sequence_id)
REFERENCES bio_sequence (id)
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE fasta_file_entry_index ADD CONSTRAINT bio_sequence_fasta_db_entry_index_fk
FOREIGN KEY (bio_sequence_id)
REFERENCES bio_sequence (id)
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE seq_db_entry_protein_identifier_map ADD CONSTRAINT protein_identifier_seq_db_entry_protein_identifier_map_fk
FOREIGN KEY (protein_identifier_id)
REFERENCES protein_identifier (id)
ON UPDATE NO ACTION;

ALTER TABLE seq_db_entry_protein_identifier_map ADD CONSTRAINT seq_db_entry_seq_db_entry_protein_identifier_map_fk
FOREIGN KEY (seq_db_entry_id)
REFERENCES seq_db_entry (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE seq_db_entry_gene_map ADD CONSTRAINT seq_db_entry_seq_db_entry_gene_identifier_map_fk
FOREIGN KEY (seq_db_entry_id)
REFERENCES seq_db_entry (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE seq_db_entry_object_tree_map ADD CONSTRAINT seq_db_entry_seq_db_entry_object_tree_map_fk
FOREIGN KEY (seq_db_entry_id)
REFERENCES seq_db_entry (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE fasta_file_entry_index ADD CONSTRAINT seq_db_entry_fasta_db_entry_index_fk
FOREIGN KEY (seq_db_entry_id)
REFERENCES seq_db_entry (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

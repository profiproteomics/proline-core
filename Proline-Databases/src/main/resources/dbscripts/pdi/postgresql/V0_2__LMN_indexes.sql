-- Mainly performance optimizations for Uniprot imports

ALTER TABLE seq_db_config
  ALTER COLUMN alphabet TYPE VARCHAR(3);

CREATE INDEX bio_sequence_crc_upper_idx
ON bio_sequence (upper(crc64));

CREATE INDEX protein_identifier_bio_sequence_id_idx
ON protein_identifier (bio_sequence_id);

CREATE INDEX gene_name_upper_idx
ON gene (upper(name));

CREATE INDEX bio_sequence_gene_map_gene_id_idx
ON bio_sequence_gene_map (gene_id);

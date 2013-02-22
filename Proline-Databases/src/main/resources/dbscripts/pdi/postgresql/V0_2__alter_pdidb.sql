ALTER TABLE seq_db_config
  ALTER alphabet TYPE VARCHAR(3) ;

DROP INDEX protein_identifier_value_taxon_idx;

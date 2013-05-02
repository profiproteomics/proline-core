ALTER TABLE bio_sequence ALTER COLUMN mass INTEGER NOT NULL;
COMMENT ON COLUMN bio_sequence.mass IS 'The approximated molecular mass of the protein or of the nucleic acid strand.';

ALTER TABLE taxon ADD COLUMN is_active BOOLEAN NOT NULL;
COMMENT ON TABLE taxon IS 'Describes the NCBI taxononmy.';

ALTER TABLE gene DROP COLUMN orf_names;
ALTER TABLE gene ADD COLUMN name_type VARCHAR(20) NOT NULL;
-- Add PeptideReadablePtmString entity

CREATE TABLE peptide_readable_ptm_string (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    readable_ptm_string VARCHAR NOT NULL,
    peptide_id BIGINT NOT NULL,
    result_set_id BIGINT NOT NULL,
    FOREIGN KEY (peptide_id) REFERENCES peptide (id),
    FOREIGN KEY (result_set_id) REFERENCES result_set (id)               
);

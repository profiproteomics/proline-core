CREATE TABLE public.peptide_readable_ptm_string (
  id IDENTITY NOT NULL,
  readable_ptm_string VARCHAR NOT NULL,
  peptide_id BIGINT NOT NULL,
  result_set_id BIGINT NOT NULL,
  CONSTRAINT id PRIMARY KEY (id)
);
COMMENT ON COLUMN public.peptide_readable_ptm_string.readable_ptm_string IS 'Human-readable PTM string.';

CREATE UNIQUE INDEX public.peptide_id_result_set_id_idx
  ON public.peptide_readable_ptm_string ( peptide_id, result_set_id );

ALTER TABLE public.peptide_readable_ptm_string ADD CONSTRAINT peptide_peptide_readable_ptm_string_fk
  FOREIGN KEY (peptide_id)
  REFERENCES public.peptide (id)
  ON DELETE NO ACTION
  ON UPDATE NO ACTION;

ALTER TABLE public.peptide_readable_ptm_string ADD CONSTRAINT result_set_peptide_readable_ptm_string_fk
  FOREIGN KEY (result_set_id)
  REFERENCES public.result_set (id)
  ON DELETE NO ACTION
  ON UPDATE NO ACTION;

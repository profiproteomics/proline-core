
DROP INDEX public.ptm_full_name_idx;

CREATE INDEX public.ptm_full_name_idx ON public.ptm ( full_name );
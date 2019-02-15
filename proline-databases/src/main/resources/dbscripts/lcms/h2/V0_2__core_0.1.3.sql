
ALTER TABLE public.processed_map_run_map_mapping DROP CONSTRAINT run_map_processed_map_run_map_mapping_fk;

ALTER TABLE public.run_map DROP CONSTRAINT run_run_map_fk;

ALTER TABLE public.scan DROP CONSTRAINT run_scan_fk;
ALTER TABLE public.processed_map_run_map_mapping RENAME TO processed_map_raw_map_mapping;

ALTER TABLE public.processed_map_raw_map_mapping ALTER COLUMN run_map_id RENAME TO raw_map_id;
ALTER TABLE public.run_map RENAME TO raw_map;

ALTER TABLE public.raw_map ALTER COLUMN run_id RENAME TO scan_sequence_id;
ALTER TABLE public.run RENAME TO scan_sequence;

ALTER TABLE public.scan ALTER COLUMN run_id RENAME TO scan_sequence_id;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE public.processed_map_raw_map_mapping ADD CONSTRAINT raw_map_processed_map_raw_map_mapping_fk
FOREIGN KEY (raw_map_id)
REFERENCES public.raw_map (id)
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE public.raw_map ADD CONSTRAINT scan_sequence_raw_map_fk
FOREIGN KEY (scan_sequence_id)
REFERENCES public.scan_sequence (id)
ON UPDATE NO ACTION;

ALTER TABLE public.scan ADD CONSTRAINT scan_sequence_scan_fk
FOREIGN KEY (scan_sequence_id)
REFERENCES public.scan_sequence (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;
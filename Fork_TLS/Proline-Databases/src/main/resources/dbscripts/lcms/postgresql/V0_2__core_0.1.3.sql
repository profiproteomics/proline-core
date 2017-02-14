
ALTER TABLE public.processed_map_run_map_mapping DROP CONSTRAINT run_map_processed_map_run_map_mapping_fk;

ALTER TABLE public.run_map DROP CONSTRAINT run_run_map_fk;

ALTER TABLE public.scan DROP CONSTRAINT run_scan_fk;
ALTER TABLE processed_map_run_map_mapping RENAME TO processed_map_raw_map_mapping;

ALTER TABLE public.processed_map_raw_map_mapping RENAME COLUMN run_map_id TO raw_map_id;
ALTER TABLE run_map RENAME TO raw_map;

ALTER TABLE public.raw_map RENAME COLUMN run_id TO scan_sequence_id;
ALTER TABLE run RENAME TO scan_sequence;

ALTER TABLE public.scan RENAME COLUMN run_id TO scan_sequence_id;

ALTER TABLE public.processed_map_raw_map_mapping ADD CONSTRAINT raw_map_processed_map_raw_map_mapping_fk
FOREIGN KEY (raw_map_id)
REFERENCES public.raw_map (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.raw_map ADD CONSTRAINT scan_sequence_raw_map_fk
FOREIGN KEY (scan_sequence_id)
REFERENCES public.scan_sequence (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.scan ADD CONSTRAINT scan_sequence_scan_fk
FOREIGN KEY (scan_sequence_id)
REFERENCES public.scan_sequence (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

/* --- MAIN database changes relative to  PSdb removal branch --- */
-- Remove column instrument from scan_sequence  --
ALTER TABLE scan_sequence DROP CONSTRAINT instrument_scan_sequence_fk;
ALTER TABLE public.scan_sequence DROP COLUMN instrument_id;

-- Remove tables 'instrument'  --
DROP TABLE public.instrument ;


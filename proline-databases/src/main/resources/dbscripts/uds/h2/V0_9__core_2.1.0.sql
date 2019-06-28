-- Add Missing SILAC residue tags & methods

/* ADDITIONAL SQL QUERIES USED FOR DATA UPDATE */

INSERT INTO  quant_method(name,type,abundance_unit)  values
  ('SILAC 2plex', 'residue_labeling', 'feature_intensity'),
  ('SILAC 3plex', 'residue_labeling', 'feature_intensity');


INSERT INTO quant_label (name, type, number, quant_method_id)
 values
  ('Light', 'residue_label', 1,(SELECT id FROM quant_method WHERE name = 'SILAC 2plex')),
  ('Heavy', 'residue_label', 2,(SELECT id FROM quant_method WHERE name = 'SILAC 2plex'));


INSERT INTO quant_label (name,type,  number, quant_method_id)
values
  ('Light', 'residue_label', 1, (SELECT id FROM quant_method WHERE name = 'SILAC 3plex')),
  ('Medium', 'residue_label', 2, (SELECT id FROM quant_method WHERE name = 'SILAC 3plex')),
  ('Heavy', 'residue_label', 3, (SELECT id FROM quant_method WHERE name = 'SILAC 3plex'));


-- Rename quant_label.type value 'isobaric_tagging' to 'isobaric_tag' --
UPDATE quant_label SET type = 'isobaric_tag' WHERE type = 'isobaric_tagging';


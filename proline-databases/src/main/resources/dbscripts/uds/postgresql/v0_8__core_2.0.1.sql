-- Add Missing SILAC residue tags & methods
WITH silac_methods(name,type,abundance_unit) as (
  values
  ('SILAC 2plex', 'residue_labeling', 'feature_intensity'),
  ('SILAC 3plex', 'residue_labeling', 'feature_intensity')
)

INSERT INTO quant_method (name,type, abundance_unit)
SELECT name,type,abundance_unit
FROM silac_methods
WHERE NOT EXISTS (SELECT 1
                  FROM quant_method qm
                  WHERE qm.name = silac_methods.name);

WITH tags(name, type, number) as (
  values
  ('Light', 'residue_label', 1),
  ('Heavy', 'residue_label', 2)
)
INSERT INTO quant_label (type, name, number, quant_method_id)
SELECT type, name, number, (SELECT id FROM quant_method WHERE name = 'SILAC 2plex')
FROM tags;

WITH tags(name, type, number) as (
  values
  ('Light', 'residue_label', 1),
  ('Medium', 'residue_label', 2),
  ('Heavy', 'residue_label', 3)
)
INSERT INTO quant_label (type, name, number, quant_method_id)
SELECT type, name, number, (SELECT id FROM quant_method WHERE name = 'SILAC 3plex')
FROM tags;



-- Add Missing scoring 
WITH all_values (search_engine,name,description) as (
  values 
  ('mascot', 'ions score', 'The score provided for each Mascot peptide.'),
  ('mascot', 'standard score', 'The score provided for each Mascot protein hit (it corresponds to the sum of ion scores).'),
  ('mascot', 'mudpit score', 'The score provided for each Mascot protein hit when the number of MS/MS queries is high.'),
  ('mascot', 'modified mudpit score', 'A modified version of the MudPIT score computed by Proline.'),
  ('omssa', 'expect value', 'The -log(E-value) provided by OMSSA for a peptide match.'), 
  ('comet', 'evalue log scaled', 'The -log(expectation value) provided by Comet for a peptide match.'),
  ('msgf', 'evalue log scaled', 'The -log(EValue) provided by MS-GF for a peptide match.'),
  ('sequest', 'expect log scaled', 'The -log(expect) provided by Sequest for a peptide match.'),
  ('xtandem', 'hyperscore', 'The hyperscore provided by X!Tandem for a peptide match.'),
  ('maxquant','score','The score provided by MaxQuant for a peptide match.' )
)

INSERT INTO scoring (search_engine,name,description)
SELECT search_engine,name,description
FROM all_values
WHERE NOT EXISTS (SELECT 1 
                  FROM scoring sc 
                  WHERE sc.name = all_values.name AND sc.search_engine = all_values.search_engine);

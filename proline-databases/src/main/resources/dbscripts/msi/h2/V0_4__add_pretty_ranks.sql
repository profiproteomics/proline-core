
ALTER TABLE public.peptide_match ADD COLUMN cd_pretty_rank INTEGER;
ALTER TABLE public.peptide_match ADD COLUMN sd_pretty_rank INTEGER;

COMMENT ON COLUMN public.peptide_match.cd_pretty_rank IS 'Pretty rank recalculated when importing a new ResultSet from a concatenated database. The PSMs corresponding to the same query are sorted by decreasing score, PSMs with very close scores (less than 0.1) are considered equals and will get the same pretty rank. This pretty rank is calculated with PSMs from both target and decoy ResultSets.';
COMMENT ON COLUMN public.peptide_match.sd_pretty_rank IS 'Pretty rank recalculated when importing a ResultSet from a separated database. The PSMs corresponding to the same query are sorted by decreasing score, PSMs with very close scores (less than 0.1) are considered equals and will get the same pretty rank. This pretty rank is calculated with PSMs only from target or decoy ResultSet.';

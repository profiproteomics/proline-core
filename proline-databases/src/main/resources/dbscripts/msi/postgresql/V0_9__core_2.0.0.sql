
/* --- MAIN database changes relative to the PSdb removal --- */

--add column atom_label_id to the already exit table MSI_db.peptide 
ALTER TABLE public.peptide ADD COLUMN atom_label_id BIGINT;
--add column ptm_id to the already exist table MSI_db.ptm_specificity
ALTER TABLE public.ptm_specificity ADD COLUMN ptm_id BIGINT;
--add column classification_id to the already exist table MSI_db.ptm_specificity
ALTER TABLE public.ptm_specificity ADD COLUMN classification_id BIGINT;

CREATE SEQUENCE public.peptide_id_seq;
ALTER TABLE public.peptide ALTER COLUMN id SET DEFAULT nextval('public.peptide_id_seq');
ALTER SEQUENCE public.peptide_id_seq OWNED BY public.peptide.id;

CREATE SEQUENCE public.ptm_specificity_id_seq;
ALTER TABLE public.ptm_specificity ALTER COLUMN id SET DEFAULT nextval('public.ptm_specificity_id_seq');
ALTER SEQUENCE public.ptm_specificity_id_seq OWNED BY public.ptm_specificity.id;

CREATE SEQUENCE public.ptm_classification_id_seq;

CREATE TABLE public.ptm_classification (
                id BIGINT NOT NULL DEFAULT nextval('public.ptm_classification_id_seq'),
                name VARCHAR(1000) NOT NULL,
                CONSTRAINT ptm_classification_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.ptm_classification IS 'A controlled list of PTM categories.';
COMMENT ON COLUMN public.ptm_classification.name IS 'The name of the PTM classification.
Allowed values are:
Post-translational
Co-translational
Pre-translational
Chemical derivative
Artefact
N-linked glycosylation
O-linked glycosylation
Other glycosylation
Synth. pep. protect. gp.
Isotopic label
Non-standard residue
Multiple
Other
AA substitution';

ALTER SEQUENCE public.ptm_classification_id_seq OWNED BY public.ptm_classification.id;

CREATE UNIQUE INDEX ptm_classification_idx
 ON public.ptm_classification
 ( name );

CREATE SEQUENCE public.atom_label_id_seq;

CREATE TABLE public.atom_label (
                id BIGINT NOT NULL DEFAULT nextval('public.atom_label_id_seq'),
                name VARCHAR(100) NOT NULL,
                symbol VARCHAR(2) NOT NULL,
                mono_mass DOUBLE PRECISION NOT NULL,
                average_mass DOUBLE PRECISION NOT NULL,
                serialized_properties TEXT,
                CONSTRAINT atom_label_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.atom_label IS 'Enables the description of 14N/15N and 16O/18O labeling.';
COMMENT ON COLUMN public.atom_label.name IS 'The name of the label. EX: 15N';
COMMENT ON COLUMN public.atom_label.symbol IS 'The symbol of the atom. EX: N';
COMMENT ON COLUMN public.atom_label.mono_mass IS 'The monoisotopic mass of the corresponding isotope.';
COMMENT ON COLUMN public.atom_label.average_mass IS 'The average mass of the corresponding isotope.';
COMMENT ON COLUMN public.atom_label.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';

ALTER SEQUENCE public.atom_label_id_seq OWNED BY public.atom_label.id;

CREATE UNIQUE INDEX peptide_sequence_ptm_idx
 ON public.peptide
 ( sequence, ptm_string );

CREATE SEQUENCE public.ptm_id_seq;

CREATE TABLE public.ptm (
                id BIGINT NOT NULL DEFAULT nextval('public.ptm_id_seq'),
                unimod_id BIGINT,
                full_name VARCHAR(1000),
                short_name VARCHAR(100) NOT NULL,
                serialized_properties TEXT,
                CONSTRAINT ptm_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.ptm IS 'Describes the names of the ptm definitions.
UNIQUE(short_name)';
COMMENT ON COLUMN public.ptm.unimod_id IS 'The unimod record_id.';
COMMENT ON COLUMN public.ptm.full_name IS 'A description of the PTM.';
COMMENT ON COLUMN public.ptm.short_name IS 'Descriptive, one word name, suitable for use in software applications.
This name must not include the specificity. For example, Carboxymethyl is the short name, not Carboxymethyl-Cys or Carboxymethyl (C).
MUST BE UNIQUE.';
COMMENT ON COLUMN public.ptm.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';

ALTER SEQUENCE public.ptm_id_seq OWNED BY public.ptm.id;

CREATE INDEX ptm_full_name_idx
 ON public.ptm
 ( full_name );

CREATE UNIQUE INDEX ptm_short_name_idx
 ON public.ptm
 ( short_name );

CREATE UNIQUE INDEX ptm_specificity_idx
 ON public.ptm_specificity
 ( location, residue, ptm_id );

CREATE SEQUENCE public.ptm_evidence_id_seq;

CREATE TABLE public.ptm_evidence (
                id BIGINT NOT NULL DEFAULT nextval('public.ptm_evidence_id_seq'),
                type VARCHAR(14) NOT NULL,
                is_required BOOLEAN NOT NULL,
                composition VARCHAR(50) NOT NULL,
                mono_mass DOUBLE PRECISION NOT NULL,
                average_mass DOUBLE PRECISION NOT NULL,
                serialized_properties TEXT,
                specificity_id BIGINT,
                ptm_id BIGINT,
                CONSTRAINT ptm_evidence_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.ptm_evidence IS 'Ptm associated ions/delta. Only one "Precursor" delta type MUST be defined for each ptm.
A PTM evidence can be linked to a PTM OR a PTM specificity.';
COMMENT ON COLUMN public.ptm_evidence.type IS 'The type of the PTM evidence.

Allowed types are:
- Precursor =>  delta for the precursor ion
- Artefact => associated artefact peaks
- NeutralLoss => fragment ion neutral loss
- PepNeutralLoss => precursor ion neutral loss';
COMMENT ON COLUMN public.ptm_evidence.is_required IS 'Specify if the presence of this PTM evidence is required for the peptide identification/scoring.
True for "Precursor" PTM evidence, for "Scoring Neutral Loss" (flag=false in unmod.xml) and for "Required Peptide Neutral Loss" (required=true in unimod.xml).
For more information see mascot Neutral Loss definition and unimod.xsd';
COMMENT ON COLUMN public.ptm_evidence.composition IS 'The chemical composition of the modification as a delta between the modified and unmodified residue or terminus. The formula is displayed and entered as ''atoms'', optionally followed by a number in parentheses. The atom terms are separated by spaces, and order is not important. For example, if the modification removes an H and adds a CH3 group, the Composition would be shown as H(2) C. Atoms can be either elements or molecular sub-units. The number may be negative and, if there is no number, 1 is assumed. Hence, H(2) C is the same as H(2) C(1).';
COMMENT ON COLUMN public.ptm_evidence.mono_mass IS 'The monoisotopic mass associated to the PTM evidence entity.';
COMMENT ON COLUMN public.ptm_evidence.average_mass IS 'The average mass associated to the PTM evidence entity.';
COMMENT ON COLUMN public.ptm_evidence.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';

ALTER SEQUENCE public.ptm_evidence_id_seq OWNED BY public.ptm_evidence.id;

CREATE SEQUENCE public.peptide_ptm_id_seq;

CREATE TABLE public.peptide_ptm (
                id BIGINT NOT NULL DEFAULT nextval('public.peptide_ptm_id_seq'),
                seq_position INTEGER NOT NULL,
                mono_mass DOUBLE PRECISION NOT NULL,
                average_mass DOUBLE PRECISION NOT NULL,
                serialized_properties TEXT,
                peptide_id BIGINT NOT NULL,
                ptm_specificity_id BIGINT NOT NULL,
                atom_label_id BIGINT,
                CONSTRAINT peptide_ptm_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.peptide_ptm IS 'Describes the PTM''s associated to a given peptide';
COMMENT ON COLUMN public.peptide_ptm.seq_position IS 'The position of the PTM relative to the peptide sequence.
Allowed values:
* 0 means N-ter
* -1 means C-ter
* other integer values give the position inside the peptide sequence.';
COMMENT ON COLUMN public.peptide_ptm.mono_mass IS 'The monoisotopic mass of the corresponding PTM.';
COMMENT ON COLUMN public.peptide_ptm.average_mass IS 'The average mass of the corresponding PTM.';
COMMENT ON COLUMN public.peptide_ptm.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';

ALTER SEQUENCE public.peptide_ptm_id_seq OWNED BY public.peptide_ptm.id;

CREATE INDEX peptide_ptm_peptide_idx
 ON public.peptide_ptm
 ( peptide_id );

ALTER TABLE public.ptm_specificity ADD CONSTRAINT ptm_classification_ptm_specificity_fk
FOREIGN KEY (classification_id)
REFERENCES public.ptm_classification (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_ptm ADD CONSTRAINT atom_label_peptide_ptm_fk
FOREIGN KEY (atom_label_id)
REFERENCES public.atom_label (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide ADD CONSTRAINT atom_label_peptide_fk
FOREIGN KEY (atom_label_id)
REFERENCES public.atom_label (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_ptm ADD CONSTRAINT peptide_peptide_ptm_fk
FOREIGN KEY (peptide_id)
REFERENCES public.peptide (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.ptm_evidence ADD CONSTRAINT ptm_ptm_ion_fk
FOREIGN KEY (ptm_id)
REFERENCES public.ptm (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.ptm_specificity ADD CONSTRAINT ptm_ptm_specificity_fk
FOREIGN KEY (ptm_id)
REFERENCES public.ptm (id)
ON DELETE CASCADE
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.peptide_ptm ADD CONSTRAINT ptm_specificity_peptide_ptm_fk
FOREIGN KEY (ptm_specificity_id)
REFERENCES public.ptm_specificity (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

ALTER TABLE public.ptm_evidence ADD CONSTRAINT ptm_specificity_ptm_evidence_fk
FOREIGN KEY (specificity_id)
REFERENCES public.ptm_specificity (id)
ON DELETE RESTRICT
ON UPDATE NO ACTION
NOT DEFERRABLE;

/* --- Other changes relative to core version 2.0 (redmine issue #15671) --- */

/* SCRIPT GENERATED BY POWER ARCHITECT AND MODIFIED MANUALLY */

-- Remove tables 'admin_infos' & 'cache’ --
DROP TABLE public.admin_infos;
DROP TABLE public.cache;

-- Remove column protein_match.coverage --
ALTER TABLE public.protein_match DROP COLUMN coverage;

-- Increase max length of protein_match.accession from 100 to 10000 --
ALTER TABLE ONLY public.protein_match ALTER COLUMN accession TYPE VARCHAR(10000), ALTER COLUMN accession SET NOT NULL;

-- Rename result_set.modification_timestamp to result_set.creation_timestamp --
ALTER TABLE public.result_set RENAME COLUMN modification_timestamp TO creation_timestamp;

-- Relax MSIdb constraint for fixed & variable PTMs (redmine issue #15607) --
ALTER TABLE public.used_ptm DROP CONSTRAINT used_ptm_pk;
ALTER TABLE public.used_ptm ADD COLUMN search_round INTEGER DEFAULT 1 NOT NULL;
ALTER TABLE public.used_ptm ADD PRIMARY KEY (search_settings_id, ptm_specificity_id, search_round);

ALTER TABLE public.search_settings ADD COLUMN fragmentation_rule_set_id BIGINT;
ALTER TABLE public.spectrum ADD COLUMN fragmentation_rule_set_id BIGINT;
ALTER TABLE public.spectrum DROP COLUMN instrument_config_id;

/* END OF SCRIPT GENERATED BY POWER ARCHITECT AND MODIFIED MANUALLY */


/* ADDITIONAL SQL QUERIES USED FOR DATA UPDATE */

-- Fix result_summary.is_quantified is never true
UPDATE result_summary SET is_quantified = 't' WHERE result_set_id IN (SELECT id FROM result_set WHERE type = 'QUANTITATION');

-- Fix filter properties => RANK filter should be renamed PRETTY_RANK filter
UPDATE result_summary SET serialized_properties=replace(serialized_properties, '"parameter":"RANK"','"parameter":"PRETTY_RANK"') WHERE serialized_properties IS NOT NULL;

-- Rename PEPTIDE_MODIFIED in master_quant_component props to "selection_changed"
UPDATE master_quant_component SET serialized_properties=replace(serialized_properties,'PEPTIDE_MODIFIED','selection_changed') WHERE schema_name = 'object_tree.quant_protein_sets';

/* END OF ADDITIONAL SQL QUERIES USED FOR DATA UPDATE */


/* LIST OF OPERATIONS TO BE PERFORMED IN THE NEXT JAVA MIGRATION */
-- add "peptidesCount: Int" to the QuantProteinSet JSON model
-- fill peptideMatchesCounts field in class MasterQuantProteinSetProfile
-- TODO: ommssa ionSeries should not be stored in the PeptideMatch (use an object tree in the MSIdb or dedicated instrument config)

/* END LIST OF OPERATIONS TO BE PERFORMED IN THE NEXT JAVA MIGRATION */

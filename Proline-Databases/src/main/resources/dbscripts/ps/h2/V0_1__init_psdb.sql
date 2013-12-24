
CREATE TABLE public.ptm_classification (
                id IDENTITY NOT NULL,
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


CREATE UNIQUE INDEX public.ptm_classification_idx
 ON public.ptm_classification
 ( name );

CREATE TABLE public.atom_label (
                id IDENTITY NOT NULL,
                name VARCHAR(100) NOT NULL,
                symbol VARCHAR(2) NOT NULL,
                mono_mass DOUBLE NOT NULL,
                average_mass DOUBLE NOT NULL,
                serialized_properties LONGVARCHAR,
                CONSTRAINT atom_label_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.atom_label IS 'Enables the description of 14N/15N and 16O/18O labeling.';
COMMENT ON COLUMN public.atom_label.name IS 'The name of the label. EX: 15N';
COMMENT ON COLUMN public.atom_label.symbol IS 'The symbol of the atom. EX: N';
COMMENT ON COLUMN public.atom_label.mono_mass IS 'The monoisotopic mass of the corresponding isotope.';
COMMENT ON COLUMN public.atom_label.average_mass IS 'The average mass of the corresponding isotope.';
COMMENT ON COLUMN public.atom_label.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


CREATE TABLE public.peptide (
                id IDENTITY NOT NULL,
                sequence LONGVARCHAR NOT NULL,
                ptm_string LONGVARCHAR,
                calculated_mass DOUBLE NOT NULL,
                serialized_properties LONGVARCHAR,
                atom_label_id BIGINT,
                CONSTRAINT peptide_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.peptide IS 'A peptide is an amino acid (AA) sequence with given PTMs. A peptide has a unique pair of sequence/PTM string.';
COMMENT ON COLUMN public.peptide.sequence IS 'The AA sequence of this peptide.';
COMMENT ON COLUMN public.peptide.ptm_string IS 'A string that describes the ptm structure.
EX: given the sequence MENHIR having the PTMs oxidation (M) and SILAC label (R) the unique corresponding PTM string is "1[O]7[C(-9) 13C(9)]".
Each PTM is described by its delta composition. The prefix number gives the position of ptm on the peptide. The atomic number MUST be explicited for non natural isotope only (EX: 15N) . The number of added (or removed) atoms MUST be specified ONLY if more than one atom is concerned. Must be also defined for atom labeling (EX: N(-1) 15N).';
COMMENT ON COLUMN public.peptide.calculated_mass IS 'The theoretical mass of the peptide.';
COMMENT ON COLUMN public.peptide.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


CREATE UNIQUE INDEX public.peptide_sequence_ptm_idx
 ON public.peptide
 ( sequence, ptm_string );

CREATE INDEX public.peptide_mass_idx
 ON public.peptide
 ( calculated_mass );

CREATE TABLE public.peptide_ptm_insert_status (
                peptide_id BIGINT NOT NULL,
                is_ok BOOLEAN NOT NULL,
                CONSTRAINT peptide_ptm_insert_status_pk PRIMARY KEY (peptide_id)
);
COMMENT ON TABLE public.peptide_ptm_insert_status IS 'Used to specify if the peptide_ptm records corresponding to a given peptide have been correctly inserted.  Modified peptides without link to peptide_ptm must be considered as boggus and should be manually removed from the database. The discussed information is usefull to track inconsistent peptides records and thus maintain the database integrity.';
COMMENT ON COLUMN public.peptide_ptm_insert_status.is_ok IS 'A boolean value wich tells us if the peptide PTMs have been correctly stored in the database.';


CREATE TABLE public.ptm (
                id IDENTITY NOT NULL,
                unimod_id BIGINT,
                full_name VARCHAR(1000),
                short_name VARCHAR(100) NOT NULL,
                serialized_properties LONGVARCHAR,
                CONSTRAINT ptm_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.ptm IS 'Describes the names of the ptm definitions.
UNIQUE(full_name)
UNIQUE(short_name)';
COMMENT ON COLUMN public.ptm.unimod_id IS 'The unimod record_id.';
COMMENT ON COLUMN public.ptm.full_name IS 'A description of the PTM.
MUST BE UNIQUE.';
COMMENT ON COLUMN public.ptm.short_name IS 'Descriptive, one word name, suitable for use in software applications.
This name must not include the specificity. For example, Carboxymethyl is the short name, not Carboxymethyl-Cys or Carboxymethyl (C).
MUST BE UNIQUE.';
COMMENT ON COLUMN public.ptm.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


CREATE UNIQUE INDEX public.ptm_full_name_idx
 ON public.ptm
 ( full_name );

CREATE UNIQUE INDEX public.ptm_short_name_idx
 ON public.ptm
 ( short_name );

CREATE TABLE public.ptm_specificity (
                id IDENTITY NOT NULL,
                location VARCHAR(14) NOT NULL,
                residue CHAR(1),
                serialized_properties LONGVARCHAR,
                ptm_id BIGINT NOT NULL,
                classification_id BIGINT NOT NULL,
                CONSTRAINT ptm_specificity_pk PRIMARY KEY (id)
);
COMMENT ON TABLE public.ptm_specificity IS 'Describes the specificities of the ptm definitions.';
COMMENT ON COLUMN public.ptm_specificity.location IS 'The location of the PTM relative to a peptide/protein sequence.
Allowed values are: Anywhere, Any N-term, Any C-term, Protein N-term, Protein C-term.
Choose "Anywhere" if the modification applies to a residue independent of its position, (e.g. oxidation of methionine). Choose "Any N-term" or "Any C-term" if the modification applies to a residue only when it is at a peptide terminus, (e.g. conversion of methionine to homoserine). Choose "Protein N-term" or "Protein C-term" if the modification only applies to the original terminus of the intact protein, not new peptide termini created by digestion, (e.g. post-translational acetylation of the protein amino terminus).';
COMMENT ON COLUMN public.ptm_specificity.residue IS 'The symbol of the specific residue for this PTM (Unimod "site" xml attribute).
Null if Unimod "site" xml attribute is "C-term" or "N-term".';
COMMENT ON COLUMN public.ptm_specificity.serialized_properties IS 'A JSON string which stores optional properties (see corresponding JSON schema for more details).';


CREATE UNIQUE INDEX public.ptm_specificity_idx
 ON public.ptm_specificity
 ( location, residue, ptm_id );

CREATE TABLE public.ptm_evidence (
                id IDENTITY NOT NULL,
                type VARCHAR(14) NOT NULL,
                is_required BOOLEAN NOT NULL,
                composition VARCHAR(50) NOT NULL,
                mono_mass DOUBLE NOT NULL,
                average_mass DOUBLE NOT NULL,
                serialized_properties LONGVARCHAR,
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


CREATE TABLE public.peptide_ptm (
                id IDENTITY NOT NULL,
                seq_position INTEGER NOT NULL,
                mono_mass DOUBLE NOT NULL,
                average_mass DOUBLE NOT NULL,
                serialized_properties LONGVARCHAR,
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


CREATE INDEX public.peptide_ptm_peptide_idx
 ON public.peptide_ptm
 ( peptide_id );

CREATE TABLE public.admin_infos (
                model_version VARCHAR(1000) NOT NULL,
                db_creation_date TIMESTAMP,
                model_update_date TIMESTAMP,
                CONSTRAINT admin_infos_pk PRIMARY KEY (model_version)
);
COMMENT ON TABLE public.admin_infos IS 'Give information about the current database model.';
COMMENT ON COLUMN public.admin_infos.model_version IS 'The version of the database model.';
COMMENT ON COLUMN public.admin_infos.db_creation_date IS 'The date the database was created.';
COMMENT ON COLUMN public.admin_infos.model_update_date IS 'The date the database model was last updated.';


/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE public.ptm_specificity ADD CONSTRAINT ptm_classification_ptm_specificity_fk
FOREIGN KEY (classification_id)
REFERENCES public.ptm_classification (id)
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE public.peptide_ptm ADD CONSTRAINT atom_label_peptide_ptm_fk
FOREIGN KEY (atom_label_id)
REFERENCES public.atom_label (id)
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE public.peptide ADD CONSTRAINT atom_label_peptide_fk
FOREIGN KEY (atom_label_id)
REFERENCES public.atom_label (id)
ON UPDATE NO ACTION;

ALTER TABLE public.peptide_ptm ADD CONSTRAINT peptide_peptide_ptm_fk
FOREIGN KEY (peptide_id)
REFERENCES public.peptide (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.peptide_ptm_insert_status ADD CONSTRAINT peptide_peptide_ptm_insert_status_fk
FOREIGN KEY (peptide_id)
REFERENCES public.peptide (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.ptm_evidence ADD CONSTRAINT ptm_ptm_ion_fk
FOREIGN KEY (ptm_id)
REFERENCES public.ptm (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

ALTER TABLE public.ptm_specificity ADD CONSTRAINT ptm_ptm_specificity_fk
FOREIGN KEY (ptm_id)
REFERENCES public.ptm (id)
ON DELETE CASCADE
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE public.peptide_ptm ADD CONSTRAINT ptm_specificity_peptide_ptm_fk
FOREIGN KEY (ptm_specificity_id)
REFERENCES public.ptm_specificity (id)
ON UPDATE NO ACTION;

/*
Warning: H2 Database does not support this relationship's delete action (RESTRICT).
*/
ALTER TABLE public.ptm_evidence ADD CONSTRAINT ptm_specificity_ptm_evidence_fk
FOREIGN KEY (specificity_id)
REFERENCES public.ptm_specificity (id)
ON UPDATE NO ACTION;
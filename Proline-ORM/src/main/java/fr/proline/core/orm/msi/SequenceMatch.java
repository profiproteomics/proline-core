package fr.proline.core.orm.msi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;

/**
 * The persistent class for the sequence_match database table.
 * 
 */
@Entity
@Table(name = "sequence_match")
public class SequenceMatch implements Serializable {

    private static final long serialVersionUID = 1L;

    @EmbeddedId
    private SequenceMatchPK id;

    @Column(name = "best_peptide_match_id")
    private long bestPeptideMatchId;

    @Column(name = "is_decoy")
    private boolean isDecoy;

    @Column(name = "residue_after")
    private String residueAfter;

    @Column(name = "residue_before")
    private String residueBefore;

    @Column(name = "result_set_id")
    private long resultSetId;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    public SequenceMatch() {
    }

    public SequenceMatchPK getId() {
	return this.id;
    }

    public void setId(SequenceMatchPK id) {
	this.id = id;
    }

    public long getBestPeptideMatchId() {
	return bestPeptideMatchId;
    }

    public void setBestPeptideMatchId(final long pBestPeptideMatchId) {
	bestPeptideMatchId = pBestPeptideMatchId;
    }

    public boolean getIsDecoy() {
	return isDecoy;
    }

    public void setIsDecoy(final boolean pIsDecoy) {
	isDecoy = pIsDecoy;
    }

    public String getResidueAfter() {
	return this.residueAfter;
    }

    public void setResidueAfter(String residueAfter) {
	this.residueAfter = residueAfter;
    }

    public String getResidueBefore() {
	return this.residueBefore;
    }

    public void setResidueBefore(String residueBefore) {
	this.residueBefore = residueBefore;
    }

    public long getResultSetId() {
	return resultSetId;
    }

    public void setResultSetId(final long pResultSetId) {
	resultSetId = pResultSetId;
    }

    public String getSerializedProperties() {
	return this.serializedProperties;
    }

    public void setSerializedProperties(String serializedProperties) {
	this.serializedProperties = serializedProperties;
    }

}

package fr.proline.core.orm.msi;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the sequence_match database table.
 * 
 */
@Entity
@Table(name="sequence_match")
public class SequenceMatch implements Serializable {
	private static final long serialVersionUID = 1L;

	@EmbeddedId
	private SequenceMatchPK id;

	@Column(name="best_peptide_match_id")
	private Integer bestPeptideMatchId;

	@Column(name="is_decoy")
	private Boolean isDecoy;

	@Column(name="residue_after")
	private String residueAfter;

	@Column(name="residue_before")
	private String residueBefore;

	@Column(name="result_set_id")
	private Integer resultSetId;

	@Column(name="serialized_properties")
	private String serializedProperties;

    public SequenceMatch() {
    }

	public SequenceMatchPK getId() {
		return this.id;
	}

	public void setId(SequenceMatchPK id) {
		this.id = id;
	}
	
	public Integer getBestPeptideMatchId() {
		return this.bestPeptideMatchId;
	}

	public void setBestPeptideMatchId(Integer bestPeptideMatchId) {
		this.bestPeptideMatchId = bestPeptideMatchId;
	}

	public Boolean getIsDecoy() {
		return this.isDecoy;
	}

	public void setIsDecoy(Boolean isDecoy) {
		this.isDecoy = isDecoy;
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

	public Integer getResultSetId() {
		return this.resultSetId;
	}

	public void setResultSetId(Integer resultSetId) {
		this.resultSetId = resultSetId;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	
}
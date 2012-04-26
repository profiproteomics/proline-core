package fr.proline.core.orm.msi;

import java.io.Serializable;
import javax.persistence.*;

/**
 * The primary key class for the sequence_match database table.
 * 
 */
@Embeddable
public class SequenceMatchPK implements Serializable {
	//default serial version id, required for serializable classes.
	private static final long serialVersionUID = 1L;

	@Column(name="protein_match_id")
	private Integer proteinMatchId;

	@Column(name="peptide_id")
	private Integer peptideId;

	private Integer start;

	private Integer stop;

    public SequenceMatchPK() {
    }
	public Integer getProteinMatchId() {
		return this.proteinMatchId;
	}
	public void setProteinMatchId(Integer proteinMatchId) {
		this.proteinMatchId = proteinMatchId;
	}
	public Integer getPeptideId() {
		return this.peptideId;
	}
	public void setPeptideId(Integer peptideId) {
		this.peptideId = peptideId;
	}
	public Integer getStart() {
		return this.start;
	}
	public void setStart(Integer start) {
		this.start = start;
	}
	public Integer getStop() {
		return this.stop;
	}
	public void setStop(Integer stop) {
		this.stop = stop;
	}

	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof SequenceMatchPK)) {
			return false;
		}
		SequenceMatchPK castOther = (SequenceMatchPK)other;
		return 
			this.proteinMatchId.equals(castOther.proteinMatchId)
			&& this.peptideId.equals(castOther.peptideId)
			&& this.start.equals(castOther.start)
			&& this.stop.equals(castOther.stop);

    }
    
	public int hashCode() {
		final int prime = 31;
		int hash = 17;
		hash = hash * prime + this.proteinMatchId.hashCode();
		hash = hash * prime + this.peptideId.hashCode();
		hash = hash * prime + this.start.hashCode();
		hash = hash * prime + this.stop.hashCode();
		
		return hash;
    }
}
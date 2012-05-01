package fr.proline.core.orm.pdi;

import java.io.Serializable;
import javax.persistence.*;

/**
 * The primary key class for the bio_sequence_relation database table.
 * 
 */
@Embeddable
public class BioSequenceRelationPK implements Serializable {
	//default serial version id, required for serializable classes.
	private static final long serialVersionUID = 1L;

	@Column(name="na_sequence_id")
	private Integer naSequenceId;

	@Column(name="aa_sequence_id")
	private Integer aaSequenceId;

    public BioSequenceRelationPK() {
    }
	public Integer getNaSequenceId() {
		return this.naSequenceId;
	}
	public void setNaSequenceId(Integer naSequenceId) {
		this.naSequenceId = naSequenceId;
	}
	public Integer getAaSequenceId() {
		return this.aaSequenceId;
	}
	public void setAaSequenceId(Integer aaSequenceId) {
		this.aaSequenceId = aaSequenceId;
	}

	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof BioSequenceRelationPK)) {
			return false;
		}
		BioSequenceRelationPK castOther = (BioSequenceRelationPK)other;
		return 
			this.naSequenceId.equals(castOther.naSequenceId)
			&& this.aaSequenceId.equals(castOther.aaSequenceId);

    }
    
	public int hashCode() {
		final int prime = 31;
		int hash = 17;
		hash = hash * prime + this.naSequenceId.hashCode();
		hash = hash * prime + this.aaSequenceId.hashCode();
		
		return hash;
    }
}
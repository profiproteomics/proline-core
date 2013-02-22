package fr.proline.core.orm.msi;

import java.io.Serializable;
import javax.persistence.*;

/**
 * The primary key class for the peptide_set_relation database table.
 * 
 */
@Embeddable
public class PeptideSetRelationPK implements Serializable {
	//default serial version id, required for serializable classes.
	private static final long serialVersionUID = 1L;

	@Column(name="peptide_overset_id")
	private Integer peptideOversetId;

	@Column(name="peptide_subset_id")
	private Integer peptideSubsetId;

    public PeptideSetRelationPK() {
    }
	public Integer getPeptideOversetId() {
		return this.peptideOversetId;
	}
	public void setPeptideOversetId(Integer peptideOversetId) {
		this.peptideOversetId = peptideOversetId;
	}
	public Integer getPeptideSubsetId() {
		return this.peptideSubsetId;
	}
	public void setPeptideSubsetId(Integer peptideSubsetId) {
		this.peptideSubsetId = peptideSubsetId;
	}

	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof PeptideSetRelationPK)) {
			return false;
		}
		PeptideSetRelationPK castOther = (PeptideSetRelationPK)other;
		return 
			this.peptideOversetId.equals(castOther.peptideOversetId)
			&& this.peptideSubsetId.equals(castOther.peptideSubsetId);

    }
    
	public int hashCode() {
		final int prime = 31;
		int hash = 17;
		hash = hash * prime + this.peptideOversetId.hashCode();
		hash = hash * prime + this.peptideSubsetId.hashCode();
		
		return hash;
    }
}
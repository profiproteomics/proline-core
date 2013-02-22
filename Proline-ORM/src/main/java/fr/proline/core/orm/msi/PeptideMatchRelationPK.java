package fr.proline.core.orm.msi;

import java.io.Serializable;
import javax.persistence.*;

/**
 * The primary key class for the peptide_match_relation database table.
 * 
 */
@Embeddable
public class PeptideMatchRelationPK implements Serializable {
	//default serial version id, required for serializable classes.
	private static final long serialVersionUID = 1L;

	@Column(name="parent_peptide_match_id")
	private Integer parentPeptideMatchId;

	@Column(name="child_peptide_match_id")
	private Integer childPeptideMatchId;

    public PeptideMatchRelationPK() {
    }
	public Integer getParentPeptideMatchId() {
		return this.parentPeptideMatchId;
	}
	public void setParentPeptideMatchId(Integer parentPeptideMatchId) {
		this.parentPeptideMatchId = parentPeptideMatchId;
	}
	public Integer getChildPeptideMatchId() {
		return this.childPeptideMatchId;
	}
	public void setChildPeptideMatchId(Integer childPeptideMatchId) {
		this.childPeptideMatchId = childPeptideMatchId;
	}

	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof PeptideMatchRelationPK)) {
			return false;
		}
		PeptideMatchRelationPK castOther = (PeptideMatchRelationPK)other;
		return 
			this.parentPeptideMatchId.equals(castOther.parentPeptideMatchId)
			&& this.childPeptideMatchId.equals(castOther.childPeptideMatchId);

    }
    
	public int hashCode() {
		final int prime = 31;
		int hash = 17;
		hash = hash * prime + this.parentPeptideMatchId.hashCode();
		hash = hash * prime + this.childPeptideMatchId.hashCode();
		
		return hash;
    }
}
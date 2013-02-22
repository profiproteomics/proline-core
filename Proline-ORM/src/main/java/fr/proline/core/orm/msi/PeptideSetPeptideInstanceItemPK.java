package fr.proline.core.orm.msi;

import java.io.Serializable;
import javax.persistence.*;

/**
 * The primary key class for the peptide_set_peptide_instance_item database table.
 * 
 */
@Embeddable
public class PeptideSetPeptideInstanceItemPK implements Serializable {
	//default serial version id, required for serializable classes.
	private static final long serialVersionUID = 1L;

	@Column(name="peptide_set_id")
	private Integer peptideSetId;

	@Column(name="peptide_instance_id")
	private Integer peptideInstanceId;

    public PeptideSetPeptideInstanceItemPK() {
    }
	public Integer getPeptideSetId() {
		return this.peptideSetId;
	}
	public void setPeptideSetId(Integer peptideSetId) {
		this.peptideSetId = peptideSetId;
	}
	public Integer getPeptideInstanceId() {
		return this.peptideInstanceId;
	}
	public void setPeptideInstanceId(Integer peptideInstanceId) {
		this.peptideInstanceId = peptideInstanceId;
	}

	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof PeptideSetPeptideInstanceItemPK)) {
			return false;
		}
		PeptideSetPeptideInstanceItemPK castOther = (PeptideSetPeptideInstanceItemPK)other;
		return 
			this.peptideSetId.equals(castOther.peptideSetId)
			&& this.peptideInstanceId.equals(castOther.peptideInstanceId);

    }
    
	public int hashCode() {
		final int prime = 31;
		int hash = 17;
		hash = hash * prime + this.peptideSetId.hashCode();
		hash = hash * prime + this.peptideInstanceId.hashCode();
		
		return hash;
    }
}
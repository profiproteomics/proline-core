package fr.proline.core.orm.msi;

import java.io.Serializable;
import javax.persistence.*;

/**
 * The primary key class for the peptide_set_protein_match_map database table.
 * 
 */
@Embeddable
public class PeptideSetProteinMatchMapPK implements Serializable {
	//default serial version id, required for serializable classes.
	private static final long serialVersionUID = 1L;

	@Column(name="peptide_set_id")
	private Integer peptideSetId;

	@Column(name="protein_match_id")
	private Integer proteinMatchId;

    public PeptideSetProteinMatchMapPK() {
    }
	public Integer getPeptideSetId() {
		return this.peptideSetId;
	}
	public void setPeptideSetId(Integer peptideSetId) {
		this.peptideSetId = peptideSetId;
	}
	public Integer getProteinMatchId() {
		return this.proteinMatchId;
	}
	public void setProteinMatchId(Integer proteinMatchId) {
		this.proteinMatchId = proteinMatchId;
	}

	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof PeptideSetProteinMatchMapPK)) {
			return false;
		}
		PeptideSetProteinMatchMapPK castOther = (PeptideSetProteinMatchMapPK)other;
		return 
			this.peptideSetId.equals(castOther.peptideSetId)
			&& this.proteinMatchId.equals(castOther.proteinMatchId);

    }
    
	public int hashCode() {
		final int prime = 31;
		int hash = 17;
		hash = hash * prime + this.peptideSetId.hashCode();
		hash = hash * prime + this.proteinMatchId.hashCode();
		
		return hash;
    }
}

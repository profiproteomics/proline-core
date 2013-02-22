package fr.proline.core.orm.msi;

import java.io.Serializable;
import javax.persistence.*;

/**
 * The primary key class for the protein_set_protein_match_item database table.
 * 
 */
@Embeddable
public class ProteinSetProteinMatchItemPK implements Serializable {
	//default serial version id, required for serializable classes.
	private static final long serialVersionUID = 1L;

	@Column(name="protein_set_id")
	private Integer proteinSetId;

	@Column(name="protein_match_id")
	private Integer proteinMatchId;

    public ProteinSetProteinMatchItemPK() {
    }
	public Integer getProteinSetId() {
		return this.proteinSetId;
	}
	public void setProteinSetId(Integer proteinSetId) {
		this.proteinSetId = proteinSetId;
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
		if (!(other instanceof ProteinSetProteinMatchItemPK)) {
			return false;
		}
		ProteinSetProteinMatchItemPK castOther = (ProteinSetProteinMatchItemPK)other;
		return 
			this.proteinSetId.equals(castOther.proteinSetId)
			&& this.proteinMatchId.equals(castOther.proteinMatchId);

    }
    
	public int hashCode() {
		final int prime = 31;
		int hash = 17;
		hash = hash * prime + this.proteinSetId.hashCode();
		hash = hash * prime + this.proteinMatchId.hashCode();
		
		return hash;
    }
}
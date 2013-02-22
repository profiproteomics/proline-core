package fr.proline.core.orm.msi;

import java.io.Serializable;
import javax.persistence.*;

/**
 * The primary key class for the peptide_set_peptide_instance_item database table.
 * 
 */
@Embeddable
public class PeptideInstancePeptideMatchMapPK implements Serializable {
	//default serial version id, required for serializable classes.
	private static final long serialVersionUID = 1L;

  @Column(name="peptide_instance_id")
  private Integer peptideInstanceId;
  
	@Column(name="peptide_match_id")
	private Integer peptideMatchId;

    public PeptideInstancePeptideMatchMapPK() {
    }
	public Integer getPeptideInstanceId() {
		return this.peptideInstanceId;
	}
	public void setPeptideInstanceId(Integer peptideInstanceId) {
		this.peptideInstanceId = peptideInstanceId;
	}
  public Integer getPeptideMatchId() {
    return this.peptideMatchId;
  }
  public void setPeptideMatchId(Integer peptideSetId) {
    this.peptideMatchId = peptideSetId;
  }

	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof PeptideInstancePeptideMatchMapPK)) {
			return false;
		}
		PeptideInstancePeptideMatchMapPK castOther = (PeptideInstancePeptideMatchMapPK) other;
		return 
			this.peptideMatchId.equals(castOther.peptideMatchId)
			&& this.peptideInstanceId.equals(castOther.peptideInstanceId);

    }
    
	public int hashCode() {
		final int prime = 31;
		int hash = 17;
		hash = hash * prime + this.peptideInstanceId.hashCode();
		hash = hash * prime + this.peptideMatchId.hashCode();		
		
		return hash;
    }
}
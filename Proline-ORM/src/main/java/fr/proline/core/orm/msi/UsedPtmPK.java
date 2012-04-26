package fr.proline.core.orm.msi;

import java.io.Serializable;
import javax.persistence.*;

/**
 * The primary key class for the used_ptm database table.
 * 
 */
@Embeddable
public class UsedPtmPK implements Serializable {
	//default serial version id, required for serializable classes.
	private static final long serialVersionUID = 1L;

	@Column(name="search_settings_id")
	private Integer searchSettingsId;

	@Column(name="ptm_specificity_id")
	private Integer ptmSpecificityId;

    public UsedPtmPK() {
    }
	public Integer getSearchSettingsId() {
		return this.searchSettingsId;
	}
	public void setSearchSettingsId(Integer searchSettingsId) {
		this.searchSettingsId = searchSettingsId;
	}
	public Integer getPtmSpecificityId() {
		return this.ptmSpecificityId;
	}
	public void setPtmSpecificityId(Integer ptmSpecificityId) {
		this.ptmSpecificityId = ptmSpecificityId;
	}

	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof UsedPtmPK)) {
			return false;
		}
		UsedPtmPK castOther = (UsedPtmPK)other;
		return 
			this.searchSettingsId.equals(castOther.searchSettingsId)
			&& this.ptmSpecificityId.equals(castOther.ptmSpecificityId);

    }
    
	public int hashCode() {
		final int prime = 31;
		int hash = 17;
		hash = hash * prime + this.searchSettingsId.hashCode();
		hash = hash * prime + this.ptmSpecificityId.hashCode();
		
		return hash;
    }
}
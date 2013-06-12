package fr.proline.core.orm.msi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Embeddable;

/**
 * The primary key class for the used_ptm database table.
 * 
 */
@Embeddable
public class UsedPtmPK implements Serializable {

    // default serial version id, required for serializable classes.
    private static final long serialVersionUID = 1L;

    @Column(name = "search_settings_id")
    private long searchSettingsId;

    @Column(name = "ptm_specificity_id")
    private long ptmSpecificityId;

    public UsedPtmPK() {
    }

    public long getSearchSettingsId() {
	return searchSettingsId;
    }

    public void setSearchSettingsId(final long pSearchSettingsId) {
	searchSettingsId = pSearchSettingsId;
    }

    public long getPtmSpecificityId() {
	return ptmSpecificityId;
    }

    public void setPtmSpecificityId(final long pPtmSpecificityId) {
	ptmSpecificityId = pPtmSpecificityId;
    }

    @Override
    public boolean equals(final Object obj) {
	boolean result = false;

	if (obj == this) {
	    result = true;
	} else if (obj instanceof UsedPtmPK) {
	    final UsedPtmPK otherPK = (UsedPtmPK) obj;

	    result = ((getSearchSettingsId() == otherPK.getSearchSettingsId()) && (getPtmSpecificityId() == otherPK
		    .getPtmSpecificityId()));
	}

	return result;
    }

    @Override
    public int hashCode() {
	return (Long.valueOf(getSearchSettingsId()).hashCode() ^ Long.valueOf(getPtmSpecificityId())
		.hashCode());
    }

}

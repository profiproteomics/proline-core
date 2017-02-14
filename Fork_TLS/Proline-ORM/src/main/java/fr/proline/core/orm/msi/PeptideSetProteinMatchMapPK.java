package fr.proline.core.orm.msi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Embeddable;

/**
 * The primary key class for the peptide_set_protein_match_map database table.
 * 
 */
@Embeddable
public class PeptideSetProteinMatchMapPK implements Serializable {

    // default serial version id, required for serializable classes.
    private static final long serialVersionUID = 1L;

    @Column(name = "peptide_set_id")
    private long peptideSetId;

    @Column(name = "protein_match_id")
    private long proteinMatchId;

    public PeptideSetProteinMatchMapPK() {
    }

    public long getPeptideSetId() {
	return peptideSetId;
    }

    public void setPeptideSetId(final long pPeptideSetId) {
	peptideSetId = pPeptideSetId;
    }

    public long getProteinMatchId() {
	return proteinMatchId;
    }

    public void setProteinMatchId(final long pProteinMatchId) {
	proteinMatchId = pProteinMatchId;
    }

    @Override
    public boolean equals(final Object obj) {
	boolean result = false;

	if (obj == this) {
	    result = true;
	} else if (obj instanceof PeptideSetProteinMatchMapPK) {
	    final PeptideSetProteinMatchMapPK otherPK = (PeptideSetProteinMatchMapPK) obj;

	    result = ((getPeptideSetId() == otherPK.getPeptideSetId()) && (getProteinMatchId() == otherPK
		    .getProteinMatchId()));
	}

	return result;
    }

    @Override
    public int hashCode() {
	return (Long.valueOf(getPeptideSetId()).hashCode() ^ Long.valueOf(getProteinMatchId()).hashCode());
    }

}

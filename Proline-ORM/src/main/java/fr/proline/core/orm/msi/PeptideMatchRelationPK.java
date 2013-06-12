package fr.proline.core.orm.msi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Embeddable;

/**
 * The primary key class for the peptide_match_relation database table.
 * 
 */
@Embeddable
public class PeptideMatchRelationPK implements Serializable {

    // default serial version id, required for serializable classes.
    private static final long serialVersionUID = 1L;

    @Column(name = "parent_peptide_match_id")
    private long parentPeptideMatchId;

    @Column(name = "child_peptide_match_id")
    private long childPeptideMatchId;

    public PeptideMatchRelationPK() {
    }

    public long getParentPeptideMatchId() {
	return parentPeptideMatchId;
    }

    public void setParentPeptideMatchId(final long pParentPeptideMatchId) {
	parentPeptideMatchId = pParentPeptideMatchId;
    }

    public long getChildPeptideMatchId() {
	return childPeptideMatchId;
    }

    public void setChildPeptideMatchId(final long pChildPeptideMatchId) {
	childPeptideMatchId = pChildPeptideMatchId;
    }

    @Override
    public boolean equals(final Object obj) {
	boolean result = false;

	if (obj == this) {
	    result = true;
	} else if (obj instanceof PeptideMatchRelationPK) {
	    final PeptideMatchRelationPK otherPK = (PeptideMatchRelationPK) obj;

	    result = ((getParentPeptideMatchId() == otherPK.getParentPeptideMatchId()) && (getChildPeptideMatchId() == otherPK
		    .getChildPeptideMatchId()));
	}

	return result;
    }

    @Override
    public int hashCode() {
	return (Long.valueOf(getParentPeptideMatchId()).hashCode() ^ Long.valueOf(getChildPeptideMatchId())
		.hashCode());
    }

}

package fr.proline.core.orm.pdi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Embeddable;

/**
 * The primary key class for the bio_sequence_relation database table.
 * 
 */
@Embeddable
public class BioSequenceRelationPK implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "na_sequence_id")
    private long naSequenceId;

    @Column(name = "aa_sequence_id")
    private long aaSequenceId;

    public BioSequenceRelationPK() {
    }

    public long getNaSequenceId() {
	return naSequenceId;
    }

    public void setNaSequenceId(final long pNaSequenceId) {
	naSequenceId = pNaSequenceId;
    }

    public long getAaSequenceId() {
	return aaSequenceId;
    }

    public void setAaSequenceId(final long pAaSequenceId) {
	aaSequenceId = pAaSequenceId;
    }

    @Override
    public boolean equals(final Object obj) {
	boolean result = false;

	if (obj == this) {
	    result = true;
	} else if (obj instanceof BioSequenceRelationPK) {
	    final BioSequenceRelationPK otherPK = (BioSequenceRelationPK) obj;

	    result = ((getNaSequenceId() == otherPK.getNaSequenceId()) && (getAaSequenceId() == otherPK
		    .getAaSequenceId()));
	}

	return result;
    }

    @Override
    public int hashCode() {
	return (Long.valueOf(getNaSequenceId()).hashCode() ^ Long.valueOf(getAaSequenceId()).hashCode());
    }

}

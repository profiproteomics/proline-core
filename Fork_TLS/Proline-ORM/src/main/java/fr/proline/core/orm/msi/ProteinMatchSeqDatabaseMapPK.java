package fr.proline.core.orm.msi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Embeddable;

/**
 * The primary key class for the protein_match_seq_database_map database table.
 * 
 */
@Embeddable
public class ProteinMatchSeqDatabaseMapPK implements Serializable {
    // default serial version id, required for serializable classes.
    private static final long serialVersionUID = 1L;

    @Column(name = "protein_match_id")
    private long proteinMatchId;

    @Column(name = "seq_database_id")
    private long seqDatabaseId;

    public ProteinMatchSeqDatabaseMapPK() {
    }

    public long getProteinMatchId() {
	return proteinMatchId;
    }

    public void setProteinMatchId(final long pProteinMatchId) {
	proteinMatchId = pProteinMatchId;
    }

    public long getSeqDatabaseId() {
	return seqDatabaseId;
    }

    public void setSeqDatabaseId(final long pSeqDatabaseId) {
	seqDatabaseId = pSeqDatabaseId;
    }

    @Override
    public boolean equals(final Object obj) {
	boolean result = false;

	if (obj == this) {
	    result = true;
	} else if (obj instanceof ProteinMatchSeqDatabaseMapPK) {
	    final ProteinMatchSeqDatabaseMapPK otherPK = (ProteinMatchSeqDatabaseMapPK) obj;

	    result = ((getProteinMatchId() == otherPK.getProteinMatchId()) && (getSeqDatabaseId() == otherPK
		    .getSeqDatabaseId()));
	}

	return result;
    }

    @Override
    public int hashCode() {
	return (Long.valueOf(getProteinMatchId()).hashCode() ^ Long.valueOf(getSeqDatabaseId()).hashCode());
    }

}

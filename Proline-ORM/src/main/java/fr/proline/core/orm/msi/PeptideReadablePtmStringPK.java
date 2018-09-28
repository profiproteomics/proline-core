package fr.proline.core.orm.msi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Embeddable;

/**
 * The primary key class for the peptide_match_relation database table.
 * 
 */
@Embeddable
public class PeptideReadablePtmStringPK implements Serializable {

	// default serial version id, required for serializable classes.
	private static final long serialVersionUID = 1L;

	@Column(name = "peptide_id")
	private long peptideId;

	@Column(name = "result_set_id")
	private long resultSetId;

	public PeptideReadablePtmStringPK() {
	}

	public long getPeptideId() {
		return peptideId;
	}

	public void setPeptideId(final long pPeptideId) {
		peptideId = pPeptideId;
	}

	public long getResultSetId() {
		return resultSetId;
	}

	public void setResultSetId(final long pResultSetId) {
		resultSetId = pResultSetId;
	}

	@Override
	public boolean equals(final Object obj) {
		boolean result = false;

		if (obj == this) {
			result = true;
		} else if (obj instanceof PeptideReadablePtmStringPK) {
			final PeptideReadablePtmStringPK otherPK = (PeptideReadablePtmStringPK) obj;

			result = ((getPeptideId() == otherPK.getPeptideId()) && (getResultSetId() == otherPK
				.getResultSetId()));
		}

		return result;
	}

	@Override
	public int hashCode() {
		return (Long.valueOf(getPeptideId()).hashCode() ^ Long.valueOf(getResultSetId())
			.hashCode());
	}

}

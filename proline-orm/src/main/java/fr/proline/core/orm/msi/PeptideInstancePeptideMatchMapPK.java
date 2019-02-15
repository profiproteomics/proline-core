package fr.proline.core.orm.msi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Embeddable;

/**
 * The primary key class for the peptide_set_peptide_instance_item database table.
 * 
 */
@Embeddable
public class PeptideInstancePeptideMatchMapPK implements Serializable {

	// default serial version id, required for serializable classes.
	private static final long serialVersionUID = 1L;

	@Column(name = "peptide_instance_id")
	private long peptideInstanceId;

	@Column(name = "peptide_match_id")
	private long peptideMatchId;

	public PeptideInstancePeptideMatchMapPK() {
	}

	public long getPeptideInstanceId() {
		return peptideInstanceId;
	}

	public void setPeptideInstanceId(final long pPeptideInstanceId) {
		peptideInstanceId = pPeptideInstanceId;
	}

	public long getPeptideMatchId() {
		return peptideMatchId;
	}

	public void setPeptideMatchId(final long pPeptideSetId) {
		peptideMatchId = pPeptideSetId;
	}

	@Override
	public boolean equals(final Object obj) {
		boolean result = false;

		if (obj == this) {
			result = true;
		} else if (obj instanceof PeptideInstancePeptideMatchMapPK) {
			final PeptideInstancePeptideMatchMapPK otherPK = (PeptideInstancePeptideMatchMapPK) obj;

			result = ((getPeptideInstanceId() == otherPK.getPeptideInstanceId()) && (getPeptideMatchId() == getPeptideMatchId()));
		}

		return result;
	}

	@Override
	public int hashCode() {
		return (Long.valueOf(getPeptideInstanceId()).hashCode() ^ Long.valueOf(getPeptideMatchId())
			.hashCode());
	}

}

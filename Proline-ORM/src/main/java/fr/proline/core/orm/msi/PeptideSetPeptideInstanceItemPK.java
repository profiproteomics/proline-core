package fr.proline.core.orm.msi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Embeddable;

/**
 * The primary key class for the peptide_set_peptide_instance_item database table.
 * 
 */
@Embeddable
public class PeptideSetPeptideInstanceItemPK implements Serializable {
	// default serial version id, required for serializable classes.
	private static final long serialVersionUID = 1L;

	@Column(name = "peptide_set_id")
	private long peptideSetId;

	@Column(name = "peptide_instance_id")
	private long peptideInstanceId;

	public PeptideSetPeptideInstanceItemPK() {
	}

	public long getPeptideSetId() {
		return peptideSetId;
	}

	public void setPeptideSetId(final long pPeptideSetId) {
		peptideSetId = pPeptideSetId;
	}

	public long getPeptideInstanceId() {
		return peptideInstanceId;
	}

	public void setPeptideInstanceId(final long pPeptideInstanceId) {
		peptideInstanceId = pPeptideInstanceId;
	}

	@Override
	public boolean equals(final Object obj) {
		boolean result = false;

		if (obj == this) {
			result = true;
		} else if (obj instanceof PeptideSetPeptideInstanceItemPK) {
			final PeptideSetPeptideInstanceItemPK otherPK = (PeptideSetPeptideInstanceItemPK) obj;

			result = ((getPeptideSetId() == otherPK.getPeptideSetId()) && (getPeptideInstanceId() == otherPK
				.getPeptideInstanceId()));
		}

		return result;
	}

	@Override
	public int hashCode() {
		return (Long.valueOf(getPeptideSetId()).hashCode() ^ Long.valueOf(getPeptideInstanceId()).hashCode());
	}

}

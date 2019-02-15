package fr.proline.core.orm.msi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Embeddable;

/**
 * The primary key class for the peptide_set_relation database table.
 * 
 */
@Embeddable
public class PeptideSetRelationPK implements Serializable {

	// default serial version id, required for serializable classes.
	private static final long serialVersionUID = 1L;

	@Column(name = "peptide_overset_id")
	private long peptideOversetId;

	@Column(name = "peptide_subset_id")
	private long peptideSubsetId;

	public PeptideSetRelationPK() {
	}

	public long getPeptideOversetId() {
		return peptideOversetId;
	}

	public void setPeptideOversetId(final long pPeptideOversetId) {
		peptideOversetId = pPeptideOversetId;
	}

	public long getPeptideSubsetId() {
		return peptideSubsetId;
	}

	public void setPeptideSubsetId(final long pPeptideSubsetId) {
		peptideSubsetId = pPeptideSubsetId;
	}

	@Override
	public boolean equals(final Object obj) {
		boolean result = false;

		if (obj == this) {
			result = true;
		} else if (obj instanceof PeptideSetRelationPK) {
			final PeptideSetRelationPK otherPK = (PeptideSetRelationPK) obj;

			result = ((getPeptideOversetId() == otherPK.getPeptideOversetId()) && (getPeptideSubsetId() == otherPK
				.getPeptideSubsetId()));
		}

		return result;
	}

	@Override
	public int hashCode() {
		return (Long.valueOf(getPeptideOversetId()).hashCode() ^ Long.valueOf(getPeptideSubsetId())
			.hashCode());
	}

}

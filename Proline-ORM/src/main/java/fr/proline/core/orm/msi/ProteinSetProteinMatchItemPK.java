package fr.proline.core.orm.msi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Embeddable;

/**
 * The primary key class for the protein_set_protein_match_item database table.
 * 
 */
@Embeddable
public class ProteinSetProteinMatchItemPK implements Serializable {

	// default serial version id, required for serializable classes.
	private static final long serialVersionUID = 1L;

	@Column(name = "protein_set_id")
	private long proteinSetId;

	@Column(name = "protein_match_id")
	private long proteinMatchId;

	public ProteinSetProteinMatchItemPK() {
	}

	public long getProteinSetId() {
		return proteinSetId;
	}

	public void setProteinSetId(final long pProteinSetId) {
		proteinSetId = pProteinSetId;
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
		} else if (obj instanceof ProteinSetProteinMatchItemPK) {
			final ProteinSetProteinMatchItemPK otherPK = (ProteinSetProteinMatchItemPK) obj;

			result = ((getProteinSetId() == otherPK.getProteinSetId()) && (getProteinMatchId() == otherPK
				.getProteinMatchId()));
		}

		return result;

	}

	public int hashCode() {
		return (Long.valueOf(getProteinSetId()).hashCode() ^ Long.valueOf(getProteinMatchId()).hashCode());
	}

}

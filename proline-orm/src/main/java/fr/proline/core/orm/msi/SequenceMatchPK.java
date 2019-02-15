package fr.proline.core.orm.msi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Embeddable;

/**
 * The primary key class for the sequence_match database table.
 * 
 */
@Embeddable
public class SequenceMatchPK implements Serializable {

	// default serial version id, required for serializable classes.
	private static final long serialVersionUID = 1L;

	@Column(name = "protein_match_id")
	private long proteinMatchId;

	@Column(name = "peptide_id")
	private long peptideId;

	private int start;

	private int stop;

	public SequenceMatchPK() {
	}

	public long getProteinMatchId() {
		return proteinMatchId;
	}

	public void setProteinMatchId(final long pProteinMatchId) {
		proteinMatchId = pProteinMatchId;
	}

	public long getPeptideId() {
		return peptideId;
	}

	public void setPeptideId(final long pPeptideId) {
		peptideId = pPeptideId;
	}

	public int getStart() {
		return start;
	}

	public void setStart(final int pStart) {
		start = pStart;
	}

	public int getStop() {
		return this.stop;
	}

	public void setStop(final int pStop) {
		stop = pStop;
	}

	@Override
	public boolean equals(final Object obj) {
		boolean result = false;

		if (obj == this) {
			result = true;
		} else if (obj instanceof SequenceMatchPK) {
			final SequenceMatchPK otherPK = (SequenceMatchPK) obj;

			result = ((getProteinMatchId() == otherPK.getProteinMatchId())
				&& (getPeptideId() == otherPK.getPeptideId()) && (getStart() == otherPK.getStart()) && (getStop() == otherPK
					.getStop()));
		}

		return result;

	}

	@Override
	public int hashCode() {
		return (Long.valueOf(getProteinMatchId()).hashCode() ^ Long.valueOf(getPeptideId()).hashCode()
			^ Integer.valueOf(getStart()).hashCode() ^ Integer.valueOf(getStop()).hashCode());
	}

}

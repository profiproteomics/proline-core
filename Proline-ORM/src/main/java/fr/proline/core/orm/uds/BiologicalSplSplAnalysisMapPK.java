package fr.proline.core.orm.uds;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Embeddable;

@Embeddable
public class BiologicalSplSplAnalysisMapPK implements Serializable {

	// default serial version id, required for serializable classes.
	private static final long serialVersionUID = 1L;

	@Column(name = "biological_sample_id")
	private long biologicalSampleId;

	@Column(name = "sample_analysis_id")
	private long sampleAnalysisId;

	@Override
	public boolean equals(final Object obj) {
		boolean result = false;

		if (obj == this) {
			result = true;
		} else if (obj instanceof BiologicalSplSplAnalysisMapPK) {
			final BiologicalSplSplAnalysisMapPK otherPK = (BiologicalSplSplAnalysisMapPK) obj;

			result = ((getBiologicalSampleId() == otherPK.getBiologicalSampleId()) &&
				(getSampleAnalysisId() == otherPK.getSampleAnalysisId()));
		}

		return result;

	}

	public int hashCode() {
		return (Long.valueOf(getBiologicalSampleId()).hashCode() ^
			Long.valueOf(getSampleAnalysisId()).hashCode());
	}

	public long getBiologicalSampleId() {
		return biologicalSampleId;
	}

	public void setBiologicalSampleId(long biologicalSampleId) {
		this.biologicalSampleId = biologicalSampleId;
	}

	public long getSampleAnalysisId() {
		return sampleAnalysisId;
	}

	public void setSampleAnalysisId(long sampleAnalysisId) {
		this.sampleAnalysisId = sampleAnalysisId;
	}
}

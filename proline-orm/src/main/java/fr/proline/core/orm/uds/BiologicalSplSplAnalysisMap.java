package fr.proline.core.orm.uds;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MapsId;
import javax.persistence.Table;

/**
 * The persistent class for the biological_sample_sample_analysis_map database
 * table.
 * 
 */
@Entity
@Table(name = "biological_sample_sample_analysis_map")
public class BiologicalSplSplAnalysisMap implements Serializable {
	private static final long serialVersionUID = 1L;

	@EmbeddedId
	private BiologicalSplSplAnalysisMapPK id;

	// bi-directional many-to-one association to Project
	@ManyToOne
	@JoinColumn(name = "biological_sample_id")
	@MapsId("biologicalSampleId")
	private BiologicalSample biologicalSample;

	// bi-directional many-to-one association to UserAccount
	@ManyToOne
	@JoinColumn(name = "sample_analysis_id")
	@MapsId("sampleAnalysisId")
	private SampleAnalysis sampleAnalysis;

	@Column(name = "sample_analysis_number")
	private int sampleAnalysisNumber;

	public BiologicalSplSplAnalysisMap() {
		super();
	}

	public BiologicalSplSplAnalysisMapPK getId() {
		return id;
	}

	public void setId(BiologicalSplSplAnalysisMapPK id) {
		this.id = id;
	}

	public BiologicalSample getBiologicalSample() {
		return biologicalSample;
	}

	public void setBiologicalSample(BiologicalSample biologicalSample) {
		this.biologicalSample = biologicalSample;
	}

	public SampleAnalysis getSampleAnalysis() {
		return sampleAnalysis;
	}

	public void setSampleAnalysis(SampleAnalysis sampleAnalysis) {
		this.sampleAnalysis = sampleAnalysis;
	}

	public int getSampleAnalysisNumber() {
		return sampleAnalysisNumber;
	}

	public void setSampleAnalysisNumber(int sampleAnalysisNumber) {
		this.sampleAnalysisNumber = sampleAnalysisNumber;
	}

}

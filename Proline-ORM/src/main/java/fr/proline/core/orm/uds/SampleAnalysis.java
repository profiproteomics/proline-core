package fr.proline.core.orm.uds;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Table;

/**
 * The persistent class for the sample_analysis database table.
 * 
 */
@Entity
@Table(name = "sample_analysis")
public class SampleAnalysis implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	@Column(name = "serialized_properties")
	private String serializedProperties;

	// bi-directional many-to-one association to QuantChannel
	@OneToMany(mappedBy = "sampleAnalysis")
	@OrderBy("number")
	private List<QuantitationChannel> quantitationChannels;

	// bi-directional many-to-one association to BiologicalSplSplAnalysisMap
	@OneToMany(mappedBy = "sampleAnalysis")
	private Set<BiologicalSplSplAnalysisMap> biologicalSplSplAnalysisMap;

	// bi-directional many-to-one association to Dataset
	@ManyToOne
	@JoinColumn(name = "quantitation_id")
	private Dataset dataset;

	public SampleAnalysis() {
	}

	public long getId() {
		return id;
	}

	public void setId(final long pId) {
		id = pId;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public List<QuantitationChannel> getQuantitationChannels() {
		return quantitationChannels;
	}

	public void setQuantitationChannels(final List<QuantitationChannel> quantitationChannels) {
		this.quantitationChannels = quantitationChannels;
	}

	public Set<BiologicalSplSplAnalysisMap> getBiologicalSplSplAnalysisMap() {
		return biologicalSplSplAnalysisMap;
	}

	public void setBiologicalSplSplAnalysisMap(final Set<BiologicalSplSplAnalysisMap> biologicalSamplesMap) {
		this.biologicalSplSplAnalysisMap = biologicalSamplesMap;
	}

	public Dataset getDataset() {
		return this.dataset;
	}

	public void setDataset(Dataset dataset) {
		this.dataset = dataset;
	}

}

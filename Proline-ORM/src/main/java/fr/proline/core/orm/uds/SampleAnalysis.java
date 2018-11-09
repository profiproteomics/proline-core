package fr.proline.core.orm.uds;

import javax.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
	@OneToMany(mappedBy = "sampleAnalysis", cascade = CascadeType.PERSIST)
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

	protected void addBiologicalSample(BiologicalSplSplAnalysisMap item) {
		getBiologicalSplSplAnalysisMap().add(item);
	}

	private Set<BiologicalSplSplAnalysisMap> getBiologicalSplSplAnalysisMap() {
		if (biologicalSplSplAnalysisMap == null) {
			biologicalSplSplAnalysisMap = new HashSet<>();
		}
		return biologicalSplSplAnalysisMap;
	}

	public Dataset getDataset() {
		return this.dataset;
	}

	public void setDataset(Dataset dataset) {
		this.dataset = dataset;
	}

}

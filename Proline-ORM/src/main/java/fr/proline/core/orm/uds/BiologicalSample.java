package fr.proline.core.orm.uds;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
 * The persistent class for the biological_sample database table.
 * 
 */
@Entity
@Table(name = "biological_sample")
public class BiologicalSample implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	private String name;

	private int number;

	@Column(name = "serialized_properties")
	private String serializedProperties;

	// bi-directional many-to-one association to Dataset
	@ManyToOne
	@JoinColumn(name = "quantitation_id")
	private Dataset dataset;

	// bi-directional many-to-one association to QuantChannel
	@OneToMany(mappedBy = "biologicalSample")
	@OrderBy("number")
	private List<QuantitationChannel> quantitationChannels;

	// bi-directional many-to-one association to BiologicalSplSplAnalysisMap
	@OneToMany(mappedBy = "biologicalSample")
	@OrderBy("sampleAnalysisNumber")
	private List<BiologicalSplSplAnalysisMap> biologicalSplSplAnalysisMap;

	public BiologicalSample() {
	}

	public long getId() {
		return id;
	}

	public void setId(final long pId) {
		id = pId;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getNumber() {
		return number;
	}

	public void setNumber(final int pNumber) {
		number = pNumber;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public Dataset getDataset() {
		return this.dataset;
	}

	public void setDataset(Dataset dataset) {
		this.dataset = dataset;
	}

	public List<QuantitationChannel> getQuantitationChannels() {
		return quantitationChannels;
	}

	public void setQuantitationChannels(final List<QuantitationChannel> quantitationChannels) {
		this.quantitationChannels = quantitationChannels;
	}

	public BiologicalSplSplAnalysisMap addSampleAnalysis(SampleAnalysis sampleAnalysis) {
		BiologicalSplSplAnalysisMap item = new BiologicalSplSplAnalysisMap();

		BiologicalSplSplAnalysisMapPK key = new BiologicalSplSplAnalysisMapPK();
		key.setBiologicalSampleId(this.getId());
		key.setSampleAnalysisId(sampleAnalysis.getId());
		item.setId(key);
		item.setBiologicalSample(this);
		item.setSampleAnalysis(sampleAnalysis);
		item.setSampleAnalysisNumber(getBiologicalSplSplAnalysisMap().size()+1);

		sampleAnalysis.addBiologicalSample(item);
		return (getBiologicalSplSplAnalysisMap().add(item)) ? item : null;
	}

	public List<SampleAnalysis> getSampleAnalyses() {
		return getBiologicalSplSplAnalysisMap().stream().map(i -> i.getSampleAnalysis()).collect(Collectors.toList());
	}

	private List<BiologicalSplSplAnalysisMap> getBiologicalSplSplAnalysisMap() {
		if (biologicalSplSplAnalysisMap == null) {
			biologicalSplSplAnalysisMap = new ArrayList<>();
		}
		return biologicalSplSplAnalysisMap;
	}

}

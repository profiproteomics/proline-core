package fr.proline.core.orm.uds;

import java.io.Serializable;
import javax.persistence.*;

import java.util.Set;


/**
 * The persistent class for the biological_sample database table.
 * 
 */
@Entity
@Table(name="biological_sample")
public class BiologicalSample implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	private String name;

	private Integer number;

	@Column(name="serialized_properties")
	private String serializedProperties;

	//bi-directional many-to-one association to Dataset
	@ManyToOne
	@JoinColumn(name="quantitation_id")
	private Dataset dataset;

	//bi-directional many-to-one association to QuantChannel
	@OneToMany(mappedBy="biologicalSample")
	private Set<QuantitationChannel> quantitationChannels;

	//bi-directional many-to-many association to SampleAnalysis
	@ManyToMany(mappedBy="biologicalSamples")
	private Set<SampleAnalysis> sampleAnalysis;

    public BiologicalSample() {
    }

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Integer getNumber() {
		return this.number;
	}

	public void setNumber(Integer number) {
		this.number = number;
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
	
	public Set<QuantitationChannel> getQuantitationChannels() {
		return this.quantitationChannels;
	}

	public void setQuantitationChannels(Set<QuantitationChannel> quantitationChannels) {
		this.quantitationChannels = quantitationChannels;
	}
	
	public Set<SampleAnalysis> getSampleReplicates() {
		return this.sampleAnalysis;
	}

	public void setSampleReplicates(Set<SampleAnalysis> sampleReplicates) {
		this.sampleAnalysis = sampleReplicates;
	}
	
}
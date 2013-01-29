package fr.proline.core.orm.uds;

import java.io.Serializable;
import javax.persistence.*;

import java.util.Set;


/**
 * The persistent class for the sample_analysis database table.
 * 
 */
@Entity
@Table(name="sample_analysis")
public class SampleAnalysis implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	private Integer number;

	@Column(name="serialized_properties")
	private String serializedProperties;

	//bi-directional many-to-one association to QuantChannel
	@OneToMany(mappedBy="sampleAnalysis")
	private Set<QuantitationChannel> quantitationChannels;

	//bi-directional many-to-many association to BiologicalSample
	@ManyToMany
		@JoinTable(
			name="biological_sample_sample_analysis_map"
			, joinColumns={
				@JoinColumn(name="sample_analysis_id")
				}	
			, inverseJoinColumns={
				@JoinColumn(name="biological_sample_id")
				}		
			)
	private Set<BiologicalSample> biologicalSamples;


	//bi-directional many-to-one association to Dataset
    @ManyToOne
      @JoinColumn(name="quantitation_id")
	private Dataset dataset;

    public SampleAnalysis() {
    }

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
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

	public Set<QuantitationChannel> getQuantitationChannels() {
		return this.quantitationChannels;
	}

	public void setQuantitationChannels(Set<QuantitationChannel> quantitationChannels) {
		this.quantitationChannels = quantitationChannels;
	}
	
	public Set<BiologicalSample> getBiologicalSample() {
		return this.biologicalSamples;		
	}

	public void setBiologicalSample(Set<BiologicalSample> biologicalSamples) {
		this.biologicalSamples = biologicalSamples;
	}
	
	public Dataset getDataset() {
		return this.dataset;
	}

	public void setDataset(Dataset dataset) {
		this.dataset = dataset;
	}
	
}
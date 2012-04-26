package fr.proline.core.orm.uds;

import java.io.Serializable;
import javax.persistence.*;
import java.util.Set;


/**
 * The persistent class for the sample_analysis_replicate database table.
 * 
 */
@Entity
@Table(name="sample_analysis_replicate")
public class SampleAnalysisReplicate implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	private Integer number;

	@Column(name="serialized_properties")
	private String serializedProperties;

	//bi-directional many-to-one association to QuantChannel
	@OneToMany(mappedBy="sampleReplicate")
	private Set<QuantitationChannel> quantitationChannels;

	//bi-directional many-to-one association to BiologicalSample
    @ManyToOne
	@JoinColumn(name="biological_sample_id")
	private BiologicalSample biologicalSample;

	//bi-directional many-to-one association to Quantitation
    @ManyToOne
	private Quantitation quantitation;

    public SampleAnalysisReplicate() {
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
	
	public BiologicalSample getBiologicalSample() {
		return this.biologicalSample;
	}

	public void setBiologicalSample(BiologicalSample biologicalSample) {
		this.biologicalSample = biologicalSample;
	}
	
	public Quantitation getQuantitation() {
		return this.quantitation;
	}

	public void setQuantitation(Quantitation quantitation) {
		this.quantitation = quantitation;
	}
	
}
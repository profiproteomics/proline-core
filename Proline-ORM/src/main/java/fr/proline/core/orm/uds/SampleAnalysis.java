package fr.proline.core.orm.uds;

import java.io.Serializable;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
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

    private int number;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    // bi-directional many-to-one association to QuantChannel
    @OneToMany(mappedBy = "sampleAnalysis")
    @OrderBy("number")
    private List<QuantitationChannel> quantitationChannels;

    // bi-directional many-to-many association to BiologicalSample
    @ManyToMany
    @JoinTable(name = "biological_sample_sample_analysis_map", joinColumns = { @JoinColumn(name = "sample_analysis_id") }, inverseJoinColumns = { @JoinColumn(name = "biological_sample_id") })
    @OrderBy("number")
    private List<BiologicalSample> biologicalSamples;

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

    public List<QuantitationChannel> getQuantitationChannels() {
	return quantitationChannels;
    }

    public void setQuantitationChannels(final List<QuantitationChannel> quantitationChannels) {
	this.quantitationChannels = quantitationChannels;
    }

    public List<BiologicalSample> getBiologicalSample() {
	return biologicalSamples;
    }

    public void setBiologicalSample(final List<BiologicalSample> biologicalSamples) {
	this.biologicalSamples = biologicalSamples;
    }

    public Dataset getDataset() {
	return this.dataset;
    }

    public void setDataset(Dataset dataset) {
	this.dataset = dataset;
    }

}

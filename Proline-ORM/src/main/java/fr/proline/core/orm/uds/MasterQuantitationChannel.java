package fr.proline.core.orm.uds;

import java.io.Serializable;
import java.util.List;

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
 * The persistent class for the master_quant_channel database table.
 * 
 */
@Entity
@Table(name = "master_quant_channel")
public class MasterQuantitationChannel implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column(name = "lcms_map_set_id")
    private Long lcmsMapSetId;

    private String name;

    private int number;

    @Column(name = "quant_result_summary_id")
    private Long quantResultSummaryId;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    // bi-directional many-to-one association to QuantChannel
    @OneToMany(mappedBy = "masterQuantitationChannel")
    @OrderBy("number")
    private List<QuantitationChannel> quantitationChannels;

    // bi-directional many-to-one association to Dataset
    @ManyToOne
    @JoinColumn(name = "quantitation_id")
    private Dataset dataset;

    public MasterQuantitationChannel() {
    }

    public long getId() {
	return id;
    }

    public void setId(final long pId) {
	id = pId;
    }

    public Long getLcmsMapSetId() {
	return lcmsMapSetId;
    }

    public void setLcmsMapSetId(final Long pLcmsMapSetId) {
	lcmsMapSetId = pLcmsMapSetId;
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

    public Long getQuantResultSummaryId() {
	return quantResultSummaryId;
    }

    public void setQuantResultSummaryId(final Long pQuantResultSummaryId) {
	quantResultSummaryId = pQuantResultSummaryId;
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

    public Dataset getDataset() {
	return this.dataset;
    }

    public void setDataset(Dataset dataset) {
	this.dataset = dataset;
    }

}

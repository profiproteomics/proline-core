package fr.proline.core.orm.uds;

import java.io.Serializable;
import java.util.List;

import javax.persistence.CascadeType;
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

	private String name;

	private int number;

	@Column(name = "serialized_properties")
	private String serializedProperties;

	@Column(name = "lcms_map_set_id")
	private Long lcmsMapSetId;

	@Column(name = "ident_result_summary_id")
	private Long identResultSummaryId;

	@Column(name = "quant_result_summary_id")
	private Long quantResultSummaryId;

	// bi-directional many-to-one association to ident Dataset
	@ManyToOne
	@JoinColumn(name = "ident_data_set_id")
	private Dataset identDataset;

	// bi-directional many-to-one association to quant Dataset
	@ManyToOne
	@JoinColumn(name = "quantitation_id")
	private Dataset quantDataset;

	// bi-directional many-to-one association to QuantChannel
	@OneToMany(mappedBy = "masterQuantitationChannel", cascade = CascadeType.ALL)
	@OrderBy("number")
	private List<QuantitationChannel> quantitationChannels;

	public MasterQuantitationChannel() {
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

	public Long getLcmsMapSetId() {
		return lcmsMapSetId;
	}

	public void setLcmsMapSetId(final Long pLcmsMapSetId) {
		lcmsMapSetId = pLcmsMapSetId;
	}

	public Long getIdentResultSummaryId() {
		return identResultSummaryId;
	}

	public void setIdentResultSummaryId(Long identResultSummaryId) {
		this.identResultSummaryId = identResultSummaryId;
	}

	public Long getQuantResultSummaryId() {
		return quantResultSummaryId;
	}

	public void setQuantResultSummaryId(final Long pQuantResultSummaryId) {
		quantResultSummaryId = pQuantResultSummaryId;
	}

	public Dataset getIdentDataset() {
		return identDataset;
	}

	public void setIdentDataset(Dataset identDataset) {
		this.identDataset = identDataset;
	}

	// TODO: rename to getQuantDataset
	public Dataset getDataset() {
		return this.quantDataset;
	}

	// TODO: rename to setQuantDataset
	public void setDataset(Dataset dataset) {
		this.quantDataset = dataset;
	}

	public List<QuantitationChannel> getQuantitationChannels() {
		return quantitationChannels;
	}

	public void setQuantitationChannels(final List<QuantitationChannel> quantitationChannels) {
		this.quantitationChannels = quantitationChannels;
	}

}

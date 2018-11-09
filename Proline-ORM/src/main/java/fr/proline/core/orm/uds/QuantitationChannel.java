package fr.proline.core.orm.uds;

import java.io.Serializable;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

/**
 * The persistent class for the quant_channel database table.
 * 
 */
@Entity
@Table(name = "quant_channel")
public class QuantitationChannel implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	@Column(name = "context_key")
	private String contextKey;

	@Column(name = "ident_result_summary_id")
	private long identResultSummaryId;

	@Column(name = "lcms_map_id")
	private Long lcmsMapId;

	@Column(name = "number")
	private int number;

	// uni-directional many-to-one association to Run
	@ManyToOne
	@JoinColumn(name = "run_id")
	private Run run;

	private String name;

	@Column(name = "serialized_properties")
	private String serializedProperties;

	// bi-directional many-to-one association to BiologicalSample
	@ManyToOne
	@JoinColumn(name = "biological_sample_id")
	private BiologicalSample biologicalSample;

	// uni-directional many-to-one association to QuantLabel
	@ManyToOne
	@JoinColumn(name = "quant_label_id")
	private QuantitationLabel label;

	// bi-directional many-to-one association to Dataset
	@ManyToOne
	@JoinColumn(name = "quantitation_id")
	private Dataset dataset;

	// bi-directional many-to-one association to MasterQuantitationChannel
	@ManyToOne
	@JoinColumn(name = "master_quant_channel_id")
	private MasterQuantitationChannel masterQuantitationChannel;

	// bi-directional many-to-one association to SampleAnalysis
	@ManyToOne(cascade = { CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH })
	@JoinColumn(name = "sample_analysis_id")
	private SampleAnalysis sampleAnalysis;

	public QuantitationChannel() {
	}

	public long getId() {
		return id;
	}

	public void setId(final long pId) {
		id = pId;
	}

	public String getContextKey() {
		return this.contextKey;
	}

	public void setContextKey(String contextKey) {
		this.contextKey = contextKey;
	}

	public long getIdentResultSummaryId() {
		return identResultSummaryId;
	}

	public void setIdentResultSummaryId(final long pIdentResultSummaryId) {
		identResultSummaryId = pIdentResultSummaryId;
	}

	public int getNumber() {
		return number;
	}

	public void setNumber(final int pNumber) {
		number = pNumber;
	}

	public Long getLcmsMapId() {
		return lcmsMapId;
	}

	public void setLcmsMapId(final Long pLcmsMapId) {
		lcmsMapId = pLcmsMapId;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Run getRun() {
		return run;
	}

	public void setRun(Run run) {
		this.run = run;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public BiologicalSample getBiologicalSample() {
		return this.biologicalSample;
	}

	public void setBiologicalSample(BiologicalSample biologicalSample) {
		this.biologicalSample = biologicalSample;
	}

	public QuantitationLabel getQuantitationLabel() {
		return this.label;
	}

	public void setLabel(QuantitationLabel label) {
		this.label = label;
	}

	// TODO: return a true QuantitationDataset object when it is implemented
	public Dataset getQuantitationDataset() {
		return this.dataset;
	}

	public void setQuantitationDataset(Dataset dataset) {
		this.dataset = dataset;
	}

	public MasterQuantitationChannel getMasterQuantitationChannel() {
		return this.masterQuantitationChannel;
	}

	public void setMasterQuantitationChannel(MasterQuantitationChannel masterQuantitationChannel) {
		this.masterQuantitationChannel = masterQuantitationChannel;
	}

	public SampleAnalysis getSampleReplicate() {
		return this.sampleAnalysis;
	}

	public void setSampleReplicate(SampleAnalysis sampleReplicate) {
		this.sampleAnalysis = sampleReplicate;
	}

}

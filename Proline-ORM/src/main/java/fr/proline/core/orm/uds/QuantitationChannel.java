package fr.proline.core.orm.uds;

import java.io.Serializable;

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
@Table(name="quant_channel")
public class QuantitationChannel implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	@Column(name="context_key")
	private String contextKey;

	@Column(name="ident_result_summary_id")
	private Integer identResultSummaryId;

	@Column(name="lcms_map_id")
	private Integer lcmsMapId;

	@Column(name="number")
	private Integer number;
	
	 // uni-directional many-to-one association to Run
	@ManyToOne
	@JoinColumn(name = "run_id")
	private Run run;
	
	private String name;

	@Column(name="quant_result_summary_id")
	private Integer quantResultSummaryId;

	
	@Column(name="serialized_properties")
	private String serializedProperties;

	//bi-directional many-to-one association to BiologicalSample
    @ManyToOne
	@JoinColumn(name="biological_sample_id")
	private BiologicalSample biologicalSample;

	//uni-directional many-to-one association to QuantLabel
    @ManyToOne
	@JoinColumn(name="quant_label_id")
	private QuantitationLabel label;

	//bi-directional many-to-one association to Dataset
    @ManyToOne
    	@JoinColumn(name="dataset_id")
	private Dataset dataset;

	//bi-directional many-to-one association to MasterQuantitationChannel
    @ManyToOne
	@JoinColumn(name="master_quant_channel_id")
	private MasterQuantitationChannel masterQuantitationChannel;

	//bi-directional many-to-one association to SampleAnalysis
    @ManyToOne
	@JoinColumn(name="sample_analysis_id")
	private SampleAnalysis sampleAnalysis;

    public QuantitationChannel() {
    }

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getContextKey() {
		return this.contextKey;
	}

	public void setContextKey(String contextKey) {
		this.contextKey = contextKey;
	}

	public Integer getNumber() {
		return this.number;
	}

	public void setNumber(Integer number) {
		this.number = number;
	}
	
	public Integer getIdentResultSummaryId() {
		return this.identResultSummaryId;
	}

	public void setIdentResultSummaryId(Integer identResultSummaryId) {
		this.identResultSummaryId = identResultSummaryId;
	}

	public Integer getLcmsMapId() {
		return this.lcmsMapId;
	}

	public void setLcmsMapId(Integer lcmsMapId) {
		this.lcmsMapId = lcmsMapId;
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
	

	public Integer getQuantResultSummaryId() {
		return this.quantResultSummaryId;
	}

	public void setQuantResultSummaryId(Integer quantResultSummaryId) {
		this.quantResultSummaryId = quantResultSummaryId;
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
	
	public QuantitationLabel getLabel() {
		return this.label;
	}

	public void setLabel(QuantitationLabel label) {
		this.label = label;
	}
	
	public Dataset getDataset() {
		return this.dataset;
	}

	public void setDataset(Dataset dataset) {
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
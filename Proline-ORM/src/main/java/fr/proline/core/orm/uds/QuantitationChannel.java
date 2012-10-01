package fr.proline.core.orm.uds;

import java.io.Serializable;
import javax.persistence.*;


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

	//bi-directional many-to-one association to Quantitation
    @ManyToOne
	private Quantitation quantitation;

	//bi-directional many-to-one association to QuantitationFraction
    @ManyToOne
	@JoinColumn(name="quantitation_fraction_id")
	private QuantitationFraction quantitationFraction;

	//bi-directional many-to-one association to SampleAnalysisReplicate
    @ManyToOne
	@JoinColumn(name="sample_analysis_replicate_id")
	private SampleAnalysisReplicate sampleReplicate;

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
	
	public Quantitation getQuantitation() {
		return this.quantitation;
	}

	public void setQuantitation(Quantitation quantitation) {
		this.quantitation = quantitation;
	}
	
	public QuantitationFraction getQuantitationFraction() {
		return this.quantitationFraction;
	}

	public void setQuantitationFraction(QuantitationFraction quantitationFraction) {
		this.quantitationFraction = quantitationFraction;
	}
	
	public SampleAnalysisReplicate getSampleReplicate() {
		return this.sampleReplicate;
	}

	public void setSampleReplicate(SampleAnalysisReplicate sampleReplicate) {
		this.sampleReplicate = sampleReplicate;
	}
	
}
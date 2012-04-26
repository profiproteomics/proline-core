package fr.proline.core.orm.uds;

import java.io.Serializable;
import javax.persistence.*;

import java.sql.Timestamp;
import java.util.Date;
import java.util.Set;


/**
 * The persistent class for the quantitation database table.
 * 
 */
@Entity
public class Quantitation implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	@Column(name="creation_timestamp")
	private Timestamp creationTimestamp  = new Timestamp(new Date().getTime());

	private String description;

	@Column(name="fraction_count")
	private Integer fractionCount;

	@Column(name="fractionation_type")
	private String fractionationType;

	private String keywords;

	@Column(name="modification_log")
	private String modificationLog;

	private String name;

	private Integer number;

	@ManyToOne
	@JoinColumn(name="project_id")
	private Project project;

	@Column(name="serialized_properties")
	private String serializedProperties;

	//bi-directional many-to-one association to BiologicalSample
	@OneToMany(mappedBy="quantitation")
	private Set<BiologicalSample> biologicalSamples;

	//bi-directional many-to-one association to GroupSetup
	@OneToMany(mappedBy="quantitation")
	private Set<GroupSetup> groupSetups;

	//bi-directional many-to-one association to QuantChannel
	@OneToMany(mappedBy="quantitation")
	private Set<QuantitationChannel> quantitationChannels;

	//uni-directional many-to-one association to QuantMethod
    @ManyToOne
	@JoinColumn(name="quant_method_id")
	private QuantitationMethod method;

	//bi-directional many-to-one association to QuantitationFraction
	@OneToMany(mappedBy="quantitation")
	private Set<QuantitationFraction> quantitationFractions;

	//bi-directional many-to-one association to SampleAnalysisReplicate
	@OneToMany(mappedBy="quantitation")
	private Set<SampleAnalysisReplicate> sampleReplicates;

    protected Quantitation() {
    }

    public Quantitation(Project project) {
    	this.project = project;
    }

    
	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Timestamp getCreationTimestamp() {
		return this.creationTimestamp;
	}

	public void setCreationTimestamp(Timestamp creationTimestamp) {
		this.creationTimestamp = creationTimestamp;
	}

	public String getDescription() {
		return this.description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Integer getFractionCount() {
		return this.fractionCount;
	}

	public void setFractionCount(Integer fractionCount) {
		this.fractionCount = fractionCount;
	}

	public String getFractionationType() {
		return this.fractionationType;
	}

	public void setFractionationType(String fractionationType) {
		this.fractionationType = fractionationType;
	}

	public String getKeywords() {
		return this.keywords;
	}

	public void setKeywords(String keywords) {
		this.keywords = keywords;
	}

	public String getModificationLog() {
		return this.modificationLog;
	}

	public void setModificationLog(String modificationLog) {
		this.modificationLog = modificationLog;
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

	public Project getProject() {
		return this.project;
	}

	public void setProject(Project project) {
		this.project = project;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public Set<BiologicalSample> getBiologicalSamples() {
		return this.biologicalSamples;
	}

	public void setBiologicalSamples(Set<BiologicalSample> biologicalSamples) {
		this.biologicalSamples = biologicalSamples;
	}
	
	public Set<GroupSetup> getGroupSetups() {
		return this.groupSetups;
	}

	public void setGroupSetups(Set<GroupSetup> groupSetups) {
		this.groupSetups = groupSetups;
	}
	
	public Set<QuantitationChannel> getQuantitationChannels() {
		return this.quantitationChannels;
	}

	public void setQuantitationChannels(Set<QuantitationChannel> quantitationChannels) {
		this.quantitationChannels = quantitationChannels;
	}
	
	public QuantitationMethod getMethod() {
		return this.method;
	}

	public void setMethod(QuantitationMethod method) {
		this.method = method;
	}
	
	public Set<QuantitationFraction> getQuantitationFractions() {
		return this.quantitationFractions;
	}

	public void setQuantitationFractions(Set<QuantitationFraction> quantitationFractions) {
		this.quantitationFractions = quantitationFractions;
	}
	
	public Set<SampleAnalysisReplicate> getSampleReplicates() {
		return this.sampleReplicates;
	}

	public void setSampleReplicates(Set<SampleAnalysisReplicate> sampleReplicates) {
		this.sampleReplicates = sampleReplicates;
	}
	
}
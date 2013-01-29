package fr.proline.core.orm.uds;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.Table;


/**
 * The persistent class for the dataset database table.
 * 
 */
@Entity
@Table(name="data_set")
@Inheritance(strategy=InheritanceType.JOINED)
@NamedQueries({
    @NamedQuery(name="findDatasetByProject",
		query="Select ds from Dataset ds where ds.project.id =:id"),
    @NamedQuery(name="findRootDatasetByProject",
		query="Select ds from Dataset ds where ds.project.id =:id and ds.parentDataset is null"),
    @NamedQuery(name="findDatasetNamesByProject",
    		query="Select ds.name from Dataset ds where ds.project.id =:id"),
    @NamedQuery(name="findRootDatasetNamesByProject",
    		query="Select ds.name from Dataset ds where ds.project.id =:id and ds.parentDataset is null"),
    @NamedQuery(name="findDatasetByNameAndProject",
    	query="Select ds from Dataset ds where ds.project.id =:id and ds.name=:name"),
    @NamedQuery(name="findRootDatasetByNameAndProject",
	query="Select ds from Dataset ds where ds.project.id =:id and ds.name=:name and ds.parentDataset is null")
})
public class Dataset implements Serializable{
    
	private static final long serialVersionUID = 1L;


	public enum DatasetType {
		IDENTIFICATION, QUANTITATION, AGGREGATE
	};
	    
	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	@Column(name="creation_timestamp")
	private Timestamp creationTimestamp  = new Timestamp(new Date().getTime());

	private String description;

	@Column(name="fraction_count")
	private Integer fractionCount;

	private String keywords;

	@Column(name="modification_log")
	private String modificationLog;

	private String name;

	@Enumerated(value = EnumType.STRING)    
	private DatasetType type;
	
	private Integer number;

	@ManyToOne
	@JoinColumn(name="fractionation_id")
	private Fractionation fractionation;
	
	@ManyToOne
	@JoinColumn(name="aggregation_id")
	private Aggregation aggregation;
	
	
	@ManyToOne
	@JoinColumn(name="project_id")
	private Project project;

	@Column(name="serialized_properties")
	private String serializedProperties;

	//bi-directional many-to-one association to DataSet
	@ManyToOne
	@JoinColumn(name="parent_dataset_id")
	private Dataset parentDataset;
	
	//bi-directional many-to-one association to DataSet
	@OneToMany(mappedBy="parentDataset")
	private Set<Dataset> children;	

	
	@Column(name="result_set_id")
	private Integer resultSetId;
	
	@Column(name="result_summary_id")
	private Integer resultSummaryId;
	
	//bi-directional many-to-one association to BiologicalSample
	@OneToMany(mappedBy="dataset")
	private Set<BiologicalSample> biologicalSamples;

	//bi-directional many-to-one association to GroupSetup
	@OneToMany(mappedBy="dataset")
	private Set<GroupSetup> groupSetups;

	//bi-directional many-to-one association to QuantChannel
	@OneToMany(mappedBy="dataset")
	private Set<QuantitationChannel> quantitationChannels;

	//uni-directional many-to-one association to QuantMethod
    @ManyToOne
	@JoinColumn(name="quant_method_id")
	private QuantitationMethod method;

	//bi-directional many-to-one association to MasterQuantitationChannel
	@OneToMany(mappedBy="dataset")
	private Set<MasterQuantitationChannel> masterQuantitationChannels;

	//bi-directional many-to-one association to SampleAnalysis
	@OneToMany(mappedBy="dataset")
	private Set<SampleAnalysis> sampleReplicates;

    protected Dataset() {
    }

    public Dataset(Project project) {
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


	public DatasetType getType() {
	    return type;
	}

	public void setType(DatasetType type) {
	    this.type = type;
	}

	public Dataset getParentDataset() {
	    return parentDataset;
	}

	public void setParentDataset(Dataset parentDataset) {
	    this.parentDataset = parentDataset;
	}

	public Integer getResultSetId() {
	    return resultSetId;
	}

	public void setResultSetId(Integer resultSetId) {
	    this.resultSetId = resultSetId;
	}

	public Integer getResultSummaryId() {
	    return resultSummaryId;
	}

	public void setResultSummaryId(Integer resultSummaryId) {
	    this.resultSummaryId = resultSummaryId;
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
	
	public Set<MasterQuantitationChannel> getMasterQuantitationChannels() {
		return this.masterQuantitationChannels;
	}

	public void setMasterQuantitationChannels(Set<MasterQuantitationChannel> masterQuantitationChannels) {
		this.masterQuantitationChannels = masterQuantitationChannels;
	}
	
	public Set<SampleAnalysis> getSampleReplicates() {
		return this.sampleReplicates;
	}

	public void setSampleReplicates(Set<SampleAnalysis> sampleReplicates) {
		this.sampleReplicates = sampleReplicates;
	}

	public Fractionation getFractionation() {
	    return fractionation;
	}

	public void setFractionation(Fractionation fractionation) {
	    this.fractionation = fractionation;
	}

	public Aggregation getAggregation() {
	    return aggregation;
	}

	public void setAggregation(Aggregation aggregation) {
	    this.aggregation = aggregation;
	}

	public Set<Dataset> getChildren() {
	    return children;
	}

	public void setChildren(Set<Dataset> children) {
	    this.children = children;
	}
	
	public Set<IdentificationDataset> getIdentificationDataset(){
	    Set<IdentificationDataset> idfDS = new HashSet<IdentificationDataset>();
	    if ( (getChildren() == null || getChildren().isEmpty())&& (getType().equals(DatasetType.IDENTIFICATION)) ) {		    	    	  	    		  	    		
		idfDS.add((IdentificationDataset)this);       	    	  
	    }
				
	    for (Dataset ds : getChildren()) {							
		idfDS.addAll(ds.getIdentificationDataset());
	    }
	    return idfDS;
	    
	}
	
}
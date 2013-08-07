package fr.proline.core.orm.msi;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToOne;
import javax.persistence.MapKeyColumn;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;

import fr.proline.core.orm.uds.Dataset;
import fr.proline.core.orm.util.JsonSerializer;
import fr.proline.util.StringUtils;

/**
 * The persistent class for the result_summary database table.
 * 
 */
@Entity
@Table(name = "result_summary")
public class ResultSummary implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column(name = "creation_log")
    private String creationLog;

    private String description;

    @Column(name = "is_quantified")
    private Boolean isQuantified;

    @Column(name = "modification_timestamp")
    private Timestamp modificationTimestamp;

    @ManyToOne
    @JoinColumn(name = "result_set_id")
    private ResultSet resultSet;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    // uni-directional many-to-one association to ResultSummary
    @ManyToOne
    @JoinColumn(name = "decoy_result_summary_id")
    private ResultSummary decotResultSummary;

    // uni-directional many-to-many association to ResultSummary
    @OneToMany
    @JoinTable(name = "result_summary_relation", joinColumns = { @JoinColumn(name = "parent_result_summary_id") }, inverseJoinColumns = { @JoinColumn(name = "child_result_summary_id") })
    private Set<ResultSummary> children;

    @ElementCollection
    @MapKeyColumn(name = "schema_name")
    @Column(name = "object_tree_id")
    @CollectionTable(name = "result_summary_object_tree_map", joinColumns = @JoinColumn(name = "result_summary_id", referencedColumnName = "id"))
    private Map<String, Long> objectTreeIdByName;

    // Transient Variables not saved in database
    @Transient
    private TransientData transientData = null;
    @Transient
    private Map<String, Object> serializedPropertiesMap;

    public ResultSummary() {
    }

    public long getId() {
	return id;
    }

    public void setId(final long pId) {
	id = pId;
    }

    public String getCreationLog() {
	return this.creationLog;
    }

    public void setCreationLog(String creationLog) {
	this.creationLog = creationLog;
    }

    public String getDescription() {
	return this.description;
    }

    public void setDescription(String description) {
	this.description = description;
    }

    public Boolean getIsQuantified() {
	return this.isQuantified;
    }

    public void setIsQuantified(Boolean isQuantified) {
	this.isQuantified = isQuantified;
    }

    public Timestamp getModificationTimestamp() {
	Timestamp result = null;

	if (modificationTimestamp != null) { // Should not be null
	    result = (Timestamp) modificationTimestamp.clone();
	}

	return result;
    }

    public void setModificationTimestamp(final Timestamp pModificationTimestamp) {

	if (pModificationTimestamp == null) {
	    throw new IllegalArgumentException("PModificationTimestamp is null");
	}

	modificationTimestamp = (Timestamp) pModificationTimestamp.clone();
    }

    public ResultSet getResultSet() {
	return this.resultSet;
    }

    public void setResultSet(ResultSet resultSet) {
	this.resultSet = resultSet;
    }

    public String getSerializedProperties() {
	return this.serializedProperties;
    }

    public void setSerializedProperties(String serializedProperties) {
	this.serializedProperties = serializedProperties;
    }

    public ResultSummary getDecotResultSummary() {
	return this.decotResultSummary;
    }

    public void setDecotResultSummary(ResultSummary decotResultSummary) {
	this.decotResultSummary = decotResultSummary;
    }

    public Set<ResultSummary> getChildren() {
	return this.children;
    }

    public void setChildren(Set<ResultSummary> children) {
	this.children = children;
    }

    void setObjectTreeIdByName(final Map<String, Long> objectTree) {
	objectTreeIdByName = objectTree;
    }

    public Map<String, Long> getObjectTreeIdByName() {
	return objectTreeIdByName;
    }

    public Long putObject(final String schemaName, final long objectId) {

	if (StringUtils.isEmpty(schemaName)) {
	    throw new IllegalArgumentException("Invalid schemaName");
	}

	Map<String, Long> localObjectTree = getObjectTreeIdByName();

	if (localObjectTree == null) {
	    localObjectTree = new HashMap<String, Long>();

	    setObjectTreeIdByName(localObjectTree);
	}

	return localObjectTree.put(schemaName, Long.valueOf(objectId));
    }

    public Long removeObject(final String schemaName) {
	Long result = null;

	final Map<String, Long> localObjectTree = getObjectTreeIdByName();
	if (localObjectTree != null) {
	    result = localObjectTree.remove(schemaName);
	}

	return result;
    }

    public Map<String, Object> getSerializedPropertiesAsMap() throws Exception {
	if ((serializedPropertiesMap == null) && (serializedProperties != null)) {
	    serializedPropertiesMap = JsonSerializer.getMapper().readValue(getSerializedProperties(),
		    Map.class);
	}
	return serializedPropertiesMap;
    }

    public void setSerializedPropertiesAsMap(Map<String, Object> serializedPropertiesMap) throws Exception {
	this.serializedPropertiesMap = serializedPropertiesMap;
	this.serializedProperties = JsonSerializer.getMapper().writeValueAsString(serializedPropertiesMap);
    }

    public TransientData getTransientData() {
	if (transientData == null) {
	    transientData = new TransientData();
	}
	return transientData;
    }

    /**
     * Transient Data which will be not saved in database Used by the Proline Studio IHM
     * 
     * @author JM235353
     */
    public static class TransientData implements Serializable {
	private static final long serialVersionUID = 1L;

	private PeptideInstance[] peptideInstanceArray = null;
	private ProteinSet[] proteinSetArray = null;
	private PeptideMatch[] peptideMatches;

	private Dataset dataSet = null;
	private Integer numberOfProteinSets = null;

	protected TransientData() {
	}

	public PeptideInstance[] getPeptideInstanceArray() {
	    return peptideInstanceArray;
	}

	public void setPeptideInstanceArray(PeptideInstance[] peptideInstanceArray) {
	    this.peptideInstanceArray = peptideInstanceArray;
	}

	public ProteinSet[] getProteinSetArray() {
	    return proteinSetArray;
	}

	public void setProteinSetArray(ProteinSet[] proteinSetArray) {
	    this.proteinSetArray = proteinSetArray;
	    if (proteinSetArray!=null) {
	    	numberOfProteinSets = Integer.valueOf(proteinSetArray.length);
	    }
	}

	public Integer getNumberOfProteinSet() {
	    return numberOfProteinSets;
	}

	public void setNumberOfProteinSet(Integer numberOfProteinSets) {
	    this.numberOfProteinSets = numberOfProteinSets;
	}

	public Dataset getDataSet() {
	    return dataSet;
	}

	public void setDataSet(Dataset dataSet) {
	    this.dataSet = dataSet;
	}

	public PeptideMatch[] getPeptideMatches() {
	    return peptideMatches;
	}

	public void setPeptideMatches(PeptideMatch[] peptideMatches) {
	    this.peptideMatches = peptideMatches;
	}

    }

}

package fr.proline.core.orm.msi;

import java.io.Serializable;
import javax.persistence.*;

import fr.proline.core.orm.msi.PeptideInstance;
import fr.proline.core.orm.msi.ProteinSet;
import fr.proline.core.orm.msi.PeptideMatch;
import fr.proline.core.orm.uds.Dataset;
import fr.proline.core.orm.util.JsonSerializer;


import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


/**
 * The persistent class for the result_summary database table.
 * 
 */
@Entity
@Table(name="result_summary")
public class ResultSummary implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	private String description;

	@Column(name="is_quantified")
	private Boolean isQuantified;

	@Column(name="modification_timestamp")
	private Timestamp modificationTimestamp;

	@ManyToOne
	@JoinColumn(name="result_set_id")
	private ResultSet resultSet;


	@Column(name="serialized_properties")
	private String serializedProperties;

	//uni-directional many-to-one association to ResultSummary
    @ManyToOne
	@JoinColumn(name="decoy_result_summary_id")
	private ResultSummary decotResultSummary;

	//uni-directional many-to-many association to ResultSummary
   @OneToMany
	@JoinTable(
		name="result_summary_relation"
		, joinColumns={
			@JoinColumn(name="parent_result_summary_id")
			}
		, inverseJoinColumns={
			@JoinColumn(name="child_result_summary_id")
			}
		)
	private Set<ResultSummary> children;


	@ElementCollection
   @MapKeyColumn(name="schema_name")
   @Column(name="object_tree_id")
   @CollectionTable(name="result_summary_object_tree_map",joinColumns = @JoinColumn(name = "result_summary_id", referencedColumnName = "id"))
   Map<String, Integer> objectTreeIdByName;  


	
	// Transient Variables not saved in database
    @Transient private TransientData transientData = null;
    @Transient private Map<String, Object> serializedPropertiesMap;
	
    public ResultSummary() {
    }

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
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
		return this.modificationTimestamp;
	}

	public void setModificationTimestamp(Timestamp modificationTimestamp) {
		this.modificationTimestamp = modificationTimestamp;
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
	
	public Map<String, Integer> getObjectsMap() {
		return objectTreeIdByName;
	}

	public void putObject(String schemaName, Integer objectId) {
		if (this.objectTreeIdByName == null)
			this.objectTreeIdByName = new HashMap<String, Integer>();
		this.objectTreeIdByName.put(schemaName, objectId);
	}
	
	public Map<String, Object> getSerializedPropertiesAsMap() throws Exception {
		if ((serializedPropertiesMap == null) && (serializedProperties != null)) {
			serializedPropertiesMap = JsonSerializer.getMapper().readValue(getSerializedProperties(), Map.class);
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
	 * Transient Data which will be not saved in database
	 * Used by the Proline Studio IHM
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
			numberOfProteinSets = Integer.valueOf(proteinSetArray.length);
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
			//peptideMatchesCount = peptideMatches.length;
		}
		

	
	}
	
}
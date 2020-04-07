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

import fr.proline.core.orm.MergeMode;
import fr.proline.core.orm.uds.Dataset;
import fr.proline.core.orm.uds.dto.DDataset;
import fr.proline.core.orm.util.JsonSerializer;
import fr.profi.util.StringUtils;
import fr.proline.core.orm.msi.dto.*;
import fr.proline.core.orm.util.*;

/**
 * The persistent class for the result_summary database table.
 * 
 */
@Entity
@Table(name = "result_summary")
public class ResultSummary implements Serializable, TransientDataInterface {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	@Column(name = "creation_log")
	private String creationLog;

	private String description;

	@Column(name = "is_quantified")
	private boolean isQuantified = false;

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
	private ResultSummary decoyResultSummary;

	// uni-directional many-to-many association to ResultSummary
	@OneToMany
	@JoinTable(name = "result_summary_relation", joinColumns = { @JoinColumn(name = "parent_result_summary_id") }, inverseJoinColumns = {
			@JoinColumn(name = "child_result_summary_id") })
	private Set<ResultSummary> children;

	@ElementCollection
	@MapKeyColumn(name = "schema_name")
	@Column(name = "object_tree_id")
	@CollectionTable(name = "result_summary_object_tree_map", joinColumns = @JoinColumn(name = "result_summary_id", referencedColumnName = "id") )
	private Map<String, Long> objectTreeIdByName;

	// Transient Variables not saved in database
	@Transient
	private TransientData transientData = null;
	@Transient
	private Map<String, Object> serializedPropertiesMap;
	@Transient
	private MergeMode mergeMode;

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

	public boolean getIsQuantified() {
		return this.isQuantified;
	}

	public void setIsQuantified(boolean isQuantified) {
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

	public ResultSummary getDecoyResultSummary() {
		return this.decoyResultSummary;
	}

	public void setDecoyResultSummary(ResultSummary decoyResultSummary) {
		this.decoyResultSummary = decoyResultSummary;
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

	@SuppressWarnings("unchecked")
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

	public TransientData getTransientData(TransientDataAllocationListener listener) {
		if (transientData == null) {
			transientData = new TransientData();
			if (listener != null) {
				listener.memoryAllocated(this);
			}

		}
		return transientData;
	}

	public MergeMode getMergeMode() throws Exception {
		if(mergeMode == null) {
			String mode = (String) getSerializedPropertiesAsMap().getOrDefault("merge_mode", MergeMode.NO_MERGE.name());
			mergeMode = MergeMode.valueOf(mode.toUpperCase());
		}
		return mergeMode;
	}

	public void setMergeMode(MergeMode mode){
		mergeMode=mode;
	}

	public void clearMemory() {
		transientData = null;
	}

	public String getMemoryName(String additionalName) {
		String rSetName = resultSet.getName();
		if ((rSetName == null) || (rSetName.length() == 0)) {
			return "Identification Summary "+additionalName;
		} else {
			return "Identification Summary "+rSetName;
		}
	}


	/**
	 * Transient Data which will be not saved in database Used by the Proline Studio IHM
	 * 
	 * @author JM235353
	 */
	public static class TransientData implements Serializable {

		private static final long serialVersionUID = 1L;

		private PeptideInstance[] peptideInstanceArray = null;
		private long[] peptideMatchesId;
		private DPeptideMatch[] peptideMatches;
		private DProteinSet[] proteinSetArray = null;

		private Dataset dataSet = null;
		private DDataset dDataset = null;

		private Integer numberOfProteinSets = null;
		private Integer numberOfPeptides = null;
		private Integer numberOfPeptideMatches = null;
		private Integer numberOfProteins = null;

		protected TransientData() {
		}

		public PeptideInstance[] getPeptideInstanceArray() {
			return peptideInstanceArray;
		}

		public void setPeptideInstanceArray(PeptideInstance[] peptideInstanceArray) {
			this.peptideInstanceArray = peptideInstanceArray;
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

		public DDataset getDDataset() {
			return dDataset;
		}

		public void setDDataset(DDataset dataset) {
			this.dDataset = dataset;
		}

		public DProteinSet[] getProteinSetArray() {
			return proteinSetArray;
		}

		public void setProteinSetArray(DProteinSet[] proteinSetArray) {
			this.proteinSetArray = proteinSetArray;
			if (proteinSetArray != null) {
				numberOfProteinSets = Integer.valueOf(proteinSetArray.length);
			}
		}

		public DPeptideMatch[] getPeptideMatches() {
			return peptideMatches;
		}

		public void setPeptideMatches(DPeptideMatch[] peptideMatches) {
			this.peptideMatches = peptideMatches;
		}

		public long[] getPeptideMatchesId() {
			return peptideMatchesId;
		}

		public void setPeptideMatchesId(long[] peptideMatchesId) {
			this.peptideMatchesId = peptideMatchesId;
		}

		public Integer getNumberOfProteinSets() {
			return numberOfProteinSets;
		}

		public void setNumberOfProteinSets(Integer numberOfProteinSets) {
			this.numberOfProteinSets = numberOfProteinSets;
		}

		public Integer getNumberOfPeptides() {
			return numberOfPeptides;
		}

		public void setNumberOfPeptides(Integer numberOfPeptides) {
			this.numberOfPeptides = numberOfPeptides;
		}

		public Integer getNumberOfPeptideMatches() {
			return numberOfPeptideMatches;
		}

		public void setNumberOfPeptideMatches(Integer numberOfPeptideMatches) {
			this.numberOfPeptideMatches = numberOfPeptideMatches;
		}

		public Integer getNumberOfProteins() {
			return numberOfProteins;
		}

		public void setNumberOfProteins(Integer numberOfProteins) {
			this.numberOfProteins = numberOfProteins;
		}

	}

}

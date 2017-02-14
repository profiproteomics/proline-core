package fr.proline.core.orm.msi;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.persistence.Basic;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
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

import fr.proline.core.orm.util.JsonSerializer;
import fr.profi.util.StringUtils;
import fr.proline.core.orm.msi.dto.*;

/**
 * The persistent class for the result_set database table.
 * 
 */
@Entity
@Table(name = "result_set")
public class ResultSet implements Serializable {

    public enum Type {
	SEARCH, DECOY_SEARCH, USER, DECOY_USER, QUANTITATION
    }

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column(name = "creation_log")
    private String creationLog;

    private String description;

    @Column(name = "modification_timestamp")
    private Timestamp modificationTimestamp = new Timestamp(new Date().getTime());

    private String name;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    @Basic
    @Enumerated(EnumType.STRING)
    private Type type;

    // uni-directional many-to-one association to ResultSet
    @ManyToOne
    @JoinColumn(name = "decoy_result_set_id")
    private ResultSet decoyResultSet;
    
    @Column(name = "merged_rsm_id")
    private Long mergedRsmId;
    
    @ManyToOne
    @JoinColumn(name = "msi_search_id")
    private MsiSearch msiSearch;

    @OneToMany
    @JoinTable(name = "result_set_relation", joinColumns = @JoinColumn(name = "parent_result_set_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "child_result_set_id", referencedColumnName = "id"))
    private Set<ResultSet> children;

    @ElementCollection
    @MapKeyColumn(name = "schema_name")
    @Column(name = "object_tree_id")
    @CollectionTable(name = "result_set_object_tree_map", joinColumns = @JoinColumn(name = "result_set_id", referencedColumnName = "id"))
    private Map<String, Long> objectTreeIdByName;

    @Transient
    private TransientData transientData = null;
    @Transient
    private Map<String, Object> serializedPropertiesMap;

    public ResultSet() {
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

    public String getName() {
	return this.name;
    }

    public void setName(String name) {
	this.name = name;
    }

    public String getSerializedProperties() {
	return this.serializedProperties;
    }

    public void setSerializedProperties(String serializedProperties) {
	this.serializedProperties = serializedProperties;
	this.serializedPropertiesMap = null;
    }

    public Type getType() {
	return this.type;
    }

    public void setType(Type type) {
	this.type = type;
    }

    public ResultSet getDecoyResultSet() {
	return this.decoyResultSet;
    }

    public void setDecoyResultSet(ResultSet decoyResultSet) {
	this.decoyResultSet = decoyResultSet;
    }
    
    public Long getMergedRsmId() {
	return mergedRsmId;
    }

    public void setMergedRsmId(final Long pMergedRsmId) {
    mergedRsmId = pMergedRsmId;
    }

    public MsiSearch getMsiSearch() {
	return this.msiSearch;
    }

    public void setMsiSearch(MsiSearch msiSearch) {
	this.msiSearch = msiSearch;
    }

    public Set<ResultSet> getChildren() {
	return this.children;
    }

    public void setChildren(Set<ResultSet> children) {
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



    public TransientData getTransientData() {
	if (transientData == null) {
	    transientData = new TransientData();
	}
	return transientData;
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

    /**
     * Transient Data which will be not saved in database Used by the Proline Studio IHM
     * 
     * @author JM235353
     */
	public static class TransientData implements Serializable {
		private static final long serialVersionUID = 1L;

		
		private long[] peptideMatchIds;

		private DPeptideMatch[] peptideMatches = null;
		
		private DProteinMatch[] proteinMatches = null;
		
		private Integer peptideMatchesCount = null;

		private Integer proteinMatchesCount = null;

		private Integer msQueriesCount = null;

		public long[] getPeptideMatchIds() {
			return peptideMatchIds;
		}

		public void setPeptideMatchIds(long[] peptideMatchIds) {
			this.peptideMatchIds = peptideMatchIds;
		}


		public Integer getPeptideMatchesCount() {
			return peptideMatchesCount;
		}

		public void setPeptideMatchesCount(Integer peptideMatchesCount) {
			this.peptideMatchesCount = peptideMatchesCount;
		}

		public Integer getProteinMatchesCount() {
			return proteinMatchesCount;
		}

		public void setProteinMatchesCount(Integer proteinMatchesCount) {
			this.proteinMatchesCount = proteinMatchesCount;
		}

		public Integer getMSQueriesCount() {
			return msQueriesCount;
		}

		public DPeptideMatch[] getPeptideMatches() {
			return peptideMatches;
		}

		public void setPeptideMatches(DPeptideMatch[] peptideMatches) {
			this.peptideMatches = peptideMatches;
			if (peptideMatches != null) {
				peptideMatchesCount = Integer.valueOf(peptideMatches.length);
			}
		}

		public DProteinMatch[] getProteinMatches() {
			return proteinMatches;
		}

		public void setProteinMatches(DProteinMatch[] proteinMatches) {
			this.proteinMatches = proteinMatches;
			if (proteinMatches != null) {
				proteinMatchesCount = Integer.valueOf(proteinMatches.length);
			}
		}
		


	}

}

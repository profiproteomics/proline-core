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



/**
 * The persistent class for the result_set database table.
 * 
 */
@Entity
@Table(name = "result_set")
public class ResultSet implements Serializable {

	public enum Type {
		SEARCH, DECOY_SEARCH, USER, QUANTITATION
	}
	
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private Integer id;

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

	@ManyToOne
	@JoinColumn(name = "msi_search_id")
	private MsiSearch msiSearch;

	@OneToMany
	@JoinTable(name = "result_set_relation", joinColumns = @JoinColumn(name = "parent_result_set_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "child_result_set_id", referencedColumnName = "id"))
	private Set<ResultSet> children;

	@ElementCollection
    @MapKeyColumn(name="schema_name")
    @Column(name="object_tree_id")
    @CollectionTable(name="result_set_object_tree_map",joinColumns = @JoinColumn(name = "result_set_id", referencedColumnName = "id"))
    Map<String, Integer> objectTreeIdByName;  
	
	// Transient Variables not saved in database
    @Transient private TransientData transientData = null;

	// Transient Variables not saved in database
	@Transient private PeptideMatch[] peptideMatches = null; //JPM.TODO : remove it

	
	public ResultSet() {
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

	public Timestamp getModificationTimestamp() {
		return this.modificationTimestamp;
	}

	public void setModificationTimestamp(Timestamp modificationTimestamp) {
		this.modificationTimestamp = modificationTimestamp;
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

	public Map<String, Integer> getObjectsMap() {
		return objectTreeIdByName;
	}

	public void putObject(String schemaName, Integer objectId) {
		if (this.objectTreeIdByName == null)
			this.objectTreeIdByName = new HashMap<String, Integer>();
		this.objectTreeIdByName.put(schemaName, objectId);
	}

	/**
	 * Get of Transient peptideMatches, Must be set beforehand.
	 * @return
	 */
	//JPM.TODO : remove two next methodes
	public PeptideMatch[] getTransientPeptideMatches() {
		return peptideMatches;
	}

	public void setTransientPeptideMatches(PeptideMatch[] peptideMatches) {
		this.peptideMatches = peptideMatches;
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
		
		private PeptideMatch[] peptideMatches;
		
		private Integer peptideMatchesCount = null;
		private Integer msQueriesCount= null;
		private Integer proteinMatchesCount= null;
		
		public PeptideMatch[] getPeptideMatches() {
			return peptideMatches;
		}

		public void setPeptideMatches(PeptideMatch[] peptideMatches) {
			this.peptideMatches = peptideMatches;
			peptideMatchesCount = peptideMatches.length;
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

		public void setMSQueriesCount(Integer msQueriesCount) {
			this.msQueriesCount = msQueriesCount;
		}
	}
    
}
package fr.proline.core.orm.msi;

import java.io.Serializable;
import javax.persistence.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;


/**
 * The persistent class for the protein_set database table.
 * 
 */
@Entity
@Table(name="protein_set")
public class ProteinSet implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	@Column(name="is_validated")
	private Boolean isValidated;

	@Column(name="master_quant_component_id")
	private Integer masterQuantComponentId;

	@ManyToOne
	@JoinColumn(name="result_summary_id")
	private ResultSummary resultSummary;

	@Column(name="selection_level")
	private Integer selectionLevel;

	@Column(name="serialized_properties")
	private String serializedProperties;

	//bi-directional one-to-one association to PeptideSet
	@OneToOne(mappedBy="proteinSet")
	private PeptideSet peptideOverSet;

	@Column(name="typical_protein_match_id")
	private Integer typicalProteinMatchId;

	//bi-directional many-to-one association to ProteinSetProteinMatchItem
	@OneToMany(mappedBy="proteinSet")
	private Set<ProteinSetProteinMatchItem> proteinSetProteinMatchItems;


	@ElementCollection
   @MapKeyColumn(name="schema_name")
   @Column(name="object_tree_id")
   @CollectionTable(name="protein_set_object_tree_map",joinColumns = @JoinColumn(name = "protein_set_id", referencedColumnName = "id"))
   Map<String, Integer> objectTreeIdByName;  
	
	
	// Transient data not saved in database
	@Transient private TransientData transientData = null;
	
    public ProteinSet() {
    }

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Boolean getIsValidated() {
		return this.isValidated;
	}

	public void setIsValidated(Boolean isValidated) {
		this.isValidated = isValidated;
	}

	public Integer getMasterQuantComponentId() {
		return this.masterQuantComponentId;
	}

	public void setMasterQuantComponentId(Integer masterQuantComponentId) {
		this.masterQuantComponentId = masterQuantComponentId;
	}

	public ResultSummary getResultSummary() {
		return this.resultSummary;
	}

	public void setResultSummary(ResultSummary resultSummary) {
		this.resultSummary = resultSummary;
	}

	public Integer getSelectionLevel() {
		return this.selectionLevel;
	}

	public void setSelectionLevel(Integer selectionLevel) {
		this.selectionLevel = selectionLevel;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public PeptideSet getPeptideOverSet() {
		return this.peptideOverSet;
	}

	public void setPeptideOverSet(PeptideSet peptideSet) {
		this.peptideOverSet = peptideSet;
	}
	
	public Integer getProteinMatchId() {
		return this.typicalProteinMatchId;
	}

	public void setProteinMatchId(Integer proteinMatchId) {
		this.typicalProteinMatchId = proteinMatchId;
	}
	
	public Set<ProteinSetProteinMatchItem> getProteinSetProteinMatchItems() {
		return this.proteinSetProteinMatchItems;
	}

	public void setProteinSetProteinMatchItems(Set<ProteinSetProteinMatchItem> proteinSetProteinMatchItems) {
		this.proteinSetProteinMatchItems = proteinSetProteinMatchItems;
	}
	
	public Map<String, Integer> getObjectsMap() {
		return objectTreeIdByName;
	}

	public void putObject(String schemaName, Integer objectId) {
		if (this.objectTreeIdByName == null)
			this.objectTreeIdByName = new HashMap<String, Integer>();
		this.objectTreeIdByName.put(schemaName, objectId);
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
		
		private ProteinMatch   typicalProteinMatch   = null;
		private ProteinMatch[] sameSet               = null; // loaded later than sameSetCount
		private ProteinMatch[] subSet                = null; // loaded later than subSetCount
		private Integer        spectralCount         = null;
		private Integer        specificSpectralCount = null;
		private Integer        sameSetCount          = null;
		private Integer        subSetCount           = null;
		
		protected TransientData() {
		}
		
		public ProteinMatch getTypicalProteinMatch() {
			return typicalProteinMatch;
		}
		public void setTypicalProteinMatch(ProteinMatch p) {
			typicalProteinMatch = p;
		}	

		public ProteinMatch[] getSameSet() {
			return sameSet;
		}
		public void setSameSet(ProteinMatch[] sameSet) {
			this.sameSet = sameSet;
		}
		
		public ProteinMatch[] getSubSet() {
			return subSet;
		}
		public void setSubSet(ProteinMatch[] subSet) {
			this.subSet = subSet;
		}
		
		public Integer getSpectralCount() {
			return spectralCount;
		}
		public void setSpectralCount(Integer spectralCount) {
			this.spectralCount = spectralCount;
		}
		
		public Integer getSpecificSpectralCount() {
			return specificSpectralCount;
		}
		public void setSpecificSpectralCount(Integer specificSpectralCount) {
			this.specificSpectralCount = specificSpectralCount;
		}
		
		public Integer getSameSetCount() {
			return sameSetCount;
		}
		public void setSameSetCount(Integer sameSetCount) {
			this.sameSetCount = sameSetCount;
		}
		
		public Integer getSubSetCount() {
			return subSetCount;
		}
		public void setSubSetCount(Integer subSetCount) {
			this.subSetCount = subSetCount;
		}
		
		
	}
	
	
}
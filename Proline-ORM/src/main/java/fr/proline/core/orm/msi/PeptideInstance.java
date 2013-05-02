package fr.proline.core.orm.msi;

import java.io.Serializable;
import javax.persistence.*;


import fr.proline.core.orm.msi.ProteinSet;

import java.util.Set;


/**
 * The persistent class for the peptide_instance database table.
 * 
 */
@Entity
@NamedQueries ({
@NamedQuery(name="findPepInstByPepMatch",
query="select pi from PeptideInstance pi, IN (pi.peptideInstancePeptideMatchMaps) pm where pm.id.peptideMatchId = :pmID "),

@NamedQuery(name="findPepInstForPeptideId",
query="select pi from PeptideInstance pi where pi.peptideId = :pepID ")

})
@Table(name="peptide_instance")
public class PeptideInstance implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	@Column(name = "best_peptide_match_id")
	private Integer bestPeptideMatchId;

	@Column(name="master_quant_component_id")
	private Integer masterQuantComponentId;

	@Column(name="peptide_id")
	private Integer peptideId;

	@Column(name="peptide_match_count")
	private Integer peptideMatchCount;

	@Column(name="protein_match_count")
	private Integer proteinMatchCount;

	@Column(name="protein_set_count")
	private Integer proteinSetCount;
	
    @Column(name="total_leaves_match_count")
    private Integer totalLeavesMatchCount;

	@Column(name="validated_protein_set_count")
	private Integer validatedProteinSetCount;

	@Column(name="total_leaves_match_count")
	private Integer totalLeavesMatchCount;

	@Column(name="elution_time")
	private Float elutionTime;
	
	@ManyToOne
	@JoinColumn(name = "result_summary_id")
	private ResultSummary resultSummary;

	@Column(name="selection_level")
	private Integer selectionLevel;

	@Column(name="serialized_properties")
	private String serializedProperties;

	@Column(name="unmodified_peptide_id")
	private Integer unmodifiedPeptideId;

	//bi-directional many-to-one association to PeptideSetPeptideInstanceItem
	@OneToMany(mappedBy="peptideInstance")
	private Set<PeptideSetPeptideInstanceItem> peptideSetPeptideInstanceItems;

	//uni-directional many-to-one association to PeptideInstancePeptideMatchMap
  @OneToMany(mappedBy="peptideInstance")
  private Set<PeptideInstancePeptideMatchMap> peptideInstancePeptideMatchMaps;
  
	/*@OneToMany
	@JoinTable(
			name="peptide_instance_peptide_match_map"
			, joinColumns={
				@JoinColumn(name="peptide_match_id")
				}
			, inverseJoinColumns={
				@JoinColumn(name="peptide_instance_id")
				}
			)
	private Set<PeptideMatch> peptidesMatches; 
	
    public PeptideInstance() {
    }*/

    //Transient Variable not saved in database
    @Transient private PeptideMatch bestPeptideMatch = null; //JPM.DEPRECATED : will remove it
	
	// Transient Variables not saved in database
    @Transient private TransientData transientData = null;
    
	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Integer getBestPeptideMatchId() {
		return this.bestPeptideMatchId;
	}

	public void setBestPeptideMatchId(Integer bestPeptideMatchId) {
		this.bestPeptideMatchId = bestPeptideMatchId;
	}

	public Integer getMasterQuantComponentId() {
		return this.masterQuantComponentId;
	}

	public void setMasterQuantComponentId(Integer masterQuantComponentId) {
		this.masterQuantComponentId = masterQuantComponentId;
	}

	public Integer getPeptideId() {
		return this.peptideId;
	}

	public void setPeptideId(Integer peptideId) {
		this.peptideId = peptideId;
	}

	public Integer getPeptideMatchCount() {
		return this.peptideMatchCount;
	}

	public void setPeptideMatchCount(Integer peptideMatchCount) {
		this.peptideMatchCount = peptideMatchCount;
	}

	public Integer getProteinMatchCount() {
		return this.proteinMatchCount;
	}

	public void setProteinMatchCount(Integer proteinMatchCount) {
		this.proteinMatchCount = proteinMatchCount;
	}

	public Integer getProteinSetCount() {
		return this.proteinSetCount;
	}

	public void setProteinSetCount(Integer proteinSetCount) {
		this.proteinSetCount = proteinSetCount;
	}
	
    public Integer getTotalLeavesMatchCount() {
      return this.totalLeavesMatchCount;
    }

    public void setTotalLeavesMatchCount(Integer totalLeavesMatchCount) {
      this.totalLeavesMatchCount = totalLeavesMatchCount;
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

	public Float getElutionTime() {
		return elutionTime;
	}

	public void setElutionTime(Float elutionTime) {
		this.elutionTime = elutionTime;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public Integer getUnmodifiedPeptideId() {
		return this.unmodifiedPeptideId;
	}

	public void setUnmodifiedPeptideId(Integer unmodifiedPeptideId) {
		this.unmodifiedPeptideId = unmodifiedPeptideId;
	}
	
	public Integer getValidatedProteinSetCount() {
		return this.validatedProteinSetCount;
	}

	public void setValidatedProteinSetCount(Integer validatedProteinSetCount) {
		this.validatedProteinSetCount = validatedProteinSetCount;
	}

	public Set<PeptideSetPeptideInstanceItem> getPeptideSetPeptideInstanceItems() {
		return this.peptideSetPeptideInstanceItems;
	}

	public void setPeptideSetPeptideInstanceItems(Set<PeptideSetPeptideInstanceItem> peptideSetPeptideInstanceItems) {
		this.peptideSetPeptideInstanceItems = peptideSetPeptideInstanceItems;
	}
	
  public Set<PeptideInstancePeptideMatchMap> getPeptideInstancePeptideMatchMaps() {
    return this.peptideInstancePeptideMatchMaps;
  }

  public void setPeptideInstancePeptideMatchMaps(Set<PeptideInstancePeptideMatchMap> peptideInstancePeptideMatchMaps) {
    this.peptideInstancePeptideMatchMaps = peptideInstancePeptideMatchMaps;
  }

	/*public Set<PeptideMatch> getPeptidesMatches() {
		return peptidesMatches;
	}

	public void setPeptidesMatches(Set<PeptideMatch> peptidesMatches) {
		this.peptidesMatches = peptidesMatches;
	}*/
	
	/**
	 * Get of Transient peptideSet, Must be set by the user first.
	 * @return
	 */
	public PeptideMatch getTransientBestPeptideMatch() {  //JPM.DEPRECATED : will remove it
		return bestPeptideMatch;
	}

	public void setTransientBestPeptideMatch(PeptideMatch bestPeptideMatch) {  //JPM.DEPRECATED : will remove it
		this.bestPeptideMatch = bestPeptideMatch;
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
		
		private ProteinSet[] proteinSetArray = null;
		private PeptideMatch bestPeptideMatch = null;
		
		protected TransientData() {
		}
		
		public ProteinSet[] getProteinSetArray() {
			return proteinSetArray;
		}

		public void setProteinSetArray(ProteinSet[] proteinSetArray) {
			this.proteinSetArray = proteinSetArray;
		}
		
		public PeptideMatch getBestPeptideMatch() {
			return bestPeptideMatch;
		}

		public void setBestPeptideMatch(PeptideMatch bestPeptideMatch) {
			this.bestPeptideMatch = bestPeptideMatch;
		}
	}
}
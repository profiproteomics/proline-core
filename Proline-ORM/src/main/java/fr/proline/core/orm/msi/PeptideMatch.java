package fr.proline.core.orm.msi;

import java.io.Serializable;
import javax.persistence.*;

import fr.proline.core.orm.msi.ProteinSet.TransientData;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;


/**
 * The persistent class for the peptide_match database table.
 * 
 */
@Entity
@NamedQueries({
@NamedQuery( 
	name="findPeptideMatchesByResultSet",
	query="Select pm from PeptideMatch pm where pm.resultSet.id=:id"
),
@NamedQuery( 
	name="findPeptideMatchesByPeptide",
	query="Select pm from PeptideMatch pm where pm.peptideId=:id"
	),
@NamedQuery( 
	name="findPeptideMatchesByPeptideAndResultSet",
	query="Select pm from PeptideMatch pm where pm.resultSet.id=:resultset_id and pm.peptideId=:peptide_id"
)
})
@Table(name="peptide_match")
public class PeptideMatch implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	private Integer charge;

	@Column(name="delta_moz")
	private Double deltaMoz;

	@Column(name="experimental_moz")
	private Double experimentalMoz;

	@Column(name="fragment_match_count")
	private Integer fragmentMatchCount;

	@Column(name="is_decoy")
	private Boolean isDecoy;

	@Column(name="missed_cleavage")
	private Integer missedCleavage;

	@Column(name="peptide_id")
	private Integer peptideId;

	private Integer rank;

   @ManyToOne
	@JoinColumn(name="result_set_id")
	private ResultSet resultSet;

	private Float score;

	@Column(name="scoring_id")
	private Integer scoringId;

	@Column(name="serialized_properties")
	private String serializedProperties;

	//bi-directional many-to-one association to MsQuery
    @ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name="ms_query_id")
	private MsQuery msQuery;

	//uni-directional many-to-one association to PeptideMatch
    @ManyToOne
	@JoinColumn(name="best_child_id")
	private PeptideMatch bestPeptideMatch;

	//uni-directional many-to-many association to PeptideMatch
    @OneToMany
	@JoinTable(
		name="peptide_match_relation"
		, joinColumns={
			@JoinColumn(name="child_peptide_match_id")
			}
		, inverseJoinColumns={
			@JoinColumn(name="parent_peptide_match_id")
			}
		)
	private Set<PeptideMatch> children;


 	@ElementCollection
    @MapKeyColumn(name="schema_name")
    @Column(name="object_tree_id")
    @CollectionTable(name="peptide_match_object_tree_map",joinColumns = @JoinColumn(name = "peptide_match_id", referencedColumnName = "id"))
    Map<String, Integer> objectTreeIdByName;  
 	
 	
    // Transient Variable not saved in database
 	@Transient private TransientData transientData = null;
 	
    public PeptideMatch() {
    }

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Integer getCharge() {
		return this.charge;
	}

	public void setCharge(Integer charge) {
		this.charge = charge;
	}

	public double getDeltaMoz() {
		return this.deltaMoz;
	}

	public void setDeltaMoz(double deltaMoz) {
		this.deltaMoz = deltaMoz;
	}

	public double getExperimentalMoz() {
		return this.experimentalMoz;
	}

	public void setExperimentalMoz(double experimentalMoz) {
		this.experimentalMoz = experimentalMoz;
	}

	public Integer getFragmentMatchCount() {
		return this.fragmentMatchCount;
	}

	public void setFragmentMatchCount(Integer fragmentMatchCount) {
		this.fragmentMatchCount = fragmentMatchCount;
	}

	public Boolean getIsDecoy() {
		return this.isDecoy;
	}

	public void setIsDecoy(Boolean isDecoy) {
		this.isDecoy = isDecoy;
	}

	public Integer getMissedCleavage() {
		return this.missedCleavage;
	}

	public void setMissedCleavage(Integer missedCleavage) {
		this.missedCleavage = missedCleavage;
	}

	public Integer getPeptideId() {
		return this.peptideId;
	}

	public void setPeptideId(Integer peptideId) {
		this.peptideId = peptideId;
	}

	public Integer getRank() {
		return this.rank;
	}

	public void setRank(Integer rank) {
		this.rank = rank;
	}

	public ResultSet getResultSet() {
		return this.resultSet;
	}

	public void setResultSet(ResultSet resultSet) {
		this.resultSet = resultSet;
	}

	public float getScore() {
		return this.score;
	}

	public void setScore(float score) {
		this.score = score;
	}

	public Integer getScoringId() {
		return this.scoringId;
	}

	public void setScoringId(Integer scoringId) {
		this.scoringId = scoringId;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public MsQuery getMsQuery() {
		return this.msQuery;
	}

	public void setMsQuery(MsQuery msQuery) {
		this.msQuery = msQuery;
	}
	
	public PeptideMatch getBestPeptideMatch() {
		return this.bestPeptideMatch;
	}

	public void setBestPeptideMatch(PeptideMatch bestPeptideMatch) {
		this.bestPeptideMatch = bestPeptideMatch;
	}
	
	public Set<PeptideMatch> getChildren() {
		return this.children;
	}

	public void setChildren(Set<PeptideMatch> children) {
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
		
		private Peptide peptide = null;
		private boolean isMsQuerySet = false;
		private ProteinMatch[] proteinMatchArray = null;
		
		protected TransientData() {
		}
		
		public Peptide getPeptide() {
			return peptide;
		}

		public void setPeptide(Peptide peptide) {
			this.peptide = peptide;
		}
		
		public boolean getIsMsQuerySet() {
			return isMsQuerySet;
		}

		public void setIsMsQuerySet(boolean isMsQuerySet) {
			this.isMsQuerySet = isMsQuerySet;
		}
		
		public ProteinMatch[] getProteinMatches() {
			return proteinMatchArray;
		}
		
		public void setProteinMatches(ProteinMatch[] proteinMatchArray) {
			this.proteinMatchArray = proteinMatchArray;
		}
		
	}
}
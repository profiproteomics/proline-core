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

	private float score;

	@Column(name="scoring_id")
	private Integer scoringId;

	@Column(name="selection_level")
	private Integer selectionLevel;

	@Column(name="serialized_properties")
	private String serializedProperties;

	//bi-directional many-to-one association to PeptideSet
	@OneToMany(mappedBy="proteinSet")
	private Set<PeptideSet> peptideSets;

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

	public Set<PeptideSet> getPeptideSets() {
		return this.peptideSets;
	}

	public void setPeptideSets(Set<PeptideSet> peptideSets) {
		this.peptideSets = peptideSets;
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
}
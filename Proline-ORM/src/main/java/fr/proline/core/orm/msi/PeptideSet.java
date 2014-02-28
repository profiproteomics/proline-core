package fr.proline.core.orm.msi;

import java.io.Serializable;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;

import fr.proline.core.orm.msi.dto.DPeptideInstance;

/**
 * The persistent class for the peptide_set database table.
 * 
 */
@Entity
@Table(name = "peptide_set")
public class PeptideSet implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column(name = "is_subset")
    private boolean isSubset;

    private float score;

    // uni-directional many-to-one association to Scoring
    @ManyToOne
    @JoinColumn(name = "scoring_id")
    private Scoring scoring;

    @Column(name = "peptide_count")
    private Integer peptideCount;

    @Column(name = "peptide_match_count")
    private Integer peptideMatchCount;

    @Column(name = "result_summary_id")
    private long resultSummaryId;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    // bi-directional many-to-one association to ProteinSet
    @ManyToOne
    @JoinColumn(name = "protein_set_id")
    private ProteinSet proteinSet;

    // bi-directional many-to-one association to PeptideSetPeptideInstanceItem
    @OneToMany(mappedBy = "peptideSet")
    private Set<PeptideSetPeptideInstanceItem> peptideSetPeptideInstanceItems;

    // uni-directional many-to-many association to ProteinMatch
    @ManyToMany
    @JoinTable(name = "peptide_set_protein_match_map", joinColumns = { @JoinColumn(name = "peptide_set_id") }, inverseJoinColumns = { @JoinColumn(name = "protein_match_id") })
    private Set<ProteinMatch> proteinMatches;

    // Transient Variables not saved in database
    @Transient
    private PeptideInstance[] peptideInstances = null; //JPM.TODO : will be removed
    @Transient
    private DPeptideInstance[] dpeptideInstances = null; 

    public PeptideSet() {
    }

    public long getId() {
	return id;
    }

    public void setId(final long pId) {
	id = pId;
    }

    public boolean getIsSubset() {
	return this.isSubset;
    }

    public void setIsSubset(boolean isSubset) {
	this.isSubset = isSubset;
    }

    public Integer getPeptideCount() {
	return this.peptideCount;
    }

    public void setPeptideCount(Integer peptideCount) {
	this.peptideCount = peptideCount;
    }

    public Integer getPeptideMatchCount() {
	return this.peptideMatchCount;
    }

    public void setPeptideMatchCount(Integer peptideMatchCount) {
	this.peptideMatchCount = peptideMatchCount;
    }

    public long getResultSummaryId() {
	return resultSummaryId;
    }

    public void setResultSummaryId(final long pResultSummaryId) {
	resultSummaryId = pResultSummaryId;
    }

    public float getScore() {
	return this.score;
    }

    public void setScore(float score) {
	this.score = score;
    }

    public Scoring getScoring() {
	return this.scoring;
    }

    public void setScoring(Scoring scoring) {
	this.scoring = scoring;
    }

    public String getSerializedProperties() {
	return this.serializedProperties;
    }

    public void setSerializedProperties(String serializedProperties) {
	this.serializedProperties = serializedProperties;
    }

    public ProteinSet getProteinSet() {
	return this.proteinSet;
    }

    public void setProteinSet(ProteinSet proteinSet) {
	this.proteinSet = proteinSet;
    }

    public Set<PeptideSetPeptideInstanceItem> getPeptideSetPeptideInstanceItems() {
	return this.peptideSetPeptideInstanceItems;
    }

    public void setPeptideSetPeptideInstanceItems(
	    Set<PeptideSetPeptideInstanceItem> peptideSetPeptideInstanceItems) {
	this.peptideSetPeptideInstanceItems = peptideSetPeptideInstanceItems;
    }

    public Set<ProteinMatch> getProteinMatches() {
	return this.proteinMatches;
    }

    public void setProteinMatches(Set<ProteinMatch> proteinMatches) {
	this.proteinMatches = proteinMatches;
    }

    /**
     * Get of Transient peptideInstances, Must be set by the user first.
     * 
     * @return
     */
    public PeptideInstance[] getTransientPeptideInstances() {
	return peptideInstances;  //JPM.TODO : will be removed
    }

    public void setTransientPeptideInstances(PeptideInstance[] peptideInstances) {
	this.peptideInstances = peptideInstances;  //JPM.TODO : will be removed
    }
    public DPeptideInstance[] getTransientDPeptideInstances() {
	return dpeptideInstances;
    }

    public void setTransientDPeptideInstances(DPeptideInstance[] dpeptideInstances) {
	this.dpeptideInstances = dpeptideInstances;
    }

}

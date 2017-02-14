package fr.proline.core.orm.msi;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToOne;
import javax.persistence.MapKeyColumn;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import fr.profi.util.StringUtils;

/**
 * The persistent class for the peptide_match database table.
 * 
 */
@Entity
@NamedQueries({
	@NamedQuery(name = "findPeptideMatchesByResultSet", query = "Select pm from PeptideMatch pm where pm.resultSet.id=:id"),
	@NamedQuery(name = "findPeptideMatchesByPeptide", query = "Select pm from PeptideMatch pm where pm.peptideId=:id"),
	@NamedQuery(name = "findPeptideMatchesByPeptideAndResultSet", query = "Select pm from PeptideMatch pm where pm.resultSet.id=:resultset_id and pm.peptideId=:peptide_id") })
@Table(name = "peptide_match")
public class PeptideMatch implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    private int charge;

    @Column(name = "delta_moz")
    private Float deltaMoz;

    @Column(name = "experimental_moz")
    private double experimentalMoz;

    @Column(name = "fragment_match_count")
    private Integer fragmentMatchCount;

    @Column(name = "is_decoy")
    private boolean isDecoy;

    @Column(name = "missed_cleavage")
    private int missedCleavage;

    @Column(name = "peptide_id")
    private long peptideId;

    private Integer rank;

    @Column(name = "cd_pretty_rank")
    private Integer cdPrettyRank;

    @Column(name = "sd_pretty_rank")
    private Integer sdPrettyRank;

    @ManyToOne
    @JoinColumn(name = "result_set_id")
    private ResultSet resultSet;

    private Float score;

    @Column(name = "scoring_id")
    private long scoringId;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    // bi-directional many-to-one association to MsQuery
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ms_query_id")
    private MsQuery msQuery;

    // uni-directional many-to-one association to PeptideMatch
    @ManyToOne
    @JoinColumn(name = "best_child_id")
    private PeptideMatch bestPeptideMatch;

    // uni-directional many-to-many association to PeptideMatch
    @OneToMany
    @JoinTable(name = "peptide_match_relation", joinColumns = { @JoinColumn(name = "child_peptide_match_id") }, inverseJoinColumns = { @JoinColumn(name = "parent_peptide_match_id") })
    private Set<PeptideMatch> children;

    @ElementCollection
    @MapKeyColumn(name = "schema_name")
    @Column(name = "object_tree_id")
    @CollectionTable(name = "peptide_match_object_tree_map", joinColumns = @JoinColumn(name = "peptide_match_id", referencedColumnName = "id"))
    private Map<String, Long> objectTreeIdByName;

    public PeptideMatch() {
    }

    public long getId() {
	return id;
    }

    public void setId(final long pId) {
	id = pId;
    }

    public int getCharge() {
	return charge;
    }

    public void setCharge(final int pCharge) {
	charge = pCharge;
    }

    public Float getDeltaMoz() {
	return deltaMoz;
    }

    public void setDeltaMoz(final Float pDeltaMoz) {
	deltaMoz = pDeltaMoz;
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

    public boolean getIsDecoy() {
	return isDecoy;
    }

    public void setIsDecoy(final boolean pIsDecoy) {
	isDecoy = pIsDecoy;
    }

    public int getMissedCleavage() {
	return this.missedCleavage;
    }

    public void setMissedCleavage(final int pMissedCleavage) {
	missedCleavage = pMissedCleavage;
    }

    public long getPeptideId() {
	return peptideId;
    }

    public void setPeptideId(final long pPeptideId) {
	peptideId = pPeptideId;
    }

    public Integer getRank() {
	return this.rank;
    }

    public void setRank(Integer rank) {
	this.rank = rank;
    }

    public Integer getCDPrettyRank() {
	return this.cdPrettyRank;
    }

    public void setCDPrettyRank(Integer cdPrettyRank) {
	this.cdPrettyRank = cdPrettyRank;
    }

    public Integer getSDPrettyRank() {
	return this.sdPrettyRank;
    }

    public void setSDPrettyRank(Integer sdPrettyRank) {
	this.sdPrettyRank = sdPrettyRank;
    }

    public ResultSet getResultSet() {
	return this.resultSet;
    }

    public void setResultSet(ResultSet resultSet) {
	this.resultSet = resultSet;
    }

    public Float getScore() {
	return score;
    }

    public void setScore(final Float pScore) {
	score = pScore;
    }

    public long getScoringId() {
	return scoringId;
    }

    public void setScoringId(final long pScoringId) {
	scoringId = pScoringId;
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


}

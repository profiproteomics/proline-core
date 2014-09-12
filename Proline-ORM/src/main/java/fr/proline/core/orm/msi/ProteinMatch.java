package fr.proline.core.orm.msi;

import java.io.Serializable;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.Table;

/**
 * The persistent class for the protein_match database table.
 * 
 */
@Entity
@NamedQueries({
	@NamedQuery(name = "findProteinMatchesForResultSet", query = "select pm from fr.proline.core.orm.msi.ProteinMatch pm"
		+ " where pm.resultSet = :resultSet"),

	@NamedQuery(name = "findProteinMatchesForResultSetId", query = "select pm from fr.proline.core.orm.msi.ProteinMatch pm"
		+ " where pm.resultSet.id = :resultSetId")

})
@Table(name = "protein_match")
public class ProteinMatch implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    private String accession;

    @Column(name = "bio_sequence_id")
    private Long bioSequenceId;

    @Column(name = "is_last_bio_sequence")
    private boolean isLastBioSequence;

    private float coverage;

    private String description;

    @Column(name = "gene_name")
    private String geneName;

    @Column(name = "is_decoy")
    private boolean isDecoy;

    @Column(name = "peptide_count")
    private int peptideCount;

    @Column(name = "peptide_match_count")
    private int peptideMatchCount;

    @ManyToOne
    @JoinColumn(name = "result_set_id")
    private ResultSet resultSet;

    private Float score;

    @Column(name = "scoring_id")
    private long scoringId;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    @Column(name = "taxon_id")
    private Long taxonId;

    // bi-directional many-to-one association to ProteinSetProteinMatchItem
    @OneToMany(mappedBy = "proteinMatch")
    private Set<ProteinSetProteinMatchItem> proteinSetProteinMatchItems;


    public ProteinMatch() {
    }

    public long getId() {
	return id;
    }

    public void setId(final long pId) {
	id = pId;
    }

    public String getAccession() {
	return this.accession;
    }

    public void setAccession(String accession) {
	this.accession = accession;
    }

    public Long getBioSequenceId() {
	return bioSequenceId;
    }

    public void setBioSequenceId(final Long pBioSequenceId) {
	bioSequenceId = pBioSequenceId;
    }

    public float getCoverage() {
	return this.coverage;
    }

    public void setCoverage(float coverage) {
	this.coverage = coverage;
    }

    public String getDescription() {
	return this.description;
    }

    public void setDescription(String description) {
	this.description = description;
    }

    public String getGeneName() {
	return this.geneName;
    }

    public void setGeneName(String geneName) {
	this.geneName = geneName;
    }

    public boolean getIsDecoy() {
	return isDecoy;
    }

    public void setIsDecoy(final boolean pIsDecoy) {
	isDecoy = pIsDecoy;
    }

    public int getPeptideCount() {
	return peptideCount;
    }

    public void setPeptideCount(final int pPeptideCount) {
	peptideCount = pPeptideCount;
    }

    public int getPeptideMatchCount() {
	return this.peptideMatchCount;
    }

    public void setPeptideMatchCount(int peptideMatchCount) {
	this.peptideMatchCount = peptideMatchCount;
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

    public Long getTaxonId() {
	return taxonId;
    }

    public void setTaxonId(final Long pTaxonId) {
	taxonId = pTaxonId;
    }

    public Set<ProteinSetProteinMatchItem> getProteinSetProteinMatchItems() {
	return this.proteinSetProteinMatchItems;
    }

    public void setProteinSetProteinMatchItems(Set<ProteinSetProteinMatchItem> proteinSetProteinMatchItems) {
	this.proteinSetProteinMatchItems = proteinSetProteinMatchItems;
    }

    public boolean getIsLastBioSequence() {
	return isLastBioSequence;
    }

    public void setIsLastBioSequence(final boolean pIsLastBioSequence) {
	isLastBioSequence = pIsLastBioSequence;
    }

}

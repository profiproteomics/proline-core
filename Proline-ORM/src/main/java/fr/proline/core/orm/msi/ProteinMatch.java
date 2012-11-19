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
import javax.persistence.Transient;

import fr.proline.core.orm.msi.PeptideMatch.TransientData;

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
    private Integer id;

    private String accession;

    @Column(name = "bio_sequence_id")
    private Integer bioSequenceId;

    @Column(name = "is_last_bio_sequence")
    private Boolean isLastBioSequence;

    private float coverage;

    private String description;

    @Column(name = "gene_name")
    private String geneName;

    @Column(name = "is_decoy")
    private Boolean isDecoy;

    @Column(name = "peptide_count")
    private Integer peptideCount;

    @Column(name = "peptide_match_count")
    private Integer peptideMatchCount;

    @ManyToOne
    @JoinColumn(name = "result_set_id")
    private ResultSet resultSet;

    private float score;

    @Column(name = "scoring_id")
    private Integer scoringId;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    @Column(name = "taxon_id")
    private Integer taxonId;

    // bi-directional many-to-one association to ProteinSetProteinMatchItem
    @OneToMany(mappedBy = "proteinMatch")
    private Set<ProteinSetProteinMatchItem> proteinSetProteinMatchItems;

	// Transient Variables not saved in database
    @Transient private TransientData transientData = null;
    
    public ProteinMatch() {
    }

    public Integer getId() {
	return this.id;
    }

    public void setId(Integer id) {
	this.id = id;
    }

    public String getAccession() {
	return this.accession;
    }

    public void setAccession(String accession) {
	this.accession = accession;
    }

    public Integer getBioSequenceId() {
	return this.bioSequenceId;
    }

    public void setBioSequenceId(Integer bioSequenceId) {
	this.bioSequenceId = bioSequenceId;
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

    public Boolean getIsDecoy() {
	return this.isDecoy;
    }

    public void setIsDecoy(Boolean isDecoy) {
	this.isDecoy = isDecoy;
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

    public Integer getTaxonId() {
	return this.taxonId;
    }

    public void setTaxonId(Integer taxonId) {
	this.taxonId = taxonId;
    }

    public Set<ProteinSetProteinMatchItem> getProteinSetProteinMatchItems() {
	return this.proteinSetProteinMatchItems;
    }

    public void setProteinSetProteinMatchItems(Set<ProteinSetProteinMatchItem> proteinSetProteinMatchItems) {
	this.proteinSetProteinMatchItems = proteinSetProteinMatchItems;
    }

    public Boolean getIsLastBioSequence() {
	return isLastBioSequence;
    }

    public void setIsLastBioSequence(Boolean isLastBioSequence) {
	this.isLastBioSequence = isLastBioSequence;
    }
    
	public TransientData getTransientData() {
		return transientData;
	}

	public void setTransientData(TransientData transientData) {
		this.transientData = transientData;
	}
	
	/**
	 * Transient Data which will be not saved in database
	 * Used by the Proline Studio IHM
	 * @author JM235353
	 */
	public static class TransientData implements Serializable {
		private static final long serialVersionUID = 1L;
		
	    private PeptideSet peptideSet   = null;
	    private BioSequence bioSequence = null;
		private ProteinSet[] proteinSetArray = null;
		
		public TransientData() {
		}
	
		public PeptideSet getPeptideSet() {
			return peptideSet;
		}

		public void setPeptideSet(PeptideSet peptideSet) {
			this.peptideSet = peptideSet;
		}

		public BioSequence getBioSequence() {
			return bioSequence;
		}

		public void setBioSequence(BioSequence bioSequence) {
			this.bioSequence = bioSequence;
		}

		public ProteinSet[] getProteinSetArray() {
			return proteinSetArray;
		}

		public void setProteinSetArray(ProteinSet[] proteinSetArray) {
			this.proteinSetArray = proteinSetArray;
		}
	
	}
	
}
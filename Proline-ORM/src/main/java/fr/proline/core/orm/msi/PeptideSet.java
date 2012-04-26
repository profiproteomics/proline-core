package fr.proline.core.orm.msi;

import java.io.Serializable;
import javax.persistence.*;
import java.util.Set;


/**
 * The persistent class for the peptide_set database table.
 * 
 */
@Entity
@Table(name="peptide_set")
public class PeptideSet implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	@Column(name="is_subset")
	private Boolean isSubset;

	@Column(name="peptide_count")
	private Integer peptideCount;

	@Column(name="peptide_match_count")
	private Integer peptideMatchCount;

	@Column(name="result_summary_id")
	private Integer resultSummaryId;

	@Column(name="serialized_properties")
	private String serializedProperties;

	//bi-directional many-to-one association to ProteinSet
    @ManyToOne
	@JoinColumn(name="protein_set_id")
	private ProteinSet proteinSet;

	//bi-directional many-to-one association to PeptideSetPeptideInstanceItem
	@OneToMany(mappedBy="peptideSet")
	private Set<PeptideSetPeptideInstanceItem> peptideSetPeptideInstanceItems;

	//uni-directional many-to-many association to ProteinMatch
    @ManyToMany
	@JoinTable(
		name="peptide_set_protein_match_map"
		, joinColumns={
			@JoinColumn(name="peptide_set_id")
			}
		, inverseJoinColumns={
			@JoinColumn(name="protein_match_id")
			}
		)
	private Set<ProteinMatch> proteinMatches;

    public PeptideSet() {
    }

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Boolean getIsSubset() {
		return this.isSubset;
	}

	public void setIsSubset(Boolean isSubset) {
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

	public Integer getResultSummaryId() {
		return this.resultSummaryId;
	}

	public void setResultSummaryId(Integer resultSummaryId) {
		this.resultSummaryId = resultSummaryId;
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

	public void setPeptideSetPeptideInstanceItems(Set<PeptideSetPeptideInstanceItem> peptideSetPeptideInstanceItems) {
		this.peptideSetPeptideInstanceItems = peptideSetPeptideInstanceItems;
	}
	
	public Set<ProteinMatch> getProteinMatches() {
		return this.proteinMatches;
	}

	public void setProteinMatches(Set<ProteinMatch> proteinMatches) {
		this.proteinMatches = proteinMatches;
	}
	
}
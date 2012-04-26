package fr.proline.core.orm.msi;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the peptide_set_relation database table.
 * 
 */
@Entity
@Table(name="peptide_set_relation")
public class PeptideSetRelation implements Serializable {
	private static final long serialVersionUID = 1L;

	@EmbeddedId
	private PeptideSetRelationPK id;

	@Column(name="is_strict_subset")
	private Boolean isStrictSubset;

	@ManyToOne
	@JoinColumn(name="result_summary_id")
	private ResultSummary resultSummary;
	
	@ManyToOne
	@JoinColumn(name="peptide_overset_id", insertable=false, updatable=false)
	private PeptideSet overset;

	@ManyToOne
	@JoinColumn(name="peptide_subset_id", insertable=false, updatable=false)
	private PeptideSet subset;

	
    public PeptideSetRelation() {
    }

	public PeptideSetRelationPK getId() {
		return this.id;
	}

	public void setId(PeptideSetRelationPK id) {
		this.id = id;
	}
	
	public Boolean getIsStrictSubset() {
		return this.isStrictSubset;
	}

	public void setIsStrictSubset(Boolean isStrictSubset) {
		this.isStrictSubset = isStrictSubset;
	}

	public ResultSummary getResultSummary() {
		return this.resultSummary;
	}

	public void setResultSummaryId(ResultSummary resultSummary) {
		this.resultSummary = resultSummary;
	}

	public PeptideSet getOverset() {
		return overset;
	}

	public void setOverset(PeptideSet overset) {
		this.overset = overset;
	}

	public PeptideSet getSubset() {
		return subset;
	}

	public void setSubset(PeptideSet subset) {
		this.subset = subset;
	}

}
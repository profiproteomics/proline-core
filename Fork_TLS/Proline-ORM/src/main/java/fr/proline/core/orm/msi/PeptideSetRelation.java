package fr.proline.core.orm.msi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MapsId;
import javax.persistence.Table;

/**
 * The persistent class for the peptide_set_relation database table.
 * 
 */
@Entity
@Table(name = "peptide_set_relation")
public class PeptideSetRelation implements Serializable {
    private static final long serialVersionUID = 1L;

    @EmbeddedId
    private PeptideSetRelationPK id;

    @Column(name = "is_strict_subset")
    private boolean isStrictSubset;

    @ManyToOne
    @JoinColumn(name = "result_summary_id")
    private ResultSummary resultSummary;

    @ManyToOne
    @JoinColumn(name = "peptide_overset_id")
    @MapsId("peptideOversetId")
    private PeptideSet overset;

    @ManyToOne
    @JoinColumn(name = "peptide_subset_id")
    @MapsId("peptideSubsetId")
    private PeptideSet subset;

    public PeptideSetRelation() {
    }

    public PeptideSetRelationPK getId() {
	return this.id;
    }

    public void setId(PeptideSetRelationPK id) {
	this.id = id;
    }

    public boolean getIsStrictSubset() {
	return isStrictSubset;
    }

    public void setIsStrictSubset(final boolean pIsStrictSubset) {
	isStrictSubset = pIsStrictSubset;
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

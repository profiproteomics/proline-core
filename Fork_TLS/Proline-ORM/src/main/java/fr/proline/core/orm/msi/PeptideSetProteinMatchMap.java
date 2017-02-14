package fr.proline.core.orm.msi;

import java.io.Serializable;

import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MapsId;
import javax.persistence.Table;

/**
 * The persistent class for the peptide_set_protein_match_map database table.
 * 
 */
@Entity
@Table(name = "peptide_set_protein_match_map")
public class PeptideSetProteinMatchMap implements Serializable {
    private static final long serialVersionUID = 1L;

    @EmbeddedId
    private PeptideSetProteinMatchMapPK id;

    @ManyToOne
    @JoinColumn(name = "result_summary_id")
    private ResultSummary resultSummary;

    // bi-directional many-to-one association to ProteinMatch
    @ManyToOne
    @JoinColumn(name = "protein_match_id")
    @MapsId("proteinMatchId")
    private ProteinMatch proteinMatch;

    // bi-directional many-to-one association to PeptideSet
    @ManyToOne
    @JoinColumn(name = "peptide_set_id")
    @MapsId("PeptideSetId")
    private PeptideSet peptideSet;

    public PeptideSetProteinMatchMap() {
    }

    public PeptideSetProteinMatchMapPK getId() {
	return this.id;
    }

    public void setId(PeptideSetProteinMatchMapPK id) {
	this.id = id;
    }

    public ResultSummary getResultSummary() {
	return this.resultSummary;
    }

    public void setResultSummary(ResultSummary resultSummary) {
	this.resultSummary = resultSummary;
    }

    public ProteinMatch getProteinMatch() {
	return this.proteinMatch;
    }

    public void setProteinMatch(ProteinMatch proteinMatch) {
	this.proteinMatch = proteinMatch;
    }

    public PeptideSet getPeptideSet() {
	return peptideSet;
    }

    public void setPeptideSet(final PeptideSet pPeptideSet) {
	peptideSet = pPeptideSet;
    }

}

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
 * The persistent class for the peptide_set_peptide_instance_item database table.
 * 
 */
@Entity
@Table(name = "peptide_set_peptide_instance_item")
public class PeptideSetPeptideInstanceItem implements Serializable {

	private static final long serialVersionUID = 1L;

	@EmbeddedId
	private PeptideSetPeptideInstanceItemPK id;

	@Column(name = "is_best_peptide_set")
	private boolean isBestPeptideSet = false;

	@ManyToOne
	@JoinColumn(name = "result_summary_id")
	private ResultSummary resultSummary;

	@Column(name = "selection_level")
	private int selectionLevel;

	@Column(name = "serialized_properties")
	private String serializedProperties;

	// bi-directional many-to-one association to PeptideInstance
	@ManyToOne
	@JoinColumn(name = "peptide_instance_id")
	@MapsId("peptideInstanceId")
	private PeptideInstance peptideInstance;

	// bi-directional many-to-one association to PeptideSet
	@ManyToOne
	@JoinColumn(name = "peptide_set_id")
	@MapsId("peptideSetId")
	private PeptideSet peptideSet;

	public PeptideSetPeptideInstanceItem() {
	}

	public PeptideSetPeptideInstanceItemPK getId() {
		return this.id;
	}

	public void setId(PeptideSetPeptideInstanceItemPK id) {
		this.id = id;
	}

	public boolean getIsBestPeptideSet() {
		return this.isBestPeptideSet;
	}

	public void setIsBestPeptideSet(boolean isBestPeptideSet) {
		this.isBestPeptideSet = isBestPeptideSet;
	}

	public ResultSummary getResultSummary() {
		return this.resultSummary;
	}

	public void setResultSummary(ResultSummary resultSummary) {
		this.resultSummary = resultSummary;
	}

	public int getSelectionLevel() {
		return selectionLevel;
	}

	public void setSelectionLevel(final int pSelectionLevel) {
		selectionLevel = pSelectionLevel;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public PeptideInstance getPeptideInstance() {
		return this.peptideInstance;
	}

	public void setPeptideInstance(PeptideInstance peptideInstance) {
		this.peptideInstance = peptideInstance;
	}

	public PeptideSet getPeptideSet() {
		return this.peptideSet;
	}

	public void setPeptideSet(PeptideSet peptideSet) {
		this.peptideSet = peptideSet;
	}

}

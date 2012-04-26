package fr.proline.core.orm.msi;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the protein_set_protein_match_item database table.
 * 
 */
@Entity
@Table(name="protein_set_protein_match_item")
public class ProteinSetProteinMatchItem implements Serializable {
	private static final long serialVersionUID = 1L;

	@EmbeddedId
	private ProteinSetProteinMatchItemPK id;

	@ManyToOne
	@JoinColumn(name = "result_summary_id")
	private ResultSummary resultSummary;

	@Column(name="serialized_properties")
	private String serializedProperties;

	//bi-directional many-to-one association to ProteinMatch
    @ManyToOne
	@JoinColumn(name="protein_match_id", updatable=false, insertable=false)
	private ProteinMatch proteinMatch;

	//bi-directional many-to-one association to ProteinSet
    @ManyToOne
	@JoinColumn(name="protein_set_id", updatable=false, insertable=false)
	private ProteinSet proteinSet;

    public ProteinSetProteinMatchItem() {
    }

	public ProteinSetProteinMatchItemPK getId() {
		return this.id;
	}

	public void setId(ProteinSetProteinMatchItemPK id) {
		this.id = id;
	}
	
	public ResultSummary getResultSummary() {
		return this.resultSummary;
	}

	public void setResultSummary(ResultSummary resultSummary) {
		this.resultSummary = resultSummary;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public ProteinMatch getProteinMatch() {
		return this.proteinMatch;
	}

	public void setProteinMatch(ProteinMatch proteinMatch) {
		this.proteinMatch = proteinMatch;
	}
	
	public ProteinSet getProteinSet() {
		return this.proteinSet;
	}

	public void setProteinSet(ProteinSet proteinSet) {
		this.proteinSet = proteinSet;
	}
	
}
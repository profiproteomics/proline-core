package fr.proline.core.orm.msi;

import java.io.Serializable;
import javax.persistence.*;

/**
 * The persistent class for the peptide_match_relation database table.
 * 
 */
@Entity
@Table(name = "peptide_match_relation")
public class PeptideMatchRelation implements Serializable {
	private static final long serialVersionUID = 1L;

	@EmbeddedId
	private PeptideMatchRelationPK id;

	@ManyToOne
	@JoinColumn(name = "parent_result_set_id")
	private ResultSet parentResultSet;

	@ManyToOne
	@JoinColumn(name = "parent_peptide_match_id")
	@MapsId("parentPeptideMatchId")
	private PeptideMatch parentPeptideMatch;

	@ManyToOne
	@JoinColumn(name = "child_peptide_match_id")
	@MapsId("childPeptideMatchId")
	private PeptideMatch childPeptideMatch;

	public PeptideMatchRelation() {
	}

	public PeptideMatchRelationPK getId() {
		return this.id;
	}

	public void setId(PeptideMatchRelationPK id) {
		this.id = id;
	}

	public ResultSet getParentResultSet() {
		return this.parentResultSet;
	}

	public void setParentResultSetId(ResultSet parentResultSet) {
		this.parentResultSet = parentResultSet;
	}

	public PeptideMatch getParentPeptideMatch() {
		return parentPeptideMatch;
	}

	public void setParentPeptideMatch(PeptideMatch parentPeptideMatch) {
		this.parentPeptideMatch = parentPeptideMatch;
	}

	public PeptideMatch getChildPeptideMatch() {
		return childPeptideMatch;
	}

	public void setChildPeptideMatch(PeptideMatch childPeptideMatch) {
		this.childPeptideMatch = childPeptideMatch;
	}

}
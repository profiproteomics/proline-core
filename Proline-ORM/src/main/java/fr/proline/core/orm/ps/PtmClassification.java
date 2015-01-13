package fr.proline.core.orm.ps;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

import fr.profi.util.StringUtils;

/**
 * The persistent class for the ptm_classification database table.
 * 
 */
@Entity
@NamedQuery(name = "findPtmClassificationForName", query = "select pc from fr.proline.core.orm.ps.PtmClassification pc"
	+ " where upper(pc.name) = :name")
@Table(name = "ptm_classification")
public class PtmClassification implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@Column(name = "id")
	private long id;

	private String name;

	public PtmClassification() {
	}

	public long getId() {
		return id;
	}

	public void setId(final long pId) {
		id = pId;
	}

	public PtmClassificationName getName() {
		return PtmClassificationName.valueOf(this.name);
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setName(PtmClassificationName name) {
		this.name = name.toString();
	}
    
	public enum PtmClassificationName {
		
		UNKNOWN("-"),
		POST_TRANSLATIONAL("Post-translational"),
		CO_TRANSLATIONAL("Co-translational"),
		PRE_TRANSLATIONAL("Pre-translational"),
		CHEMICAL_DERIVATIVE("Chemical derivative"),
		ARTEFACT("Artefact"),
		N_LINKED_GLYCOSYLATION("N-linked glycosylation"),
		O_LINKED_GLYCOSYLATION("O-linked glycosylation"),
		OTHER_GLYCOSYLATION("Other glycosylation"),
		SYNTH_PEP_PROTECT_GP("Synth. pep. protect. gp."),
		ISOTOPIC_LABEL("Isotopic label"),
		NON_STANDARD_RESIDUE("Non-standard residue"),
		MULTIPLE("Multiple"),
		OTHER("Other"),
		AA_SUBSTITUTION("AA substitution")
		;

		private final String m_name;

		private PtmClassificationName(final String name) {
			assert (!StringUtils.isEmpty(name)) : "Classification.Name() invalid name";

			m_name = name;
		}
		
		@Override
		public String toString() {
			return m_name;
		}

	}

}

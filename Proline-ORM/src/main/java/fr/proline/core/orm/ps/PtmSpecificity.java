package fr.proline.core.orm.ps;

import static javax.persistence.CascadeType.PERSIST;

import java.io.Serializable;
import java.util.HashSet;
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

import fr.profi.util.StringUtils;

/**
 * The persistent class for the ptm_specificity database table.
 * 
 */
@Entity(name = "fr.proline.core.orm.ps.PtmSpecificity")
@NamedQueries({
	@NamedQuery(name = "findPsPtmSpecForNameLocResidue", query = "select ps from fr.proline.core.orm.ps.PtmSpecificity ps"
		+ " where (upper(ps.location) = :location) and (ps.residue = :residue) and (upper(ps.ptm.shortName) = :ptmShortName)"),

	@NamedQuery(name = "findPsPtmSpecForNameAndLoc", query = "select ps from fr.proline.core.orm.ps.PtmSpecificity ps"
		+ " where (upper(ps.location) = :location) and (ps.residue is null) and (upper(ps.ptm.shortName) = :ptmShortName)")

})
@Table(name = "ptm_specificity")
public class PtmSpecificity implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@Column(name = "id")
	private long id;

	private String location;

	private Character residue;

	// bi-directional many-to-one association to Ptm
	@ManyToOne
	@JoinColumn(name = "ptm_id")
	private Ptm ptm;

	// uni-directional many-to-one association to PtmClassification
	@ManyToOne(cascade = PERSIST)
	@JoinColumn(name = "classification_id")
	private PtmClassification classification;

	@OneToMany(mappedBy = "specificity", cascade = PERSIST)
	private Set<PtmEvidence> evidences;

	public PtmSpecificity() {
	}

	public long getId() {
		return id;
	}

	public void setId(final long pId) {
		id = pId;
	}

	public PtmLocation getLocation() {
		return PtmLocation.valueOf(this.location);
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public void setLocation(PtmLocation location) {
		this.location = location.toString();
	}

	public Character getResidue() {
		return residue;
	}

	public void setResidue(final Character pResidue) {
		residue = pResidue;
	}

	public Ptm getPtm() {
		return this.ptm;
	}

	public void setPtm(Ptm ptm) {
		this.ptm = ptm;
	}

	public PtmClassification getClassification() {
		return this.classification;
	}

	public void setClassification(PtmClassification classification) {
		this.classification = classification;
	}

	public void setEvidences(final Set<PtmEvidence> pEvidences) {
		evidences = pEvidences;
	}

	public Set<PtmEvidence> getEvidences() {
		return evidences;
	}

	public void addEvidence(final PtmEvidence evidence) {

		if (evidence != null) {
			Set<PtmEvidence> localEvidences = getEvidences();

			if (localEvidences == null) {
				localEvidences = new HashSet<PtmEvidence>();

				setEvidences(localEvidences);
			}

			localEvidences.add(evidence);
		}

	}

	public void removeEvidence(final PtmEvidence evidence) {

		final Set<PtmEvidence> localEvidences = getEvidences();
		if (localEvidences != null) {
			localEvidences.remove(evidence);
		}

	}

	public enum PtmLocation {

		ANYWHERE("Anywhere"),
		ANY_N_TERM("Any N-term"),
		ANY_C_TERM("Any C-term"),
		PROT_N_TERM("Protein N-term"),
		PROT_C_TERM("Protein C-term");

		private final String m_location;

		private PtmLocation(final String location) {
			assert (!StringUtils.isEmpty(location)) : "PtmSpecificity.Location() invalid name";

			m_location = location;
		}

		@Override
		public String toString() {
			return m_location;
		}
	}

}

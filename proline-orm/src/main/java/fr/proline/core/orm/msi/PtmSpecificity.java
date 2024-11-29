package fr.proline.core.orm.msi;

import static javax.persistence.CascadeType.PERSIST;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;

import fr.profi.util.StringUtils;

/**
 * The persistent class for the ptm_specificity database table.
 * 
 */
@Entity(name = "fr.proline.core.orm.msi.PtmSpecificity")
@NamedQueries({
	@NamedQuery(name = "findMsiPtmSpecForNameLocResidue", query = "select ps from fr.proline.core.orm.msi.PtmSpecificity ps"
		+ " where (upper(ps.location) = :location) and (ps.residue = :residue) and (upper(ps.ptm.shortName) = :ptmShortName)"),

	@NamedQuery(name = "findMsiPtmSpecForNameAndLoc", query = "select ps from fr.proline.core.orm.msi.PtmSpecificity ps"
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

	@Column(name = "serialized_properties")
	private String serializedProperties;

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

	// bi-directional many-to-one association to UsedPtm
	@OneToMany(mappedBy = "ptmSpecificity")
	private Set<UsedPtm> usedPtms;

	public PtmSpecificity() {
	}

	public long getId() {
		return id;
	}

	public void setId(final long pId) {
		id = pId;
	}

	public String getLocation() {
		return this.location;
	}

	public void setLocation(String location) {
		this.location = location;
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
		private static HashMap<String, PtmLocation> ptmLocationByLocation = null;

		private PtmLocation(final String location) {
			assert (!StringUtils.isEmpty(location)) : "PtmSpecificity.Location() invalid location";

			m_location = location;
		}

		public static PtmLocation withName(final String location) {
			if (ptmLocationByLocation == null) {
				ptmLocationByLocation = new HashMap<String, PtmLocation>();

				for (PtmLocation ptmLoc : PtmLocation.values()) {
					ptmLocationByLocation.put(ptmLoc.toString(), ptmLoc);
				}
			}

			return ptmLocationByLocation.get(location);
		}

		@Override
		public String toString() {
			return m_location;
		}
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public Set<UsedPtm> getUsedPtms() {
		return this.usedPtms;
	}

	public void setUsedPtms(final Set<UsedPtm> pUsedPtms) {
		usedPtms = pUsedPtms;
	}

	public void addUsedPtm(final UsedPtm usedPtm) {

		if (usedPtm != null) {
			Set<UsedPtm> localUsedPtms = getUsedPtms();

			if (localUsedPtms == null) {
				localUsedPtms = new HashSet<UsedPtm>();

				setUsedPtms(localUsedPtms);
			}

			localUsedPtms.add(usedPtm);
		}

	}

	public void removeUsedPtms(final UsedPtm usedPtm) {

		final Set<UsedPtm> localUsedPtms = getUsedPtms();
		if (localUsedPtms != null) {
			localUsedPtms.remove(usedPtm);
		}

	}
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(ptm.getShortName());
		if(!PtmLocation.withName(location).equals(PtmLocation.ANYWHERE)) {
			sb.append(" (").append(location).append(")");
		}
		if( residue != null && residue != '\u0000' ) {
			sb.append(" (").append(residue).append(")");
		}
		return sb.toString();
	}
}
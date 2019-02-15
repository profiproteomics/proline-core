package fr.proline.core.orm.msi;

import static javax.persistence.CascadeType.PERSIST;
import static javax.persistence.CascadeType.REMOVE;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;

/**
 * The persistent class for the ptm database table.
 * 
 */
@Entity
@NamedQueries({
	@NamedQuery(name = "findMsiPtmForName", query = "select p from fr.proline.core.orm.msi.Ptm p"
		+ " where (upper(p.shortName) = :name) or (upper(p.fullName) = :name)"),
	@NamedQuery(name = "findMsiPtmForShortName", query = "select p from fr.proline.core.orm.msi.Ptm p"
		+ " where (upper(p.shortName) = :name)")
})
public class Ptm implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@Column(name = "id")
	private long id;

	@Column(name = "full_name")
	private String fullName;

	@Column(name = "serialized_properties")
	private String serializedProperties;

	@Column(name = "short_name")
	private String shortName;

	/* Unimod record_id is optional and of type : xs:long */
	@Column(name = "unimod_id")
	private Long unimodId;

	// bi-directional many-to-one association to PtmEvidence
	@OneToMany(mappedBy = "ptm", cascade = { PERSIST, REMOVE })
	private Set<PtmEvidence> evidences;

	// bi-directional many-to-one association to PtmSpecificity
	@OneToMany(mappedBy = "ptm", cascade = { PERSIST, REMOVE })
	private Set<PtmSpecificity> specificities;

	public Ptm() {
	}

	public long getId() {
		return id;
	}

	public void setId(final long pId) {
		id = pId;
	}

	public String getFullName() {
		return this.fullName;
	}

	public void setFullName(String fullName) {
		this.fullName = fullName;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public String getShortName() {
		return this.shortName;
	}

	public void setShortName(String shortName) {
		this.shortName = shortName;
	}

	public Long getUnimodId() {
		return unimodId;
	}

	public void setUnimodId(final Long pUnimodId) {
		unimodId = pUnimodId;
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

	public void setSpecificities(final Set<PtmSpecificity> specs) {
		specificities = specs;
	}

	public Set<PtmSpecificity> getSpecificities() {
		return specificities;
	}

	public void addSpecificity(final PtmSpecificity specificity) {

		if (specificity != null) {
			Set<PtmSpecificity> localSpecificities = getSpecificities();

			if (localSpecificities == null) {
				localSpecificities = new HashSet<PtmSpecificity>();

				setSpecificities(localSpecificities);
			}

			localSpecificities.add(specificity);
		}

	}

	public void removeSpecificity(final PtmSpecificity specificity) {

		final Set<PtmSpecificity> localSpecificities = getSpecificities();
		if (localSpecificities != null) {
			localSpecificities.remove(specificity);
		}

	}

}

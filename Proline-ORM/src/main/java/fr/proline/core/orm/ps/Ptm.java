package fr.proline.core.orm.ps;

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
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;


/**
 * The persistent class for the ptm database table.
 * 
 */
@Entity
@NamedQuery(name="findPtmByName",
			query="select p from Ptm p where UPPER(p.shortName) = :name or UPPER(p.fullName) = :name")
public class Ptm implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	@Column(name="id")
	private Integer id;

	@Column(name="full_name")
	private String fullName;

	@Column(name="serialized_properties")
	private String serializedProperties;

	@Column(name="short_name")
	private String shortName;

	@Column(name="unimod_id")
	private Integer unimodId;

	//bi-directional many-to-one association to PtmEvidence
	@OneToMany(mappedBy="ptm", cascade = { PERSIST, REMOVE })
	private Set<PtmEvidence> evidences;

	//bi-directional many-to-one association to PtmSpecificity
	@OneToMany(mappedBy="ptm", cascade = { PERSIST, REMOVE })
	private Set<PtmSpecificity> specificities;

    public Ptm() {
    }

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
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

	public Integer getUnimodId() {
		return this.unimodId;
	}

	public void setUnimodId(Integer unimodId) {
		this.unimodId = unimodId;
	}

	public Set<PtmEvidence> getEvidences() {
		return this.evidences;
	}

	public boolean addEvidence(PtmEvidence evidence) {
		if (this.evidences == null)
			this.evidences = new HashSet<PtmEvidence>();
		return this.evidences.add(evidence);
	}
	
	public Set<PtmSpecificity> getSpecificities() {
		return this.specificities;
	}

	public boolean addSpecificity(PtmSpecificity specificity) {
		if (specificities == null)
			specificities = new HashSet<PtmSpecificity>();
		return specificities.add(specificity);
	}
	
}
package fr.proline.core.orm.msi;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import fr.proline.core.orm.utils.StringUtils;

/**
 * The persistent class for the ptm_specificity database table.
 * 
 */
@Entity(name = "fr.proline.core.orm.msi.PtmSpecificity")
@Table(name = "ptm_specificity")
public class PtmSpecificity implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    // Msi PtmSpecificity Id are not generated (taken from Ps PtmSpecificity entity)
    private Integer id;

    private String location;

    private String residue;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    // bi-directional many-to-one association to UsedPtm
    @OneToMany(mappedBy = "ptmSpecificity")
    private Set<UsedPtm> usedPtms;

    /**
     * Create a Msi PtmSpecificity entity from a Ps PtmSpecificity entity. Created Msi PtmSpecificity entity
     * shares the same Id with given Ps PtmSpecificity.
     * 
     * @param psPtmSpecificity
     *            PtmSpecificity entity from psDb used to initialize Msi PtmSpecificity fields (must not be
     *            <code>null</code>)
     */
    public PtmSpecificity(final fr.proline.core.orm.ps.PtmSpecificity psPtmSpecificity) {

	if (psPtmSpecificity == null) {
	    throw new IllegalArgumentException("PsPtmSpecificity is null");
	}

	setId(psPtmSpecificity.getId());
	setLocation(psPtmSpecificity.getLocation());

	final String residue = psPtmSpecificity.getResidue();

	if (StringUtils.isEmpty(residue)) {
	    setResidue(null);
	} else {
	    setResidue(residue);
	}

    }

    public PtmSpecificity() {
    }

    public Integer getId() {
	return this.id;
    }

    public void setId(Integer id) {
	this.id = id;
    }

    public String getLocation() {
	return this.location;
    }

    public void setLocation(String location) {
	this.location = location;
    }

    public String getResidue() {
	return this.residue;
    }

    public void setResidue(String residue) {
	this.residue = residue;
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

    public void setUsedPtms(final Set<UsedPtm> usedPtms) {
	this.usedPtms = usedPtms;
    }

    public void addUsedPtm(final UsedPtm usedPtm) {

	if (usedPtm != null) {
	    Set<UsedPtm> ptms = getUsedPtms();

	    if (ptms == null) {
		ptms = new HashSet<UsedPtm>();

		setUsedPtms(ptms);
	    }

	    ptms.add(usedPtm);
	}

    }

    public void removeUsedPtms(final UsedPtm usedPtm) {
	final Set<UsedPtm> ptms = getUsedPtms();

	if (ptms != null) {
	    ptms.remove(usedPtm);
	}

    }

}
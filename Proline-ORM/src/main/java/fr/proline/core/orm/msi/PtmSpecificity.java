package fr.proline.core.orm.msi;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;

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
    private long id;

    private String location;

    private Character residue;

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
	psPtmSpecificity.getResidue();

	// TODO set SerializedProperties when fields is available in PS PtmSpecificity
    }

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

}
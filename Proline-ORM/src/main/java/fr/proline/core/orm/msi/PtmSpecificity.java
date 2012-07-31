package fr.proline.core.orm.msi;

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
import javax.persistence.Table;

/**
 * The persistent class for the ptm_specificity database table.
 * 
 */
@Entity(name = "fr.proline.core.orm.msi.PtmSpecificity")
@NamedQuery(name = "findPtmSpecByLocAndResid", query = "select ps from fr.proline.core.orm.msi.PtmSpecificity ps"
	+ " where (lower(ps.location) = :loc) and (lower(ps.residue) = :resid)")
@Table(name = "ptm_specificity")
public class PtmSpecificity implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;

    private String location;

    private String residue;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    // bi-directional many-to-one association to UsedPtm
    @OneToMany(mappedBy = "ptmSpecificity")
    private Set<UsedPtm> usedPtms;

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
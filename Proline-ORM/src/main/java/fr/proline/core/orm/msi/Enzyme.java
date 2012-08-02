package fr.proline.core.orm.msi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.NamedQuery;

/**
 * The persistent class for the enzyme database table.
 * 
 */
@Entity(name = "fr.proline.core.orm.msi.Enzyme")
@NamedQuery(name = "findMsiEnzymeByName", query = "select e from fr.proline.core.orm.msi.Enzyme e"
	+ " where lower(e.name) = :name")
public class Enzyme implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    // MSI Enzyme Id are not generated (taken from Uds Enzyme entity)
    private Integer id;

    @Column(name = "cleavage_regexp")
    private String cleavageRegexp;

    @Column(name = "is_independant")
    private Boolean isIndependant;

    @Column(name = "is_semi_specific")
    private Boolean isSemiSpecific;

    private String name;

    public Enzyme() {
    }

    /**
     * Create a Msi Enzyme entity from an Uds Enzyme entity. Created Msi Enzyme entity shares the same Id with
     * given Uds Enzyme.
     * 
     * @param udsEnzyme
     *            Enzyme entity from udsDb used to initialize Msi Enzyme fields (must not be <code>null</code>
     *            )
     */
    public Enzyme(final fr.proline.core.orm.uds.Enzyme udsEnzyme) {

	if (udsEnzyme == null) {
	    throw new IllegalArgumentException("UdsEnzyme is null");
	}

	setId(udsEnzyme.getId());
	setCleavageRegexp(udsEnzyme.getCleavageRegexp());
	setIsIndependant(udsEnzyme.getIsIndependant());
	setIsSemiSpecific(udsEnzyme.getIsSemiSpecific());
	setName(udsEnzyme.getName());
    }

    public Integer getId() {
	return this.id;
    }

    public void setId(Integer id) {
	this.id = id;
    }

    public String getCleavageRegexp() {
	return this.cleavageRegexp;
    }

    public void setCleavageRegexp(String cleavageRegexp) {
	this.cleavageRegexp = cleavageRegexp;
    }

    public Boolean getIsIndependant() {
	return this.isIndependant;
    }

    public void setIsIndependant(Boolean isIndependant) {
	this.isIndependant = isIndependant;
    }

    public Boolean getIsSemiSpecific() {
	return this.isSemiSpecific;
    }

    public void setIsSemiSpecific(Boolean isSemiSpecific) {
	this.isSemiSpecific = isSemiSpecific;
    }

    public String getName() {
	return this.name;
    }

    public void setName(String name) {
	this.name = name;
    }

}
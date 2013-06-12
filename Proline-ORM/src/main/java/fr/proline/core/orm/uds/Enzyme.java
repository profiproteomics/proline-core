package fr.proline.core.orm.uds;

import java.io.Serializable;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;

/**
 * The persistent class for the enzyme database table.
 * 
 */
@Entity(name = "fr.proline.core.orm.uds.Enzyme")
@NamedQuery(name = "findUdsEnzymeForName", query = "select e from fr.proline.core.orm.uds.Enzyme e"
	+ " where upper(e.name) = :name")
public class Enzyme implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column(name = "cleavage_regexp")
    private String cleavageRegexp;

    @Column(name = "is_independant")
    private boolean isIndependant;

    @Column(name = "is_semi_specific")
    private boolean isSemiSpecific;

    private String name;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    // bi-directional many-to-one association to EnzymeCleavage
    @OneToMany(mappedBy = "enzyme")
    private Set<EnzymeCleavage> cleavages;

    public Enzyme() {
    }

    public long getId() {
	return id;
    }

    public void setId(final long pId) {
	id = pId;
    }

    public String getCleavageRegexp() {
	return this.cleavageRegexp;
    }

    public void setCleavageRegexp(String cleavageRegexp) {
	this.cleavageRegexp = cleavageRegexp;
    }

    public boolean getIsIndependant() {
	return isIndependant;
    }

    public void setIsIndependant(final boolean pIsIndependant) {
	isIndependant = pIsIndependant;
    }

    public boolean getIsSemiSpecific() {
	return isSemiSpecific;
    }

    public void setIsSemiSpecific(final boolean pIsSemiSpecific) {
	isSemiSpecific = pIsSemiSpecific;
    }

    public String getName() {
	return this.name;
    }

    public void setName(String name) {
	this.name = name;
    }

    public String getSerializedProperties() {
	return this.serializedProperties;
    }

    public void setSerializedProperties(String serializedProperties) {
	this.serializedProperties = serializedProperties;
    }

    public Set<EnzymeCleavage> getCleavages() {
	return this.cleavages;
    }

    public void setCleavages(Set<EnzymeCleavage> cleavages) {
	this.cleavages = cleavages;
    }

}

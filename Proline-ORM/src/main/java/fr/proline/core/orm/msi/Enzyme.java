package fr.proline.core.orm.msi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQuery;

/**
 * The persistent class for the enzyme database table.
 * 
 */
@Entity(name = "fr.proline.core.orm.msi.Enzyme")
@NamedQuery(name = "findMsiEnzymeByName", query = "select e from fr.proline.core.orm.msi.Enzyme e where lower(e.name) = :enzymeName")
public class Enzyme implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
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
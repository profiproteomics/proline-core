package fr.proline.core.orm.pdi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * The persistent class for the taxon_extra_name database table.
 * 
 */
@Entity
@Table(name = "taxon_extra_name")
public class TaxonExtraName implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column(name = "class")
    private String clazz;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    private String value;

    // bi-directional many-to-one association to Taxon
    @ManyToOne
    private Taxon taxon;

    public TaxonExtraName() {
    }

    public long getId() {
	return id;
    }

    public void setId(final long pId) {
	id = pId;
    }

    public String getClazz() {
	return clazz;
    }

    public void setClazz(final String pClass) {
	clazz = pClass;
    }

    public String getSerializedProperties() {
	return this.serializedProperties;
    }

    public void setSerializedProperties(String serializedProperties) {
	this.serializedProperties = serializedProperties;
    }

    public String getValue() {
	return this.value;
    }

    public void setValue(String value) {
	this.value = value;
    }

    public Taxon getTaxon() {
	return this.taxon;
    }

    public void setTaxon(Taxon taxon) {
	this.taxon = taxon;
    }

    @Override
    public String toString() {
	return new ToStringBuilder(this).append("extra name", getValue()).append("class", getClazz())
		.toString();
    }

}

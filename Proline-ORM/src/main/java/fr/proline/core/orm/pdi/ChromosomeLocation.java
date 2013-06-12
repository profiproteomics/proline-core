package fr.proline.core.orm.pdi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

/**
 * The persistent class for the chromosome_location database table.
 * 
 */
@Entity
@Table(name = "chromosome_location")
public class ChromosomeLocation implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column(name = "chromosome_identifier")
    private String chromosomeIdentifier;

    private String location;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    // bi-directional many-to-one association to Gene
    @ManyToOne
    private Gene gene;

    // uni-directional many-to-one association to Taxon
    @ManyToOne
    private Taxon taxon;

    public ChromosomeLocation() {
    }

    public long getId() {
	return id;
    }

    public void setId(final long pId) {
	id = pId;
    }

    public String getChromosomeIdentifier() {
	return this.chromosomeIdentifier;
    }

    public void setChromosomeIdentifier(String chromosomeIdentifier) {
	this.chromosomeIdentifier = chromosomeIdentifier;
    }

    public String getLocation() {
	return this.location;
    }

    public void setLocation(String location) {
	this.location = location;
    }

    public String getSerializedProperties() {
	return this.serializedProperties;
    }

    public void setSerializedProperties(String serializedProperties) {
	this.serializedProperties = serializedProperties;
    }

    public Gene getGene() {
	return this.gene;
    }

    public void setGene(Gene gene) {
	this.gene = gene;
    }

    public Taxon getTaxon() {
	return this.taxon;
    }

    public void setTaxon(Taxon taxon) {
	this.taxon = taxon;
    }

}

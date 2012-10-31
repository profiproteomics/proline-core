package fr.proline.core.orm.pdi;

import java.io.Serializable;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;

/**
 * The persistent class for the gene database table.
 * 
 */

@Entity
@NamedQueries({
	@NamedQuery(name = "findGeneForNameAndTaxon", query = "select ge from fr.proline.core.orm.pdi.Gene ge"
		+ " where (upper(ge.name) = :name) and (ge.taxon.id = :taxonId)"),

	@NamedQuery(name = "findGenesForNames", query = "select distinct ge from fr.proline.core.orm.pdi.Gene ge"
		+ " where upper(ge.name) in :names")

})
public class Gene implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;

    @Column(name = "is_active")
    private Boolean isActive;

    private String name;

    @Column(name = "orf_names")
    private String orfNames;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    private String synonyms;

    // bi-directional many-to-one association to ChromosomeLocation
    @OneToMany(mappedBy = "gene")
    private Set<ChromosomeLocation> chromosomeLocations;

    // uni-directional many-to-one association to Taxon
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "taxon_id", nullable = false)
    private Taxon taxon;

    public Gene() {
    }

    public Integer getId() {
	return this.id;
    }

    public void setId(Integer id) {
	this.id = id;
    }

    public Boolean getIsActive() {
	return this.isActive;
    }

    public void setIsActive(Boolean isActive) {
	this.isActive = isActive;
    }

    public String getName() {
	return this.name;
    }

    public void setName(String name) {
	this.name = name;
    }

    public String getOrfNames() {
	return this.orfNames;
    }

    public void setOrfNames(String orfNames) {
	this.orfNames = orfNames;
    }

    public String getSerializedProperties() {
	return this.serializedProperties;
    }

    public void setSerializedProperties(String serializedProperties) {
	this.serializedProperties = serializedProperties;
    }

    public String getSynonyms() {
	return this.synonyms;
    }

    public void setSynonyms(String synonyms) {
	this.synonyms = synonyms;
    }

    public Set<ChromosomeLocation> getChromosomeLocations() {
	return this.chromosomeLocations;
    }

    public void setChromosomeLocations(Set<ChromosomeLocation> chromosomeLocations) {
	this.chromosomeLocations = chromosomeLocations;
    }

    public Taxon getTaxon() {
	return this.taxon;
    }

    public void setTaxon(Taxon taxon) {
	this.taxon = taxon;
    }

}
package fr.proline.core.orm.pdi;

import java.io.Serializable;
import javax.persistence.*;

import java.util.Set;


/**
 * The persistent class for the gene database table.
 * 
 */

@Entity

@NamedQuery(name="findGeneByNameAndTaxon",
query="select g from Gene g where g.name = :name and g.taxonId = :taxid")

public class Gene implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	@Column(name="is_active")
	private Boolean isActive;

	private String name;

	@Column(name="orf_names")
	private String orfNames;

	@Column(name="serialized_properties")
	private String serializedProperties;

	private String synonyms;

	//bi-directional many-to-one association to ChromosomeLocation
	@OneToMany(mappedBy="gene")
	private Set<ChromosomeLocation> chromosomeLocations;

	// uni-directional many-to-one association to Taxon
	@ManyToOne
	@JoinColumn(name = "taxon_id", insertable = false, updatable = false)
	private Taxon taxon;

	@Column(name = "taxon_id")
	private Integer taxonId;

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

	public Integer getTaxonId() {
		return taxonId;
	}

	public void setTaxonId(Integer taxonId) {
		this.taxonId = taxonId;
	}
	
}
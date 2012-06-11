package fr.proline.core.orm.pdi;

import static javax.persistence.CascadeType.PERSIST;
import static javax.persistence.CascadeType.REMOVE;

import java.io.Serializable;
import javax.persistence.*;

import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.HashSet;
import java.util.Set;


/**
 * The persistent class for the taxon database table.
 * 
 */
@Entity
public class Taxon implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	private Integer id;

	private String rank;

	@Column(name="scientific_name")
	private String scientificName;

	@Column(name="serialized_properties")
	private String serializedProperties;

	//bi-directional many-to-one association to Taxon
    @ManyToOne
	@JoinColumn(name="parent_taxon_id")
	private Taxon parentTaxon;

	//bi-directional many-to-one association to Taxon
	@OneToMany(mappedBy="parentTaxon")
	private Set<Taxon> children;

	//bi-directional many-to-one association to TaxonExtraName
	@OneToMany(mappedBy="taxon", cascade = { PERSIST, REMOVE })
	private Set<TaxonExtraName> taxonExtraNames;

	protected Taxon() {
		
	}
	
    public Taxon(Integer id) {
   	 this.id = id;
    }

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getRank() {
		return this.rank;
	}

	public void setRank(String rank) {
		this.rank = rank;
	}

	public String getScientificName() {
		return this.scientificName;
	}

	public void setScientificName(String scientificName) {
		this.scientificName = scientificName;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public Taxon getParentTaxon() {
		return this.parentTaxon;
	}

	public void setParentTaxon(Taxon parentTaxon) {
		this.parentTaxon = parentTaxon;
	}
	
	public Set<Taxon> getChildren() {
		return this.children;
	}

	public boolean addChildTaxon(Taxon taxon) {
		if (children == null)
			this.children = new HashSet<Taxon>();
		return this.children.add(taxon);
	}
	
	public Set<TaxonExtraName> getTaxonExtraNames() {
		return this.taxonExtraNames;
	}

	public boolean addTaxonExtraName(TaxonExtraName extraName) {
		if (this.taxonExtraNames == null)
			this.taxonExtraNames = new HashSet<TaxonExtraName>();
		return this.taxonExtraNames.add(extraName);
	}
	
	@Override
	public String toString() {
		return new ToStringBuilder(this).append("id", id).append("scientific name", scientificName).append("rank", rank).append("parent_id", parentTaxon.id).toString();
	}
}
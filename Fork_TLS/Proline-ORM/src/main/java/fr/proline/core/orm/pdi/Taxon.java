package fr.proline.core.orm.pdi;

import static javax.persistence.CascadeType.PERSIST;
import static javax.persistence.CascadeType.REMOVE;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;

import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * The persistent class for the taxon database table.
 * 
 */
@Entity
@NamedQuery(name = "findTaxonsForIds", query = "select tax from fr.proline.core.orm.pdi.Taxon tax"
	+ " where tax.id in :ids")
public class Taxon implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private long id;

    @Column(name = "is_active")
    private boolean isActive;

    private String rank;

    @Column(name = "scientific_name")
    private String scientificName;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    // bi-directional many-to-one association to Taxon
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_taxon_id")
    private Taxon parentTaxon;

    // bi-directional many-to-one association to Taxon
    @OneToMany(mappedBy = "parentTaxon")
    private Set<Taxon> children;

    // bi-directional many-to-one association to TaxonExtraName
    @OneToMany(mappedBy = "taxon", cascade = { PERSIST, REMOVE })
    private Set<TaxonExtraName> taxonExtraNames;

    protected Taxon() {
    }

    public Taxon(final long pId) {
	setId(pId);
	setIsActive(true); // Default to true
    }

    public long getId() {
	return id;
    }

    public void setId(final long pId) {
	id = pId;
    }

    public boolean getIsActive() {
	return isActive;
    }

    public void setIsActive(final boolean pIsActive) {
	isActive = pIsActive;
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

    public void setChildren(final Set<Taxon> pChildren) {
	children = pChildren;
    }

    public Set<Taxon> getChildren() {
	return children;
    }

    public void addChildTaxon(final Taxon taxon) {

	if (taxon != null) {
	    Set<Taxon> localChildren = getChildren();

	    if (localChildren == null) {
		localChildren = new HashSet<Taxon>();

		setChildren(localChildren);
	    }

	    localChildren.add(taxon);
	}

    }

    public void removeChildTaxon(final Taxon taxon) {

	final Set<Taxon> localChildren = getChildren();
	if (localChildren != null) {
	    localChildren.remove(taxon);
	}

    }

    public void setTaxonExtraNames(final Set<TaxonExtraName> extraNames) {
	taxonExtraNames = extraNames;
    }

    public Set<TaxonExtraName> getTaxonExtraNames() {
	return taxonExtraNames;
    }

    public void addTaxonExtraName(final TaxonExtraName extraName) {

	if (extraName != null) {
	    Set<TaxonExtraName> extraNames = getTaxonExtraNames();

	    if (extraNames == null) {
		extraNames = new HashSet<TaxonExtraName>();

		setTaxonExtraNames(extraNames);
	    }

	    extraNames.add(extraName);
	}

    }

    public void removeTaxonExtraName(final TaxonExtraName extraName) {

	final Set<TaxonExtraName> extraNames = getTaxonExtraNames();
	if (extraNames != null) {
	    extraNames.remove(extraName);
	}

    }

    @Override
    public String toString() {
	return new ToStringBuilder(this).append("id", getId()).append("scientific name", getScientificName())
		.append("rank", getRank()).append("parent_id", getParentTaxon().getId()).toString();
    }

}

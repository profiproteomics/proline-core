package fr.proline.core.orm.pdi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MapsId;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

/**
 * The persistent class for the bio_sequence_gene_map database table.
 * 
 */
@Entity
@NamedQuery(name = "findBioSequenceGeneMapsForGenes", query = "select distinct bsgm from fr.proline.core.orm.pdi.BioSequenceGeneMap bsgm"
	+ " where bsgm.gene in :genes")
@Table(name = "bio_sequence_gene_map")
public class BioSequenceGeneMap implements Serializable {

    private static final long serialVersionUID = 1L;

    @EmbeddedId
    private BioSequenceGeneMapPK id;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    // uni-directional many-to-one association to BioSequence
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bio_sequence_id")
    @MapsId("bioSequenceId")
    private BioSequence bioSequence;

    // uni-directional many-to-one association to Gene
    @ManyToOne
    @JoinColumn(name = "gene_id")
    @MapsId("geneId")
    private Gene gene;

    // uni-directional many-to-one association to Taxon
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "taxon_id")
    private Taxon taxon;

    public BioSequenceGeneMap() {
	this.id = new BioSequenceGeneMapPK();
    }

    public BioSequenceGeneMapPK getId() {
	return this.id;
    }

    public void setId(BioSequenceGeneMapPK id) {
	this.id = id;
    }

    public String getSerializedProperties() {
	return this.serializedProperties;
    }

    public void setSerializedProperties(String serializedProperties) {
	this.serializedProperties = serializedProperties;
    }

    public BioSequence getBioSequence() {
	return this.bioSequence;
    }

    public void setBioSequence(BioSequence bioSequence) {
	this.bioSequence = bioSequence;
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
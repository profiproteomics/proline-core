package fr.proline.core.orm.pdi;

import java.io.Serializable;
import javax.persistence.*;

/**
 * The persistent class for the bio_sequence_gene_map database table.
 * 
 */
@Entity
@Table(name = "bio_sequence_gene_map")
public class BioSequenceGeneMap implements Serializable {
	private static final long serialVersionUID = 1L;

	@EmbeddedId
	private BioSequenceGeneMapPK id;

	@Column(name = "serialized_properties")
	private String serializedProperties;

	// uni-directional many-to-one association to BioSequence
	@ManyToOne
	@JoinColumn(name = "bio_sequence_id")
	@MapsId("bioSequenceId")
	private BioSequence bioSequence;

	// uni-directional many-to-one association to Gene
	@ManyToOne
	@JoinColumn(name = "gene_id")
	@MapsId("geneId")
	private Gene gene;

	// uni-directional many-to-one association to Taxon
	@ManyToOne
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
package fr.proline.core.orm.pdi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

/**
 * The persistent class for the bio_sequence_annotation database table.
 * 
 */
@Entity
@Table(name = "bio_sequence_annotation")
public class BioSequenceAnnotation implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column(name = "object_tree_id")
    private long objectTreeId;

    @Column(name = "schema_name")
    private String schemaName;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    private String version;

    // uni-directional many-to-one association to BioSequence
    @ManyToOne
    @JoinColumn(name = "bio_sequence_id")
    private BioSequence bioSequence;

    // uni-directional many-to-one association to Taxon
    @ManyToOne
    private Taxon taxon;

    public BioSequenceAnnotation() {
    }

    public long getId() {
	return id;
    }

    public void setId(final long pId) {
	id = pId;
    }

    public long getObjectTreeId() {
	return objectTreeId;
    }

    public void setObjectTreeId(final long pObjectTreeId) {
	objectTreeId = pObjectTreeId;
    }

    public String getSchemaName() {
	return this.schemaName;
    }

    public void setSchemaName(String schemaName) {
	this.schemaName = schemaName;
    }

    public String getSerializedProperties() {
	return this.serializedProperties;
    }

    public void setSerializedProperties(String serializedProperties) {
	this.serializedProperties = serializedProperties;
    }

    public String getVersion() {
	return this.version;
    }

    public void setVersion(String version) {
	this.version = version;
    }

    public BioSequence getBioSequence() {
	return this.bioSequence;
    }

    public void setBioSequence(BioSequence bioSequence) {
	this.bioSequence = bioSequence;
    }

    public Taxon getTaxon() {
	return this.taxon;
    }

    public void setTaxon(Taxon taxon) {
	this.taxon = taxon;
    }

}

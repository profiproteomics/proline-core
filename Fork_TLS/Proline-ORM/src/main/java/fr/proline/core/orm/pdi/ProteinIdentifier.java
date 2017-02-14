package fr.proline.core.orm.pdi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

/**
 * The persistent class for the protein_identifier database table.
 * 
 */
@Entity
@NamedQuery(name = "findProteinByValueAndTaxon", query = "select pi from ProteinIdentifier pi"
	+ " where (pi.value = :value) and (pi.taxon.id = :taxid)")
@Table(name = "protein_identifier")
public class ProteinIdentifier implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column(name = "is_ac_number")
    private boolean isAcNumber;

    @Column(name = "is_active")
    private boolean isActive;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    private String value;

    // bi-directional many-to-one association to BioSequence
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bio_sequence_id")
    private BioSequence bioSequence;

    // uni-directional many-to-one association to SequenceDbConfig
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seq_db_config_id")
    private SequenceDbConfig sequenceDbConfig;

    // uni-directional many-to-one association to Taxon
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "taxon_id", nullable = false)
    private Taxon taxon;

    public ProteinIdentifier() {
    }

    public long getId() {
	return id;
    }

    public void setId(final long pId) {
	id = pId;
    }

    public boolean getIsAcNumber() {
	return isAcNumber;
    }

    public void setIsAcNumber(final boolean pIsAcNumber) {
	isAcNumber = pIsAcNumber;
    }

    public boolean getIsActive() {
	return isActive;
    }

    public void setIsActive(final boolean pIsActive) {
	isActive = pIsActive;
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

    public BioSequence getBioSequence() {
	return this.bioSequence;
    }

    public void setBioSequence(BioSequence bioSequence) {
	this.bioSequence = bioSequence;
    }

    public Taxon getTaxon() {
	return this.taxon;
    }

    public void setTaxon(final Taxon taxon) {
	this.taxon = taxon;
    }

    public SequenceDbConfig getSequenceDbConfig() {
	return sequenceDbConfig;
    }

    public void setSequenceDbConfig(SequenceDbConfig sequenceDbConfig) {
	this.sequenceDbConfig = sequenceDbConfig;
    }

}

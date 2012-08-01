package fr.proline.core.orm.msi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * The persistent class for the bio_sequence database table.
 * 
 */
@Entity
@Table(name = "bio_sequence")
public class BioSequence implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;

    private String alphabet;

    private String crc64;

    private Integer length;

    private double mass;

    private float pi;

    private String sequence;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    public BioSequence() {
    }

    /**
     * Create a Msi BioSequence entity from a Pdi BioSequence entity. Created Msi BioSequence entity shares
     * the same Id with given Pdi Peptide.
     * 
     * @param pdiBioSequence
     *            BioSequence entity from pdiDb used to initialize Msi BioSequence fields (must not be
     *            <code>null</code>)
     */
    public BioSequence(final fr.proline.core.orm.pdi.BioSequence pdiBioSequence) {

	if (pdiBioSequence == null) {
	    throw new IllegalArgumentException("PdiBioSequence is null");
	}

	setId(pdiBioSequence.getId());
	setAlphabet(pdiBioSequence.getAlphabet());
	setCrc64(pdiBioSequence.getCrc64());
	setLength(pdiBioSequence.getLength());
	setMass(pdiBioSequence.getMass());
	setPi(pdiBioSequence.getPi());
	setSequence(pdiBioSequence.getSequence());
	setSerializedProperties(pdiBioSequence.getSerializedProperties());
    }

    public Integer getId() {
	return this.id;
    }

    public void setId(Integer id) {
	this.id = id;
    }

    public String getAlphabet() {
	return this.alphabet;
    }

    public void setAlphabet(String alphabet) {
	this.alphabet = alphabet;
    }

    public String getCrc64() {
	return this.crc64;
    }

    public void setCrc64(String crc64) {
	this.crc64 = crc64;
    }

    public Integer getLength() {
	return this.length;
    }

    public void setLength(Integer length) {
	this.length = length;
    }

    public double getMass() {
	return this.mass;
    }

    public void setMass(double mass) {
	this.mass = mass;
    }

    public float getPi() {
	return this.pi;
    }

    public void setPi(float pi) {
	this.pi = pi;
    }

    public String getSequence() {
	return this.sequence;
    }

    public void setSequence(String sequence) {
	this.sequence = sequence;
    }

    public String getSerializedProperties() {
	return this.serializedProperties;
    }

    public void setSerializedProperties(String serializedProperties) {
	this.serializedProperties = serializedProperties;
    }

}
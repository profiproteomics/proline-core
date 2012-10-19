package fr.proline.core.orm.pdi;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.Table;

/**
 * The persistent class for the bio_sequence database table.
 * 
 */
@Entity(name = "fr.proline.core.orm.pdi.BioSequence")
@NamedQueries({
	@NamedQuery(name = "findPdiBioSequenceForCrc", query = "select bs from fr.proline.core.orm.pdi.BioSequence bs"
		+ " where upper(bs.crc64) = :crc64"),

	@NamedQuery(name = "findPdiBioSequencesForCrcs", query = "select distinct bs from fr.proline.core.orm.pdi.BioSequence bs"
		+ " left join fetch bs.proteinIdentifiers as pi left join fetch pi.sequenceDbConfig"
		+ " where upper(bs.crc64) in :crcs")

})
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

    // bi-directional many-to-one association to ProteinIdentifier
    @OneToMany(mappedBy = "bioSequence")
    private Set<ProteinIdentifier> proteinIdentifiers;

    public BioSequence() {
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

    public void setProteinIdentifiers(final Set<ProteinIdentifier> protIdentifiers) {
	proteinIdentifiers = protIdentifiers;
    }

    public Set<ProteinIdentifier> getProteinIdentifiers() {
	return this.proteinIdentifiers;
    }

    public void addProteinIdentifier(final ProteinIdentifier protIdentifier) {

	if (protIdentifier != null) {
	    Set<ProteinIdentifier> protIdentifiers = getProteinIdentifiers();

	    if (protIdentifiers == null) {
		protIdentifiers = new HashSet<ProteinIdentifier>();

		setProteinIdentifiers(protIdentifiers);
	    }

	    protIdentifiers.add(protIdentifier);
	}

    }

    public void removeProteinIdentifier(final ProteinIdentifier protIdentifier) {
	final Set<ProteinIdentifier> protIdentifiers = getProteinIdentifiers();

	if (protIdentifiers != null) {
	    protIdentifiers.remove(protIdentifier);
	}

    }

}
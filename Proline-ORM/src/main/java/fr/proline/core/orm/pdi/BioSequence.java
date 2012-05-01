package fr.proline.core.orm.pdi;

import java.io.Serializable;
import javax.persistence.*;
import java.util.Set;


/**
 * The persistent class for the bio_sequence database table.
 * 
 */
@Entity(name="fr.proline.core.orm.pdi.BioSequence")
@Table(name="bio_sequence")
public class BioSequence implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	private String alphabet;

	private String crc64;

	private Integer length;

	private double mass;

	private float pi;

	private String sequence;

	@Column(name="serialized_properties")
	private String serializedProperties;

	//bi-directional many-to-one association to ProteinIdentifier
	@OneToMany(mappedBy="bioSequence")
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

	public Set<ProteinIdentifier> getProteinIdentifiers() {
		return this.proteinIdentifiers;
	}

	public void setProteinIdentifiers(Set<ProteinIdentifier> proteinIdentifiers) {
		this.proteinIdentifiers = proteinIdentifiers;
	}
	
}
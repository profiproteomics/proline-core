package fr.proline.core.orm.msi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Table;

import org.apache.commons.lang3.StringUtils;

import fr.proline.core.orm.pdi.Alphabet;

/**
 * The persistent class for the bio_sequence database table.
 * 
 */
@Entity
@Table(name = "bio_sequence")
public class BioSequence implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	// MSI BioSequence Id are not generated (taken from Pdi BioSequence entity)
	private long id;

	@Enumerated(EnumType.STRING)
	private Alphabet alphabet;

	private String crc64;

	private int length;

	private int mass;

	private Float pi;

	private String sequence;

	@Column(name = "serialized_properties")
	private String serializedProperties;

	public BioSequence() {
	}

	/**
	 * Create a Msi BioSequence entity from a Pdi BioSequence entity. Created Msi BioSequence entity shares the same Id with given Pdi Peptide.
	 * 
	 * @param pdiBioSequence
	 *            BioSequence entity from pdiDb used to initialize Msi BioSequence fields (must not be <code>null</code>)
	 */
	public BioSequence(final fr.proline.core.orm.pdi.BioSequence pdiBioSequence) {

		if (pdiBioSequence == null) {
			throw new IllegalArgumentException("PdiBioSequence is null");
		}

		setId(pdiBioSequence.getId());
		setAlphabet(pdiBioSequence.getAlphabet());
		setCrc64(pdiBioSequence.getCrc64());

		// FIXME LMN inconsistent nullable field "length"
		final Integer pdiBioSequenceLength = pdiBioSequence.getLength();

		if (pdiBioSequenceLength == null) {
			setLength(-1);
		} else {
			setLength(pdiBioSequenceLength.intValue());
		}

		setMass(pdiBioSequence.getMass());
		setPi(pdiBioSequence.getPi());
		setSequence(pdiBioSequence.getSequence());

		final String pdiBioSequenceProps = pdiBioSequence.getSerializedProperties();

		if (StringUtils.isEmpty(pdiBioSequenceProps)) {
			setSequence(null);
		} else {
			setSerializedProperties(pdiBioSequenceProps);
		}

	}

	public long getId() {
		return id;
	}

	public void setId(final long pId) {
		id = pId;
	}

	public Alphabet getAlphabet() {
		return alphabet;
	}

	public void setAlphabet(final Alphabet pAlphabet) {
		alphabet = pAlphabet;
	}

	public String getCrc64() {
		return this.crc64;
	}

	public void setCrc64(String crc64) {
		this.crc64 = crc64;
	}

	public int getLength() {
		return length;
	}

	public void setLength(final int pLength) {
		length = pLength;
	}

	public int getMass() {
		return this.mass;
	}

	public void setMass(int mass) {
		this.mass = mass;
	}

	public Float getPi() {
		return this.pi;
	}

	public void setPi(Float pi) {
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

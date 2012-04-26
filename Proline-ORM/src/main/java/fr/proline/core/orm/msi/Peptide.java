package fr.proline.core.orm.msi;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the peptide database table.
 * 
 */
@Entity(name="fr.proline.core.orm.msi.Peptide")
public class Peptide implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	@Column(name="calculated_mass")
	private double calculatedMass;

	@Column(name="ptm_string")
	private String ptmString;

	private String sequence;

	@Column(name="serialized_properties")
	private String serializedProperties;

    public Peptide() {
    }

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public double getCalculatedMass() {
		return this.calculatedMass;
	}

	public void setCalculatedMass(double calculatedMass) {
		this.calculatedMass = calculatedMass;
	}

	public String getPtmString() {
		return this.ptmString;
	}

	public void setPtmString(String ptmString) {
		this.ptmString = ptmString;
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
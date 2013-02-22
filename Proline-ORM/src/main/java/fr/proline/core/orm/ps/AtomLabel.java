package fr.proline.core.orm.ps;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the atom_label database table.
 * 
 */
@Entity
@Table(name="atom_label")
public class AtomLabel implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	@Column(name="id")
	private Integer id;

	@Column(name="average_mass")
	private double averageMass;

	@Column(name="mono_mass")
	private double monoMass;

	private String name;

	@Column(name="serialized_properties")
	private String serializedProperties;

	private String symbol;

    public AtomLabel() {
    }

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public double getAverageMass() {
		return this.averageMass;
	}

	public void setAverageMass(double averageMass) {
		this.averageMass = averageMass;
	}

	public double getMonoMass() {
		return this.monoMass;
	}

	public void setMonoMass(double monoMass) {
		this.monoMass = monoMass;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public String getSymbol() {
		return this.symbol;
	}

	public void setSymbol(String symbol) {
		this.symbol = symbol;
	}

}
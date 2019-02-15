package fr.proline.core.orm.msi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * The persistent class for the atom_label database table.
 * 
 */
@Entity
@Table(name = "atom_label")
public class AtomLabel implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@Column(name = "id")
	private long id;

	@Column(name = "average_mass")
	private double averageMass;

	@Column(name = "mono_mass")
	private double monoMass;

	private String name;

	@Column(name = "serialized_properties")
	private String serializedProperties;

	private String symbol;

	public AtomLabel() {
	}

	public long getId() {
		return id;
	}

	public void setId(final long pId) {
		id = pId;
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

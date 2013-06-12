package fr.proline.core.orm.msi;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;


/**
 * The persistent class for the ion_search database table.
 * 
 */
@Entity
@Table(name="ion_search")
public class IonSearch extends SearchSetting {
	private static final long serialVersionUID = 1L;


	@Column(name="max_protein_mass")
	private double maxProteinMass;

	@Column(name="min_protein_mass")
	private double minProteinMass;

	@Column(name="protein_pi")
	private float proteinPi;

    public IonSearch() {
    }

	public double getMaxProteinMass() {
		return this.maxProteinMass;
	}

	public void setMaxProteinMass(double maxProteinMass) {
		this.maxProteinMass = maxProteinMass;
	}

	public double getMinProteinMass() {
		return this.minProteinMass;
	}

	public void setMinProteinMass(double minProteinMass) {
		this.minProteinMass = minProteinMass;
	}

	public float getProteinPi() {
		return this.proteinPi;
	}

	public void setProteinPi(float proteinPi) {
		this.proteinPi = proteinPi;
	}

}
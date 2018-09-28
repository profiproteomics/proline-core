package fr.proline.core.orm.msi;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

/**
 * The persistent class for the ion_search database table.
 * 
 */
@Entity
@Table(name = "ion_search")
public class IonSearch extends SearchSetting {

	private static final long serialVersionUID = 1L;

	@Column(name = "max_protein_mass")
	private Double maxProteinMass;

	@Column(name = "min_protein_mass")
	private Double minProteinMass;

	@Column(name = "protein_pi")
	private Float proteinPi;

	public IonSearch() {
	}

	public Double getMaxProteinMass() {
		return maxProteinMass;
	}

	public void setMaxProteinMass(final Double pMaxProteinMass) {
		maxProteinMass = pMaxProteinMass;
	}

	public Double getMinProteinMass() {
		return minProteinMass;
	}

	public void setMinProteinMass(final Double pMinProteinMass) {
		minProteinMass = pMinProteinMass;
	}

	public Float getProteinPi() {
		return proteinPi;
	}

	public void setProteinPi(final Float pProteinPi) {
		proteinPi = pProteinPi;
	}

}

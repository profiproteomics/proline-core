package fr.proline.core.orm.msi;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;


/**
 * The persistent class for the msms_search database table.
 * 
 */
@Entity
@Table(name="msms_search")
public class MsmsSearch extends SearchSetting {
	private static final long serialVersionUID = 1L;

	@Column(name="fragment_charge_states")
	private String fragmentChargeStates;

	@Column(name="fragment_mass_error_tolerance")
	private double fragmentMassErrorTolerance;

	@Column(name="fragment_mass_error_tolerance_unit")
	private String fragmentMassErrorToleranceUnit;

    public MsmsSearch() {
    }

	public String getFragmentChargeStates() {
		return this.fragmentChargeStates;
	}

	public void setFragmentChargeStates(String fragmentChargeStates) {
		this.fragmentChargeStates = fragmentChargeStates;
	}

	public double getFragmentMassErrorTolerance() {
		return this.fragmentMassErrorTolerance;
	}

	public void setFragmentMassErrorTolerance(double fragmentMassErrorTolerance) {
		this.fragmentMassErrorTolerance = fragmentMassErrorTolerance;
	}

	public String getFragmentMassErrorToleranceUnit() {
		return this.fragmentMassErrorToleranceUnit;
	}

	public void setFragmentMassErrorToleranceUnit(String fragmentMassErrorToleranceUnit) {
		this.fragmentMassErrorToleranceUnit = fragmentMassErrorToleranceUnit;
	}

}
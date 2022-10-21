package fr.proline.core.orm.lcms;

/* time and delta time represented in the time_list and delta_time_list of MapAlignment */

/**
 * Represent an delta value at a specific time.
 * This could be a delta time for MapAlignment (time_list and delta_time_list )
 * as well as delta moz for Processed Map MozCalibration (time is specified instead of moz in MoZCalibration !!)
 */
public class MapTime {

	private Double time;
	private Double deltaValue;

	public MapTime() {
		super();
	}

	public MapTime(Double time, Double deltaValue) {
		super();
		this.time = time;
		this.deltaValue = deltaValue;
	}

	public Double getTime() {
		return time;
	}

	public void setTime(Double time) {
		this.time = time;
	}

	public Double getDeltaValue() {
		return deltaValue;
	}

	public void setDeltaValue(Double deltaValue) {
		this.deltaValue = deltaValue;
	}

}

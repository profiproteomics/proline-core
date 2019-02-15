package fr.proline.core.orm.lcms;

/* time and delta time represented in the time_list and delta_time_list of MapAlignment */
public class MapTime {

	private Double time;
	private Double deltaTime;

	public MapTime() {
		super();
	}

	public MapTime(Double time, Double deltaTime) {
		super();
		this.time = time;
		this.deltaTime = deltaTime;
	}

	public Double getTime() {
		return time;
	}

	public void setTime(Double time) {
		this.time = time;
	}

	public Double getDeltaTime() {
		return deltaTime;
	}

	public void setDeltaTime(Double deltaTime) {
		this.deltaTime = deltaTime;
	}

}

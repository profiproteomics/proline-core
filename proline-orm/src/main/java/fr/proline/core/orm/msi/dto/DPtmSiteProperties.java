package fr.proline.core.orm.msi.dto;

import java.util.Map;

public class DPtmSiteProperties {

	private Float mascotDeltaScore;
	private Map<String, Float> mascotProbabilityBySite;

	public Float getMascotDeltaScore() {
		return mascotDeltaScore;
	}

	public void setMascotDeltaScore(Float mascotDeltaScore) {
		this.mascotDeltaScore = mascotDeltaScore;
	}

	public Map<String, Float> getMascotProbabilityBySite() {
		return mascotProbabilityBySite;
	}

	public void setMascotProbabilityBySite(Map<String, Float> mascotProbabilityBySite) {
		this.mascotProbabilityBySite = mascotProbabilityBySite;
	}

}

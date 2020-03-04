package fr.proline.core.orm.lcms.dto;

import fr.proline.core.orm.lcms.Feature;
import fr.proline.core.orm.lcms.Peak;

public class DFeature extends Feature {

	private static final long serialVersionUID = 1L;
	private double predictedElutionTime;
	private boolean isBestChild;
	private Long quantChannelId;
	private Peak[] peakArray;

	public DFeature(Feature f) {
		super();
		setId(f.getId());
		setApexIntensity(f.getApexIntensity());
		setCharge(f.getCharge());
		setCompoundId(f.getCompoundId());
		setDuration(f.getDuration());
		setElutionTime(f.getElutionTime());
		setIntensity(f.getIntensity());
		setIsCluster(f.getIsCluster());
		setIsOverlapping(f.getIsOverlapping());
		setMapLayerId(f.getMapLayerId());
		setMoz(f.getMoz());
		setMs1Count(f.getMs1Count());
		setMs2Count(f.getMs2Count());
		setPeakelCount(f.getPeakelCount());
		setQualityScore(f.getQualityScore());
		setSerializedProperties(f.getSerializedProperties());
		setTheoreticalFeatureId(f.getTheoreticalFeatureId());
		setMap(f.getMap());
		setFirstScan(f.getFirstScan());
		setLastScan(f.getLastScan());
		setApexScan(f.getApexScan());
		setFeatureClusterItems(f.getFeatureClusterItems());
		setFeatureMs2Events(f.getFeatureMs2Events());
		setFeaturePeakelItems(f.getFeaturePeakelItems());
		peakArray = null;

	}

	public double getPredictedElutionTime() {
		return predictedElutionTime;
	}

	public void setPredictedElutionTime(double predictedElutionTime) {
		this.predictedElutionTime = predictedElutionTime;
	}

	public boolean isBestChild() {
		return isBestChild;
	}

	public void setBestChild(boolean isBestChild) {
		this.isBestChild = isBestChild;
	}

	public Long getQuantChannelId() {
		return quantChannelId;
	}

	public void setquantChannelId(Long quantChannelId) {
		this.quantChannelId = quantChannelId;
	}

	public Peak[] getPeakArray() {
		return peakArray;
	}

	public void setPeakArray(Peak[] peakArray) {
		this.peakArray = peakArray;
	}
}

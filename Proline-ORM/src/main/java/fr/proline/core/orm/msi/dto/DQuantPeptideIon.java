package fr.proline.core.orm.msi.dto;

import java.util.List;

public class DQuantPeptideIon {
	Float rawAbundance;
	Float abundance;
	Double moz;
	Float elutionTime;
	Float duration;
	Float correctedElutionTime;
	Float predictedElutionTime;
	Integer scanNumber;

	Integer peptideMatchesCount;
	Float ms2MatchingFrequency;
	Float bestPeptideMatchScore;
	Long quantChannelId;
	Long peptideId;
	Long peptideInstanceId;

	List<Long> msQueryIds;
	Long lcmsFeatureId;

	Long lcmsMasterFeatureId;

	Long unmodifiedPeptideIonId;
	Integer selectionLevel;
	Boolean isReliable;

	// Necessary Construtor for JSON parsing !
	protected DQuantPeptideIon() {
		super();
	}

	public Float getRawAbundance() {
		return rawAbundance;
	}

	public void setRawAbundance(Float rawAbundance) {
		this.rawAbundance = rawAbundance;
	}

	public Float getAbundance() {
		return abundance;
	}

	public void setAbundance(Float abundance) {
		this.abundance = abundance;
	}

	public Double getMoz() {
		return moz;
	}

	public void setMoz(Double moz) {
		this.moz = moz;
	}

	public Float getElutionTime() {
		return elutionTime;
	}

	public void setElutionTime(Float elutionTime) {
		this.elutionTime = elutionTime;
	}

	public Float getDuration() {
		return duration;
	}

	public void setDuration(Float duration) {
		this.duration = duration;
	}

	public Float getCorrectedElutionTime() {
		return correctedElutionTime;
	}

	public void setCorrectedElutionTime(Float correctedElutionTime) {
		this.correctedElutionTime = correctedElutionTime;
	}

	public Integer getScanNumber() {
		return scanNumber;
	}

	public void setScanNumber(Integer scanNumber) {
		this.scanNumber = scanNumber;
	}

	public Integer getPeptideMatchesCount() {
		return peptideMatchesCount;
	}

	public void setPeptideMatchesCount(Integer peptideMatchesCount) {
		this.peptideMatchesCount = peptideMatchesCount;
	}

	public Float getMs2MatchingFrequency() {
		return ms2MatchingFrequency;
	}

	public void setMs2MatchingFrequency(Float ms2MatchingFrequency) {
		this.ms2MatchingFrequency = ms2MatchingFrequency;
	}

	public Float getBestPeptideMatchScore() {
		return bestPeptideMatchScore;
	}

	public void setBestPeptideMatchScore(Float bestPeptideMatchScore) {
		this.bestPeptideMatchScore = bestPeptideMatchScore;
	}

	public Long getQuantChannelId() {
		return quantChannelId;
	}

	public void setQuantChannelId(Long quantChannelId) {
		this.quantChannelId = quantChannelId;
	}

	public Long getPeptideId() {
		return peptideId;
	}

	public void setPeptideId(Long peptideId) {
		this.peptideId = peptideId;
	}

	public Long getPeptideInstanceId() {
		return peptideInstanceId;
	}

	public void setPeptideInstanceId(Long peptideInstanceId) {
		this.peptideInstanceId = peptideInstanceId;
	}

	public List<Long> getMsQueryIds() {
		return msQueryIds;
	}

	public void setMsQueryIds(List<Long> msQueryIds) {
		this.msQueryIds = msQueryIds;
	}

	public Long getLcmsFeatureId() {
		return lcmsFeatureId;
	}

	public void setLcmsFeatureId(Long lcmsFeatureId) {
		this.lcmsFeatureId = lcmsFeatureId;
	}

	public Long getLcmsMasterFeatureId() {
		return lcmsMasterFeatureId;
	}

	public void setLcmsMasterFeatureId(Long lcmsMasterFeatureId) {
		this.lcmsMasterFeatureId = lcmsMasterFeatureId;
	}

	public Long getUnmodifiedPeptideIonId() {
		return unmodifiedPeptideIonId;
	}

	public void setUnmodifiedPeptideIonId(Long unmodifiedPeptideIonId) {
		this.unmodifiedPeptideIonId = unmodifiedPeptideIonId;
	}

	public Integer getSelectionLevel() {
		return selectionLevel;
	}

	public void setSelectionLevel(Integer selectionLevel) {
		this.selectionLevel = selectionLevel;
	}

	public Float getPredictedElutionTime() {
		return predictedElutionTime;
	}

	public void setPredictedElutionTime(Float predictedElutionTime) {
		this.predictedElutionTime = predictedElutionTime;
	}

	public Boolean getIsReliable() {
		return isReliable;
	}

	public void setIsReliable(Boolean isReliable) {
		this.isReliable = isReliable;
	}

}

package fr.proline.core.orm.msi.dto;

public class DQuantPeptide {
	
	Float rawAbundance;
	Float abundance;
	Integer selectionLevel;
	Integer peptideMatchesCount;
	Long quantChannelId;
	Float elutionTime;
	Long peptideId;
	Long peptideInstanceId;
    
    // Necessary Construtor for JSON parsing !
	protected DQuantPeptide() {
		super();
	}
	

	public DQuantPeptide(Float rawAbundance, Float abundance, Integer selectionLevel,
			Integer peptideMatchesCount, Long quantChannelId) {
		super();
		this.rawAbundance = rawAbundance;
		this.abundance = abundance;
		this.selectionLevel = selectionLevel;
		this.peptideMatchesCount = peptideMatchesCount;
		this.quantChannelId = quantChannelId;
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
	public Integer getSelectionLevel() {
		return selectionLevel;
	}
	public void setSelectionLevel(Integer selectionLevel) {
		this.selectionLevel = selectionLevel;
	}
	public Integer getPeptideMatchesCount() {
		return peptideMatchesCount;
	}
	public void setPeptideMatchesCount(Integer peptideMatchesCount) {
		this.peptideMatchesCount = peptideMatchesCount;
	}
	public Long getQuantChannelId() {
		return quantChannelId;
	}
	public void setQuantChannelId(Long quantChannelId) {
		this.quantChannelId = quantChannelId;
	}


	public Float getElutionTime() {
		return elutionTime;
	}


	public void setElutionTime(Float elutionTime) {
		this.elutionTime = elutionTime;
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


}

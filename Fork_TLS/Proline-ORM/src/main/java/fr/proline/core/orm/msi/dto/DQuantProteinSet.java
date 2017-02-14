package fr.proline.core.orm.msi.dto;

public class DQuantProteinSet {

	Float rawAbundance;
	Float abundance;
	Integer selectionLevel;
	Integer peptideMatchesCount;
	Long quantChannelId;
	Long proteinSetId = 0l;
    Long proteinMatchId = 0l;
    
    
    // Necessary Construtor for JSON parsing !
	protected DQuantProteinSet() {
		super();
	}
	
	
	public DQuantProteinSet(Float rawAbundance, Float abundance, Integer selectionLevel,
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

	public Long getProteinSetId() {
		return proteinSetId;
	}

	public void setProteinSetId(Long proteinSetId) {
		this.proteinSetId = proteinSetId;
	}

	public Long getProteinMatchId() {
		return proteinMatchId;
	}

	public void setProteinMatchId(Long proteinMatchId) {
		this.proteinMatchId = proteinMatchId;
	}
	
	
	
}

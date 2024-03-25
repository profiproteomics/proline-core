package fr.proline.core.orm.msi.dto;

import java.util.List;

public class DQuantReporterIon {

    Float rawAbundance;
    Float abundance;
    Long quantChannelId;
    Integer selectionLevel;
    Integer peptideMatchesCount;
    Double moz;

    // Necessary Construtor for JSON parsing !
    protected DQuantReporterIon() {
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

    public Long getQuantChannelId() {
        return quantChannelId;
    }

    public void setQuantChannelId(Long quantChannelId) {
        this.quantChannelId = quantChannelId;
    }

    public Double getMoz() {
        return moz;
    }

    public void setMoz(Double moz) {
        this.moz = moz;
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
}

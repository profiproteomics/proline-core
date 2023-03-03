package fr.proline.core.orm.msi.dto;

import java.util.List;

public class DQuantReporterIon {

    Float rawAbundance;
    Float abundance;
    Long quantChannelId;
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
}

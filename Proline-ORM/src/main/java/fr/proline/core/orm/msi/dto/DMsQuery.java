package fr.proline.core.orm.msi.dto;

import fr.proline.core.orm.msi.Spectrum;


/**
 *
 * @author JM235353
 */
public class DMsQuery implements Comparable<DMsQuery> {
    
    private long m_peptideMatchId;
    private long m_id;
    private int m_initialId;
    private Float m_precursorIntensity;
    
    private int m_charge;
    private double m_moz;
    
    
    private Spectrum m_spectrum; // will be removed : replaced by DSpectrum
    
    private DSpectrum m_dSpectrum;
    private boolean m_spectrumSet;
    
    public DMsQuery(long peptideMatchId, long id, int initialId, Float precursorIntensity) {
        m_peptideMatchId = peptideMatchId;
        m_id = id;
        m_initialId = initialId;
        m_precursorIntensity = precursorIntensity;

        m_spectrum = null;
        m_dSpectrum = null;
        m_spectrumSet = false;
        
    }

    public int getCharge() {
    	return m_charge;
    }

    public void setCharge(int charge) {
    	m_charge = charge;
    }

    public double getMoz() {
    	return m_moz;
    }

    public void setMoz(double moz) {
    	m_moz = moz;
    }
    
    
    public Float getPrecursorIntensity() {
        return m_precursorIntensity;
    }
    
    public long getPeptideMatchId() {
        return m_peptideMatchId;
    }
    
    public long getId() {
        return m_id;
    }

    public int getInitialId() {
        return m_initialId;
    }


    public Spectrum getSpectrum() {
        return m_spectrum;
    }

    
    public void setSpectrum(Spectrum spectrum) {
        m_spectrum = spectrum;
        m_spectrumSet = true;
    }
    
    public DSpectrum getDSpectrum() {
        return m_dSpectrum;
    }

    
    public void setDSpectrum(DSpectrum spectrum) {
    	m_dSpectrum = spectrum;
        m_spectrumSet = true;
    }
    
    public boolean isSpectrumSet() {
        return m_spectrumSet;
    }
    
    @Override
    public int compareTo(final DMsQuery otherQuery) {
        return Integer.valueOf(getInitialId()).compareTo(Integer.valueOf(otherQuery.getInitialId()));
    }
    
    @Override
    public String toString() {
	    return Integer.toString(getInitialId());
    }
}

package fr.proline.core.orm.msi.dto;




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

    
    private DSpectrum m_dSpectrum;
    private boolean m_spectrumSet;
    
    public DMsQuery(long peptideMatchId, long id, int initialId, Float precursorIntensity) {
        m_peptideMatchId = peptideMatchId;
        m_id = id;
        m_initialId = initialId;
        m_precursorIntensity = precursorIntensity;

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
    
    public boolean isSpectrumFullySet() {
        if  (!m_spectrumSet) {
        	return false;
        }
        
        return (getDSpectrum().getIntensityList() != null);
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

package fr.proline.core.orm.msi.dto;


/**
 *
 * @author JM235353
 */
public class DProteinSet {
    
    private long m_id;
    private long m_resultSummaryId;
    private long m_typicalProteinMatchId;
    
    private DProteinMatch m_typicalProteinMatch;
    
    private Integer m_spectralCount; // JPM : will be removed
    
    private Integer m_peptideMatchCount;
    private Integer m_basicSpectralCount;
    private Integer m_specificSpectralCount;
    private Integer m_sameSetCount;
    private Integer m_subSetCount;
    private DProteinMatch[] m_sameSet = null; // loaded later than sameSetCount
    private DProteinMatch[] m_subSet = null; // loaded later than subSetCount

    public DProteinSet(long id, long typicalProteinMatchId, long resultSummaryId) {
        m_id = id;
        m_typicalProteinMatchId = typicalProteinMatchId;
        m_resultSummaryId = resultSummaryId;
        
        m_typicalProteinMatch = null;
        m_spectralCount = null;  // JPM : will be removed
        m_peptideMatchCount = null;
        m_basicSpectralCount = null;
        m_specificSpectralCount = null;
        m_sameSetCount = null;
        m_subSetCount = null;
    }
    

    
    
    public long getId() {
        return m_id;
    }
    
    public long getProteinMatchId() {
        return m_typicalProteinMatchId;
    }
    
    public long getResultSummaryId() {
        return m_resultSummaryId;
    }
    
    public DProteinMatch getTypicalProteinMatch() {
        return m_typicalProteinMatch;
    }

    public void setTypicalProteinMatch(DProteinMatch typicalProteinMatch) {
        m_typicalProteinMatch = typicalProteinMatch;
    }
    
    // JPM : will be removed
    public Integer getSpectralCount() {
        return m_spectralCount;
    }

    // JPM : will be removed
    public void setSpectralCount(Integer spectralCount) {
        m_spectralCount = spectralCount;
    }

    public Integer getPeptideMatchCount() {
        return m_peptideMatchCount;
    }

    public void setPeptideMatchCount(Integer peptideMatchCount) {
    	m_peptideMatchCount = peptideMatchCount;
    }

    public Integer getBasicSpectralCount() {
        return m_basicSpectralCount;
    }

    public void setBasicSpectralCount(Integer basicSpectralCount) {
    	m_basicSpectralCount = basicSpectralCount;
    }

    
    public Integer getSpecificSpectralCount() {
        return m_specificSpectralCount;
    }

    public void setSpecificSpectralCount(Integer specificSpectralCount) {
        m_specificSpectralCount = specificSpectralCount;
    }
    
    public Integer getSameSetCount() {
        return m_sameSetCount;
    }

    public void setSameSetCount(Integer sameSetCount) {
        m_sameSetCount = sameSetCount;
    }
    
    public Integer getSubSetCount() {
        return m_subSetCount;
    }

    public void setSubSetCount(Integer subSetCount) {
        m_subSetCount = subSetCount;
    }
    
    public DProteinMatch[] getSameSet() {
        return m_sameSet;
    }

    public void setSameSet(DProteinMatch[] sameSet) {
        m_sameSet = sameSet;
    }

    public DProteinMatch[] getSubSet() {
        return m_subSet;
    }

    public void setSubSet(DProteinMatch[] subSet) {
        m_subSet = subSet;
    }
}

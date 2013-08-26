package fr.proline.core.orm.msi.dto;

import fr.proline.core.orm.msi.BioSequence;
import fr.proline.core.orm.msi.PeptideSet;
import java.util.HashMap;

public class DProteinMatch {

    private long m_id;
    private String m_accession;
    private Float m_score;
    private int m_peptideCount;
    private long m_resultSetId;
    private String m_description;
    private Long m_bioSequenceId;
    private BioSequence m_bioSequence;
    
    private HashMap<Long, PeptideSet> peptideSetMap = null;
    
    private DPeptideMatch[] m_peptideMatches;
    //private ProteinSet[] m_proteinSetArray = null;
    
    public DProteinMatch(long id, String accession,  Float score, int peptideCount, long resultSetId, String description, Long bioSequenceId) {
        m_id = id;
        m_accession = accession;
        m_score = score;
        m_peptideCount = peptideCount;
        m_resultSetId = resultSetId;
        m_description = description;
        m_bioSequenceId = bioSequenceId;
    }
    
    public DProteinMatch(long id, String accession,  Float score, int peptideCount, long resultSetId, String description, Long bioSequenceId, PeptideSet peptideSet) {
        m_id = id;
        m_accession = accession;
        m_score = score;
        m_peptideCount = peptideCount;
        m_resultSetId = resultSetId;
         m_description = description;
        m_bioSequenceId = bioSequenceId;
        
        setPeptideSet(peptideSet.getResultSummaryId(), peptideSet);
    }
    
    public long getId() {
        return m_id;
    }

    public void setId(final long pId) {
        m_id = pId;
    }
    
    public long getResultSetId() {
    	return m_resultSetId;
    }

    public String getAccession() {
        return m_accession;
    }

    public void setAccession(String accession) {
        m_accession = accession;
    }

    public Float getScore() {
        return m_score;
    }
    
    public int getPeptideCount() {
        return m_peptideCount;
    }
    
    public String getDescription() {
        return m_description;
    }
    
    public Long getBioSequenceId() {
        return m_bioSequenceId;
    }
    
    public void setBioSequence(BioSequence bioSequence) {
        m_bioSequence = bioSequence;
    }
    
    public BioSequence getBioSequence() {
        return m_bioSequence;
    }
    
    public boolean isBiosequenceSet() {
        if ((m_bioSequence != null) || (m_bioSequenceId == null)) {
            return true;
        }

        return false;
    }
    
    public DPeptideMatch[] getPeptideMatches() {
        return m_peptideMatches;
    }

    public void setPeptideMatches(DPeptideMatch[] peptideMatches) {
        m_peptideMatches = peptideMatches;
    }
    
    public PeptideSet getPeptideSet(Long resultSummaryId) {
        if (peptideSetMap == null) {
            return null;
        }
        return peptideSetMap.get(resultSummaryId);
    }



    final public void setPeptideSet(Long resultSummaryId, PeptideSet peptideSet) {
        if (peptideSetMap == null) {
            peptideSetMap = new HashMap<Long, PeptideSet>();
        }
        peptideSetMap.put(resultSummaryId, peptideSet);
    }
    
    /*public ProteinSet[] getProteinSetArray() {
        return m_proteinSetArray;
    }

    public void setProteinSetArray(ProteinSet[] proteinSetArray) {
        m_proteinSetArray = proteinSetArray;
    }*/
    
}

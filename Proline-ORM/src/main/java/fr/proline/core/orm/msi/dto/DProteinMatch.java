package fr.proline.core.orm.msi.dto;

import java.util.HashMap;

public class DProteinMatch {

    private long m_id;
    private String m_accession;
    private Float m_score;
    private int m_peptideCount;
    private long m_resultSetId;
    private String m_description;
    private DBioSequence m_bioSequence;
    private boolean m_bioSequenceSet = false;
    
    private HashMap<Long, DPeptideSet> peptideSetMap = null;
    
    private DPeptideMatch[] m_peptideMatches;
    private long[] m_peptideMatchesId;
    //private ProteinSet[] m_proteinSetArray = null;
    
    public DProteinMatch(long id, String accession,  Float score, int peptideCount, long resultSetId, String description) {
        m_id = id;
        m_accession = accession;
        m_score = score;
        m_peptideCount = peptideCount;
        m_resultSetId = resultSetId;
        m_description = description;
    }
    
    public DProteinMatch(long id, String accession,  Float score, int peptideCount, long resultSetId, String description, long peptideSetId, Float peptideSetScore, int sequenceCount, int peptideSetPeptideCount, int peptideMatchCount, long resultSummaryId) {
        m_id = id;
        m_accession = accession;
        m_score = score;
        m_peptideCount = peptideCount;
        m_resultSetId = resultSetId;
        m_description = description;
        
        DPeptideSet peptideSet = new DPeptideSet(peptideSetId, peptideSetScore, sequenceCount, peptideSetPeptideCount, peptideMatchCount, resultSummaryId);
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
    

    
    public void setDBioSequence(DBioSequence bioSequence) {
        m_bioSequence = bioSequence;
        m_bioSequenceSet = true;
    }
    
    public DBioSequence getDBioSequence() {
        return m_bioSequence;
    }

    
    public boolean isDBiosequenceSet() {
        return m_bioSequenceSet;
    }
    
    public DPeptideMatch[] getPeptideMatches() {
        return m_peptideMatches;
    }

    public void setPeptideMatches(DPeptideMatch[] peptideMatches) {
        m_peptideMatches = peptideMatches;
    }
    
    public long[] getPeptideMatchesId() {
        return m_peptideMatchesId;
    }

    public void setPeptideMatchesId(long[] peptideMatchesId) {
        m_peptideMatchesId = peptideMatchesId;
    }
    
    
    
    public DPeptideSet getPeptideSet(Long resultSummaryId) {
        if (peptideSetMap == null) {
            return null;
        }
        return peptideSetMap.get(resultSummaryId);
    }



    final public void setPeptideSet(Long resultSummaryId, DPeptideSet peptideSet) {
        if (peptideSetMap == null) {
            peptideSetMap = new HashMap<Long, DPeptideSet>();
        }
        peptideSetMap.put(resultSummaryId, peptideSet);
    }

    
}

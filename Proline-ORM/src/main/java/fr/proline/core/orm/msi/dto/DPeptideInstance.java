package fr.proline.core.orm.msi.dto;

import fr.proline.core.orm.msi.SequenceMatch;


public class DPeptideInstance {

	private long m_id;
	private long m_peptideId;
	private int m_validatedProteinSetCount;
	private Float m_elutionTime;
	
	private DPeptideMatch m_bestPeptideMatch;
	//private SequenceMatch m_sequenceMatch; // JPM.TODO : to be removed
	
	public DPeptideInstance(long id, long peptideId, int validatedProteinSetCount, Float elutionTime) {
        m_id = id;
        m_validatedProteinSetCount = validatedProteinSetCount;
        m_elutionTime = elutionTime;
        m_peptideId = peptideId;
        
        m_bestPeptideMatch = null;
        //m_sequenceMatch = null;
    }
	
    public long getId() {
        return m_id;
    }

    public long getPeptideId() {
    	return m_peptideId;
    }
    
    public int getValidatedProteinSetCount() {
        return m_validatedProteinSetCount;
    }
    
    public Float getElutionTime() {
        return m_elutionTime;
    }
    
    public DPeptideMatch getBestPeptideMatch() {
        return m_bestPeptideMatch;
    }
    
    public void setBestPeptideMatch(DPeptideMatch bestPeptideMatch) {
        m_bestPeptideMatch = bestPeptideMatch;
    }
    
    /*public SequenceMatch getSequenceMatch() {
    	return m_sequenceMatch;
    }
    
    public void setSequenceMatch(SequenceMatch sequenceMatch) {
    	m_sequenceMatch = sequenceMatch;
    }*/
}

package fr.proline.core.orm.msi.dto;

import fr.proline.core.orm.msi.ResultSummary;
import fr.proline.core.orm.msi.Peptide;


public class DPeptideInstance {

	private long m_id;
	private long m_peptideId;
	private Peptide m_peptide;
	private int m_validatedProteinSetCount;
	private Float m_elutionTime;
	
	
	private DPeptideMatch m_bestPeptideMatch;
	
	private ResultSummary resultSummary;
	
	public DPeptideInstance(long id, long peptideId, int validatedProteinSetCount, Float elutionTime) {
        m_id = id;
        m_validatedProteinSetCount = validatedProteinSetCount;
        m_elutionTime = elutionTime;
        m_peptideId = peptideId;
        
        m_bestPeptideMatch = null;
        m_peptide = null;
    }
	
    public long getId() {
        return m_id;
    }

    public long getPeptideId() {
    	return m_peptideId;
    }
    
    public void setPeptide(Peptide p) {
    	m_peptide = p;
    }
    
    public Peptide getPeptide() {
        return m_peptide;
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

	public ResultSummary getResultSummary() {
		return resultSummary;
	}

	public void setResultSummary(ResultSummary resultSummary) {
		this.resultSummary = resultSummary;
	}

}

package fr.proline.core.orm.msi.dto;

public class DProteinPTMSite {

	private DProteinMatch m_proteinMatch;
	private DPeptideMatch m_peptideMatch;
	private DPeptidePTM m_peptidePTM;
	
	private Double m_deltaMassPTM = null;
	
	public DProteinPTMSite(DProteinMatch proteinMatch, DPeptideMatch peptideMatch,  DPeptidePTM peptidePTM, Double deltaMassPTM) {
		m_proteinMatch = proteinMatch;
		m_peptideMatch = peptideMatch;
		m_peptidePTM = peptidePTM;
		m_deltaMassPTM = deltaMassPTM;
	}
	
	public DProteinMatch getPoteinMatch() {
		return m_proteinMatch;
	}
	
	public DPeptideMatch getPeptideMatch() {
		return m_peptideMatch;
	}
	
	public DPeptidePTM getPeptidePTM() {
		return m_peptidePTM;
	}
	
	public Double getDeltaMassPTM() {
		return m_deltaMassPTM;
	}
	
}

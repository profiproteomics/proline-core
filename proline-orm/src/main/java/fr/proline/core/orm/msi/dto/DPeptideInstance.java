package fr.proline.core.orm.msi.dto;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import fr.proline.core.orm.msi.Peptide;
import fr.proline.core.orm.msi.PeptideInstance;
import fr.proline.core.orm.msi.ResultSummary;
import fr.proline.core.orm.util.JsonSerializer;

public class DPeptideInstance {

	private long m_id = -1;
	private long m_peptideId = -1;
	private int m_validatedProteinSetCount = 0;
	private Float m_elutionTime = 0f;

	private Peptide m_peptide;
	private DPeptideMatch m_bestPeptideMatch;
	private ResultSummary resultSummary;
	private List<DPeptideMatch> m_peptideMatches;
	private Map<String, Object> m_properties = null;


	public DPeptideInstance(PeptideInstance peptideInstance) throws IOException {
		m_peptide = null;
		m_bestPeptideMatch = null;
		if (peptideInstance != null) {
			m_id = peptideInstance.getId();
			m_validatedProteinSetCount = peptideInstance.getValidatedProteinSetCount();
			m_elutionTime = peptideInstance.getElutionTime();
			m_peptideId = peptideInstance.getPeptide().getId();
			m_properties = (peptideInstance.getSerializedProperties() != null) ? JsonSerializer.getMapper().readValue(peptideInstance.getSerializedProperties(), Map.class) : null;
			if(peptideInstance.getPeptide()!= null)
				m_peptide = peptideInstance.getPeptide();
			if(peptideInstance.getTransientData() != null && peptideInstance.getTransientData().getBestPeptideMatch() != null)
				m_bestPeptideMatch = peptideInstance.getTransientData().getBestPeptideMatch();
		}
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

	public void setElutionTime(Float elutionTime) {
		this.m_elutionTime = elutionTime;
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

	public List<DPeptideMatch> getPeptideMatches() {
		return m_peptideMatches;
	}

	public void setPeptideMatches(List<DPeptideMatch> m_peptideMatches) {
		this.m_peptideMatches = m_peptideMatches;
	}

	public Map<String, Object> getProperties() {
		return m_properties;
	}
}

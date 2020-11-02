package fr.proline.core.orm.msi.dto;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import fr.proline.core.orm.util.JsonSerializer;

public class DProteinMatch {

	public static final String OBSERVABLE_PEPTIDE_COUNT_KEY = "observable_peptide_count";

	private long m_id;
	private String m_accession;
	private Float m_score;
	private int m_peptideCount;
	private long m_resultSetId;
	private String m_description;
	private String m_geneName;
	private DBioSequence m_bioSequence = null;
	private Integer m_observablePeptidesCount = null;

	private HashMap<Long, DPeptideSet> peptideSetMap = null;

	private DPeptideMatch[] m_peptideMatches;
	private long[] m_peptideMatchesId;

	public DProteinMatch(long id, String accession, Float score, int peptideCount, long resultSetId, String description, String serializedProperties) {
		m_id = id;
		m_accession = accession;
		m_score = score;
		m_peptideCount = peptideCount;
		m_resultSetId = resultSetId;
		m_description = description;
		setObservablePeptidesCountFromSerializedProperties(serializedProperties);
	}

	public DProteinMatch(long id, String accession, Float score, int peptideCount, long resultSetId, String description, String geneName, String serializedProperties) {
		m_id = id;
		m_accession = accession;
		m_score = score;
		m_peptideCount = peptideCount;
		m_resultSetId = resultSetId;
		m_description = description;
		m_geneName = geneName;
		setObservablePeptidesCountFromSerializedProperties(serializedProperties);
	}

	public DProteinMatch(
		long id,
		String accession,
		Float score,
		int peptideCount,
		long resultSetId,
		String description,
		String geneName,
		String serializedProperties,
		long peptideSetId,
		Float peptideSetScore,
		int sequenceCount,
		int peptideSetPeptideCount,
		int peptideMatchCount,
		long resultSummaryId) {
		m_id = id;
		m_accession = accession;
		m_score = score;
		m_peptideCount = peptideCount;
		m_resultSetId = resultSetId;
		m_description = description;
		m_geneName = geneName;
		setObservablePeptidesCountFromSerializedProperties(serializedProperties);

		DPeptideSet peptideSet = new DPeptideSet(peptideSetId, peptideSetScore, sequenceCount, peptideSetPeptideCount, peptideMatchCount, resultSummaryId);
		setPeptideSet(peptideSet.getResultSummaryId(), peptideSet);
	}

	private void setObservablePeptidesCountFromSerializedProperties(String serializedProperties) {

		m_observablePeptidesCount = null;

		try {
			if (serializedProperties != null) {
				Map<String, Object> pmqSerializedMap = JsonSerializer.getMapper().readValue(serializedProperties, Map.class);
				if (pmqSerializedMap != null) {
					Object value = pmqSerializedMap.get(OBSERVABLE_PEPTIDE_COUNT_KEY);
					if (value != null) {
						m_observablePeptidesCount = Integer.parseInt(value.toString());
					}
				}
			}
		} catch (IOException ie) {
		}
	}

	public Integer getObservablePeptidesCount() {
		return m_observablePeptidesCount;
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

	public String getGeneName() {
		return m_geneName;
	}

	public void setGeneName(String geneName) {
		this.m_geneName = geneName;
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
	}

	public DBioSequence getDBioSequence() {
		return m_bioSequence;
	}

	public boolean isDBiosequenceSet() {
		return m_bioSequence != null;
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

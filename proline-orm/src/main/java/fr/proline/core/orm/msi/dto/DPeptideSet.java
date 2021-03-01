package fr.proline.core.orm.msi.dto;

import fr.proline.core.orm.msi.PeptideSet;
import fr.proline.core.orm.util.JsonSerializer;

import java.io.IOException;
import java.util.Map;

public class DPeptideSet {

	private long m_id;
	private int m_sequenceCount;
	private int m_peptideCount;
	private int m_peptideMatchCount;
	private float m_score;
	private long m_resultSummaryId;

	private Map<String, Object> m_properties = null;
	private DPeptideInstance[] m_dPeptideInstances = null;

	public DPeptideSet(long id, float score, int sequenceCount, int peptideCount, int peptideMatchCount, long resultSummaryId) {
		super();
		this.m_id = id;
		this.m_score = score;
		this.m_sequenceCount = sequenceCount;
		this.m_peptideCount = peptideCount;
		this.m_peptideMatchCount = peptideMatchCount;
		this.m_resultSummaryId = resultSummaryId;
	}

	public DPeptideSet(long id, float score, int sequenceCount, int peptideCount, int peptideMatchCount, long resultSummaryId, String serializedProperties) throws IOException {
		this(id, score, sequenceCount, peptideCount, peptideMatchCount, resultSummaryId);
		if (serializedProperties != null)
			m_properties = JsonSerializer.getMapper().readValue(serializedProperties, Map.class);
	}

	public DPeptideSet(PeptideSet ps) {
		super();
		this.m_id = ps.getId();
		this.m_score = ps.getScore();
		this.m_sequenceCount = ps.getSequenceCount();
		this.m_peptideCount = ps.getPeptideCount();
		this.m_peptideMatchCount = ps.getPeptideMatchCount();
		this.m_resultSummaryId = ps.getResultSummaryId();
	}

	/**
	  * Get of Transient peptideInstances, Must be set by the user first.
	  * 
	  * @return
	  */
	public DPeptideInstance[] getPeptideInstances() {
		return m_dPeptideInstances;
	}

	public void setPeptideInstances(DPeptideInstance[] dpeptideInstances) {
		this.m_dPeptideInstances = dpeptideInstances;
	}

	public long getId() {
		return m_id;
	}

	public int getSequenceCount() {
		return m_sequenceCount;
	}

	public int getPeptideCount() {
		return m_peptideCount;
	}

	public int getPeptideMatchCount() {
		return m_peptideMatchCount;
	}

	public float getScore() {
		return m_score;
	}

	public long getResultSummaryId() {
		return m_resultSummaryId;
	}

	public Map<String, Object> getPropertiesAsMap() {
		return m_properties;
	}
}

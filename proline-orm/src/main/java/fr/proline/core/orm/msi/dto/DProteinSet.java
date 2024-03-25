package fr.proline.core.orm.msi.dto;

import java.util.Map;

import fr.proline.core.orm.util.JsonSerializer;

/**
 *
 * @author JM235353
 */
public class DProteinSet {

	private long m_id;
	private long m_resultSummaryId;
	private long m_typicalProteinMatchId;

	private DProteinMatch m_typicalProteinMatch;

	private Integer m_spectralCount;
	private Integer m_specificSpectralCount;

	private String m_serializedProperties = null;
	private Map<String, Object> m_serializedPropertiesMap = null;

	private Integer m_sameSetCount;
	private Integer m_subSetCount;
	private String[] m_sameSubSetNames = null;

	private DProteinMatch[] m_sameSet = null; // loaded later than sameSetCount & m_sameSubSetNames
	private DProteinMatch[] m_subSet = null; // loaded later than subSetCount & m_sameSubSetNames

	public DProteinSet(long id, long typicalProteinMatchId, long resultSummaryId) {
		m_id = id;
		m_typicalProteinMatchId = typicalProteinMatchId;
		m_resultSummaryId = resultSummaryId;

		m_typicalProteinMatch = null;
		m_spectralCount = null;
		m_specificSpectralCount = null;
		m_sameSetCount = null;
		m_subSetCount = null;
		m_sameSubSetNames = null;
	}

	public DProteinSet(long id, long typicalProteinMatchId, long resultSummaryId, String serializedProperties) {
		m_id = id;
		m_typicalProteinMatchId = typicalProteinMatchId;
		m_resultSummaryId = resultSummaryId;
		m_serializedProperties = serializedProperties;

		m_typicalProteinMatch = null;
		m_spectralCount = null;
		m_specificSpectralCount = null;
		m_sameSetCount = null;
		m_subSetCount = null;
		m_sameSubSetNames = null;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		DProteinSet that = (DProteinSet) o;
		return m_id == that.m_id;
	}

	@Override
	public int hashCode() {
		return String.valueOf(m_id).hashCode();
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

	public Integer getSpectralCount() {
		return m_spectralCount;
	}

	public void setSpectralCount(Integer spectralCount) {
		m_spectralCount = spectralCount;
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

	public String[] getSameSubSetNames() {
		return m_sameSubSetNames;
	}

	public void setSameSubSetNames(String[] sameSubSetNames) {
		this.m_sameSubSetNames = sameSubSetNames;
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

	@SuppressWarnings("unchecked")
	public Map<String, Object> getSerializedPropertiesAsMap() throws Exception {
		if ((m_serializedPropertiesMap == null) && (m_serializedProperties != null)) {
			m_serializedPropertiesMap = JsonSerializer.getMapper().readValue(m_serializedProperties, Map.class);
		}
		return m_serializedPropertiesMap;
	}

	public void setSerializedPropertiesAsMap(Map<String, Object> serializedPropertiesMap) throws Exception {
		m_serializedPropertiesMap = serializedPropertiesMap;
		m_serializedProperties = JsonSerializer.getMapper().writeValueAsString(serializedPropertiesMap);
	}


}

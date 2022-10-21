package fr.proline.core.orm.msi.dto;

import java.io.IOException;
import java.util.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import fr.profi.util.CollectionUtils;
import fr.proline.core.orm.msi.Peptide;
import fr.proline.core.orm.msi.SequenceMatch;
import fr.proline.core.orm.util.JsonSerializer;

/**
 *
 * @author JM235353
 */
public class DPeptideMatch implements Comparable<DPeptideMatch> {

	public static final String ISOTOPE_OFFSET_KEY = "isotope_offset";

	private long m_id;
	private Integer m_rank;
	private int m_charge;
	private Float m_deltaMoz;
	private double m_experimentalMoz;
	private int m_missedCleavage;
	private Float m_score;
	private Peptide m_peptide;
	private long m_resultSetId;

	private DMsQuery m_msQuery;
	private boolean m_msQuerySet;

	private SequenceMatch m_sequenceMatch;

	private DProteinMatch[] m_proteinMatchArray = null;
	private String[] m_proteinSetStringArray = null;

	private Integer m_cdPrettyRank;
	private Integer m_sdPrettyRank;

	private Float m_retentionTime; // Spectrum.firstTime

	private String m_sourceDatFile = null; // dat file of the best PSM for merged dataset

	private Boolean m_isDecoy;
	private Boolean m_isValidated;

	private String m_serializedProperties = null;

	private ArrayList<DPeptidePTM> m_peptidePTMArray = null;
	private DPtmSiteProperties m_ptmSiteProperties = null;
	private Integer m_isotopeOff = 0;
	private Map<String, Object> m_propertiesAsMap = null;

	public DPeptideMatch(
		long id,
		Integer rank,
		int charge,
		Float deltaMoz,
		double experimentalMoz,
		int missedCleavage,
		Float score,
		long resultSetId,
		Integer cdPrettyRank,
		Integer sdPrettyRank,
		String serializedProperties) {
		m_id = id;
		m_rank = rank;
		m_charge = charge;
		m_deltaMoz = deltaMoz;
		m_experimentalMoz = experimentalMoz;
		m_missedCleavage = missedCleavage;
		m_score = score;
		m_resultSetId = resultSetId;

		m_peptide = null;
		m_msQuery = null;
		m_msQuerySet = false;
		m_sequenceMatch = null;
		m_proteinMatchArray = null;

		m_cdPrettyRank = cdPrettyRank;
		m_sdPrettyRank = sdPrettyRank;
		m_isDecoy = false;
		m_isValidated = false;
		m_serializedProperties = serializedProperties;
		setIsotopeOffFromSerializedProperties();

	}

	public DPeptideMatch(
		long id,
		Integer rank,
		int charge,
		Float deltaMoz,
		double experimentalMoz,
		int missedCleavage,
		Float score,
		long resultSetId,
		Integer cdPrettyRank,
		Integer sdPrettyRank) {
		m_id = id;
		m_rank = rank;
		m_charge = charge;
		m_deltaMoz = deltaMoz;
		m_experimentalMoz = experimentalMoz;
		m_missedCleavage = missedCleavage;
		m_score = score;
		m_resultSetId = resultSetId;

		m_peptide = null;
		m_msQuery = null;
		m_msQuerySet = false;
		m_sequenceMatch = null;
		m_proteinMatchArray = null;

		m_cdPrettyRank = cdPrettyRank;
		m_sdPrettyRank = sdPrettyRank;
		m_isDecoy = false;
		m_isValidated = false;
	}

	public DPeptideMatch(
		long id,
		Integer rank,
		int charge,
		Float deltaMoz,
		double experimentalMoz,
		int missedCleavage,
		Float score,
		long resultSetId,
		Integer cdPrettyRank,
		Integer sdPrettyRank,
		Boolean isDecoy) {
		m_id = id;
		m_rank = rank;
		m_charge = charge;
		m_deltaMoz = deltaMoz;
		m_experimentalMoz = experimentalMoz;
		m_missedCleavage = missedCleavage;
		m_score = score;
		m_resultSetId = resultSetId;

		m_peptide = null;
		m_msQuery = null;
		m_msQuerySet = false;
		m_sequenceMatch = null;
		m_proteinMatchArray = null;

		m_cdPrettyRank = cdPrettyRank;
		m_sdPrettyRank = sdPrettyRank;
		m_isDecoy = isDecoy;

	}

	public DPeptideMatch(
		long id,
		Integer rank,
		int charge,
		Float deltaMoz,
		double experimentalMoz,
		int missedCleavage,
		Float score,
		long resultSetId,
		Integer cdPrettyRank,
		Integer sdPrettyRank,
		Boolean isDecoy,
		String serializedProperties) {
		m_id = id;
		m_rank = rank;
		m_charge = charge;
		m_deltaMoz = deltaMoz;
		m_experimentalMoz = experimentalMoz;
		m_missedCleavage = missedCleavage;
		m_score = score;
		m_resultSetId = resultSetId;

		m_peptide = null;
		m_msQuery = null;
		m_msQuerySet = false;
		m_sequenceMatch = null;
		m_proteinMatchArray = null;

		m_cdPrettyRank = cdPrettyRank;
		m_sdPrettyRank = sdPrettyRank;
		m_isDecoy = isDecoy;
		m_serializedProperties = serializedProperties;
		setIsotopeOffFromSerializedProperties();

	}

	public long getId() {
		return m_id;
	}

	public void setPeptide(Peptide p) {
		m_peptide = p;
	}

	public Peptide getPeptide() {
		return m_peptide;
	}

	public String getSerializedProperties() {
		return m_serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		m_serializedProperties = serializedProperties;
		setIsotopeOffFromSerializedProperties();
	}

	public void setSequenceMatch(SequenceMatch sequenceMatch) {
		m_sequenceMatch = sequenceMatch;
	}

	public SequenceMatch getSequenceMatch() {
		return m_sequenceMatch;
	}

	public Float getScore() {
		return m_score;
	}

	public Integer getRank() {
		return m_rank;
	}

	public Integer getCDPrettyRank() {
		return m_cdPrettyRank;
	}

	public Integer getSDPrettyRank() {
		return m_sdPrettyRank;
	}

	public int getCharge() {
		return m_charge;
	}

	public double getExperimentalMoz() {
		return m_experimentalMoz;
	}

	public Float getDeltaMoz() {
		return m_deltaMoz;
	}

	public int getMissedCleavage() {
		return m_missedCleavage;
	}

	public long getResultSetId() {
		return m_resultSetId;
	}

	public void setMsQuery(DMsQuery msQuery) {
		m_msQuery = msQuery;
		m_msQuerySet = true;
	}

	public DMsQuery getMsQuery() {
		return m_msQuery;
	}

	public boolean isMsQuerySet() {
		return m_msQuerySet;
	}

	public String[] getProteinSetStringArray() {
		return m_proteinSetStringArray;
	}

	public void setProteinSetStringArray(String[] s) {
		m_proteinSetStringArray = s;
	}

	public DProteinMatch[] getProteinMatches() {
		return m_proteinMatchArray;
	}

	public void setProteinMatches(DProteinMatch[] proteinMatchArray) {
		m_proteinMatchArray = proteinMatchArray;
	}

	@Override
	public int compareTo(DPeptideMatch peptideMatch) {
		if (m_peptide == null) {
			return 0;
		}
		if (peptideMatch.m_peptide == null) {
			return 0;
		}
		return m_peptide.compareTo(peptideMatch.m_peptide);
	}

	public Float getRetentionTime() {
		return m_retentionTime;
	}

	public void setRetentionTime(Float retentionTime) {
		m_retentionTime = retentionTime;
	}

	public String getSourceDatFile() {
		return m_sourceDatFile;
	}

	public void setSourceDatFile(String sourceDatFile) {
		m_sourceDatFile = sourceDatFile;
	}

	public Boolean isDecoy() {
		return m_isDecoy;
	}

	public void setDecoy(Boolean isDecoy) {
		m_isDecoy = isDecoy;
	}

	public Boolean isValidated() {
		return m_isValidated;
	}

	public void setValidated(Boolean isValidated) {
		m_isValidated = isValidated;
	}

	public ArrayList<DPeptidePTM> getPeptidePTMArray() {
		return m_peptidePTMArray;
	}

	public Integer getIsotopeOffset() {
		return m_isotopeOff;
	}

	public void setPeptidePTMArray(ArrayList<DPeptidePTM> peptidePTMArray) {
		m_peptidePTMArray = peptidePTMArray;
	}

	public DPtmSiteProperties getPtmSiteProperties() {
		return m_ptmSiteProperties;
	}

	public void setPtmSiteProperties(DPtmSiteProperties m_ptmSiteProperties) {
		this.m_ptmSiteProperties = m_ptmSiteProperties;
	}

	private void setIsotopeOffFromSerializedProperties() {
		m_isotopeOff = 0;
		try {
			if (m_serializedProperties != null) {
				Map<String, Object> pmqSerializedMap = JsonSerializer.getMapper().readValue(m_serializedProperties, Map.class);
				if (pmqSerializedMap != null) {
					Object value = pmqSerializedMap.get(ISOTOPE_OFFSET_KEY);
					if (value != null) {
						m_isotopeOff = Integer.parseInt(value.toString());
					}
				}
			}
		} catch (IOException ie) {
		}
	}

	public Map<String, Object> getPropertiesAsMap() {
		if (m_propertiesAsMap == null && m_serializedProperties != null) {
			m_propertiesAsMap = new HashMap<>();
			try {
				m_propertiesAsMap = JsonSerializer.getMapper().readValue(m_serializedProperties, Map.class);
			} catch (JsonProcessingException e) {
				m_propertiesAsMap = null;
			}
		}
		return m_propertiesAsMap;
	}

	public Boolean hasAmbiguousSeq() {
		if (m_peptide == null )
			return null;
		boolean hasAmbiguity = false;
		if(getPropertiesAsMap() != null && getPropertiesAsMap().containsKey("mascot_properties")) {
			Map<String, Object> mascotProperties = (Map<String, Object>) getPropertiesAsMap().get("mascot_properties");
			if (mascotProperties.containsKey("ambiguity_string")) {
				hasAmbiguity = true;
			}
		}
		return hasAmbiguity;
	}


	/**
	 * Return Peptide Sequence with ambiguous AA replaced.
	 * If there is no ambiguous AA or Peptide is not specified in this object, an empty String will be returned
	 *
	 * @return
	 */
	public String getDisambiguatedSeq() {
		if (m_peptide != null && hasAmbiguousSeq() ) {
			String ambiguityInfo = (String) ((Map<String, Object>) getPropertiesAsMap().get("mascot_properties")).get("ambiguity_string");

			List<Integer> indexes = new ArrayList<>();
			List<Character> subsitituteChars = new ArrayList<>();

			List<String> ambiguityChars = Arrays.asList(ambiguityInfo.split(","));
			CollectionUtils.createSlidingWindow(ambiguityChars, 3).forEach(substInfo -> {
				if (substInfo.size() == 3) {
					indexes.add(Integer.valueOf(substInfo.get(0)));
					subsitituteChars.add(substInfo.get(2).toCharArray()[0]);
				}
			});
			StringBuilder seqB = new StringBuilder();
			String sequence = m_peptide.getSequence();
			for (int i = 0; i < sequence.length(); i++) {
				if (indexes.contains(i + 1))
					seqB.append(subsitituteChars.get(indexes.indexOf(i + 1)));
				else
					seqB.append(sequence.toCharArray()[i]);
			}
			return seqB.toString();
		} else
			return "";

	}
}

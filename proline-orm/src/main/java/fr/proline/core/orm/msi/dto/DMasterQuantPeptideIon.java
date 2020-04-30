package fr.proline.core.orm.msi.dto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import fr.proline.core.orm.msi.MasterQuantComponent;
import fr.proline.core.orm.msi.MasterQuantPeptideIon;
import fr.proline.core.orm.msi.Peptide;
import fr.proline.core.orm.msi.PeptideInstance;
import fr.proline.core.orm.msi.PeptideMatch;
import fr.proline.core.orm.msi.ResultSummary;
import fr.proline.core.orm.util.JsonSerializer;

/**
 *
 * @author JM235353
 */
public class DMasterQuantPeptideIon {

	private long m_id;
	private ResultSummary m_resultSummary;
	private DPeptideInstance m_peptideInstance;
	private Map<Long, DQuantPeptideIon> m_quantPeptideIonByQchIds = null;
	private MasterQuantComponent m_masterQuantComponent;
	private DPeptideMatch m_bestPeptideMatch;

	private int m_charge;
	private double m_moz;
	private float m_elutionTime;
	private Long m_lcmsMasterFeatureId;
	private DPeptideMatch m_representativePepMatch;
	private Boolean m_usedInPeptide;
	private PepIonStatus m_pepIonStatus;

	public enum PepIonStatus {
		UNKNOWN, //should not stay in this state !
		INVALIDATED,
		VALIDATED,
		USED_VALIDATED,
		UNUSED_VALIDATED
	}

	public DMasterQuantPeptideIon() {
		m_pepIonStatus= PepIonStatus.UNKNOWN;
	}

	public DMasterQuantPeptideIon(MasterQuantPeptideIon mqpi) {
		m_id = mqpi.getId();
		m_resultSummary = mqpi.getResultSummary();
		m_quantPeptideIonByQchIds = mqpi.getQuantPeptideIonByQchIds();
		m_masterQuantComponent = mqpi.getMasterQuantComponent();
		m_charge = mqpi.getCharge();
		m_moz = mqpi.getMoz();
		m_elutionTime = mqpi.getElutionTime();
		m_lcmsMasterFeatureId = mqpi.getLcmsMasterFeatureId();
		updatePepIonStatus();
	}

	public DMasterQuantPeptideIon(PeptideInstance pi, MasterQuantPeptideIon mqpi, Peptide p, PeptideMatch bpm) {
		m_id = mqpi.getId();
		m_resultSummary = mqpi.getResultSummary();
		m_quantPeptideIonByQchIds = mqpi.getQuantPeptideIonByQchIds();
		m_masterQuantComponent = mqpi.getMasterQuantComponent();
		m_charge = mqpi.getCharge();
		m_moz = mqpi.getMoz();
		m_elutionTime = mqpi.getElutionTime();
		m_peptideInstance = new DPeptideInstance(pi.getId(), p.getId(), pi.getValidatedProteinSetCount(), pi.getElutionTime());
		m_peptideInstance.setPeptide(p);
		m_bestPeptideMatch = new DPeptideMatch(bpm.getId(), bpm.getRank(), bpm.getCharge(), bpm.getDeltaMoz(), bpm.getExperimentalMoz(),
			bpm.getMissedCleavage(), bpm.getScore(), bpm.getResultSet().getId(), bpm.getCDPrettyRank(), bpm.getSDPrettyRank(), bpm.getIsDecoy(),
			bpm.getSerializedProperties());
		m_bestPeptideMatch.setPeptide(p);
		m_lcmsMasterFeatureId = mqpi.getLcmsMasterFeatureId();
		updatePepIonStatus();
	}

	public long getId() {
		return m_id;
	}

	public void setId(final long pId) {
		m_id = pId;
	}

	public ResultSummary getResultSummary() {
		return m_resultSummary;
	}

	public void setResultSummary(ResultSummary resultSummary) {
		this.m_resultSummary = resultSummary;
	}

	public DPeptideInstance getPeptideInstance() {
		return m_peptideInstance;
	}

	public void setPeptideInstance(final DPeptideInstance pPeptideInstance) {
		m_peptideInstance = pPeptideInstance;
	}

	public int getCharge() {
		return m_charge;
	}

	public void setCharge(int charge) {
		this.m_charge = charge;
	}

	public double getMoz() {
		return m_moz;
	}

	public void setMoz(final double pMoz) {
		m_moz = pMoz;
	}

	public float getElutionTime() {
		return m_elutionTime;
	}

	public void setElutionTime(final float pElutionTime) {
		m_elutionTime = pElutionTime;
	}

	public Map<Long, DQuantPeptideIon> getQuantPeptideIonByQchIds() {
		return m_quantPeptideIonByQchIds;
	}

	public Long getLcmsMasterFeatureId() {
		return m_lcmsMasterFeatureId;
	}

	public void setLcmsMasterFeatureId(final Long pLcmsMasterFeatureId) {
		m_lcmsMasterFeatureId = pLcmsMasterFeatureId;
	}

	public void setQuantPeptideIonByQchIds(Map<Long, DQuantPeptideIon> quantPeptideIonByQchIds) {
		this.m_quantPeptideIonByQchIds = quantPeptideIonByQchIds;
	}

	public MasterQuantComponent getMasterQuantComponent() {
		return m_masterQuantComponent;
	}

	public void setMasterQuantComponent(MasterQuantComponent masterQuantComponent) {
		this.m_masterQuantComponent = masterQuantComponent;
		updatePepIonStatus();
	}

	public DPeptideMatch getBestPeptideMatch() {
		return m_bestPeptideMatch;
	}

	public void setBestPeptideMatch(DPeptideMatch m_bestPeptideMatch) {
		this.m_bestPeptideMatch = m_bestPeptideMatch;
	}

	public DPeptideMatch getRepresentativePepMatch() {
		if(m_representativePepMatch == null)
			return getBestPeptideMatch();
		return m_representativePepMatch;
	}

	public void setRepresentativePepMatch(DPeptideMatch representativePepMatch) {
		this.m_representativePepMatch = representativePepMatch;
	}

	public Map<Long, DQuantPeptideIon> parseQuantPeptideIonFromProperties(String quantPeptideIonData) {

		try {
			ObjectMapper objectMapper = JsonSerializer.getMapper();
			objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
			List<DQuantPeptideIon> quantPepIons = objectMapper.readValue(quantPeptideIonData, new TypeReference<List<DQuantPeptideIon>>() {
			});

			m_quantPeptideIonByQchIds = new HashMap<Long, DQuantPeptideIon>();
			if (quantPepIons != null) {
				for (int i = 0; i < quantPepIons.size(); i++) {
					DQuantPeptideIon nextQuantPepIon = quantPepIons.get(i);
					if (nextQuantPepIon != null) {
						m_quantPeptideIonByQchIds.put(nextQuantPepIon.getQuantChannelId(), nextQuantPepIon);
					}
				}
			}

		} catch (Exception e) {
			//LOG.warn("Error Parsing DQuantPeptideIon ", e);
			//LOG.warn("quantPeptideIonData= " + quantPeptideIonData);
			m_quantPeptideIonByQchIds = null;
		}

		return m_quantPeptideIonByQchIds;
	}

	public void setUsedInPeptide(boolean isUsed){
		if(m_usedInPeptide == null ||  !m_usedInPeptide.equals(isUsed)) {
			m_usedInPeptide = isUsed;
			updatePepIonStatus();
		}
	}

	public Boolean isUsedInPeptide(){
		return m_usedInPeptide;
	}

	private void updatePepIonStatus() {

		if(m_masterQuantComponent == null) {
			m_pepIonStatus = PepIonStatus.UNKNOWN;
			return;
		}

		boolean selected =  m_masterQuantComponent.getSelectionLevel()>=2;
		if(!selected)
			m_pepIonStatus = PepIonStatus.INVALIDATED;
		else if( m_usedInPeptide== null)
			m_pepIonStatus = PepIonStatus.VALIDATED;
		else if(m_usedInPeptide)
			m_pepIonStatus = PepIonStatus.USED_VALIDATED;
		else
			m_pepIonStatus = PepIonStatus.UNUSED_VALIDATED;
	}

	public PepIonStatus getPepIonStatus(){
		return m_pepIonStatus;
	}
}

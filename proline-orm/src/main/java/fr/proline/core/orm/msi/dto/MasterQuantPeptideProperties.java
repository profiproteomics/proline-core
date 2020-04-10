package fr.proline.core.orm.msi.dto;

import java.util.*;

public class MasterQuantPeptideProperties {

	public MasterQuantPeptideProperties() {
		super();
	}

	String m_discardingReason;
	List<Long> m_mqProtSetIds;
	HashMap<String, MasterQuantPeptideProfile> m_mqPepProfileByGroupSetupNumber;
	PepIonAbundanceSummarizingConfig m_pepIonAbSummarizingConfig;

	public List<Long> getMqProtSetIds() {
		return m_mqProtSetIds;
	}

	public void setMqProtSetIds(List<Long> mqProtSetIds) {
		this.m_mqProtSetIds = mqProtSetIds;
	}

	public String getDiscardingReason() {
		return m_discardingReason;
	}

	public void setDiscardingReason(String discardingReason) {
		this.m_discardingReason = discardingReason;
	}

	public HashMap<String, MasterQuantPeptideProfile> getMqPepProfileByGroupSetupNumber() {
		return m_mqPepProfileByGroupSetupNumber;
	}

	public void setMqPepProfileByGroupSetupNumber(HashMap<String, MasterQuantPeptideProfile> mqPepProfileByGroupSetupNumber) {
		this.m_mqPepProfileByGroupSetupNumber = mqPepProfileByGroupSetupNumber;
	}

	public PepIonAbundanceSummarizingConfig getMqPepIonAbundanceSummarizingConfig() {
		return m_pepIonAbSummarizingConfig;
	}

	public void setMqPepIonAbundanceSummarizingConfig(PepIonAbundanceSummarizingConfig m_pepIonAbSummarizingConfig) {
		this.m_pepIonAbSummarizingConfig = m_pepIonAbSummarizingConfig;
	}

	static class MasterQuantPeptideProfile {
		List<ComputedRatio> ratios;

		public MasterQuantPeptideProfile() {
			super();
			// TODO Auto-generated constructor stub
		}

		public List<ComputedRatio> getRatios() {
			return ratios;
		}

		public void setRatios(List<ComputedRatio> ratios) {
			this.ratios = ratios;
		}
	}

	public class PepIonAbundanceSummarizingConfig {

		String methodName;
		Map<Long,Integer> mqPeptideIonSelLevelById;

		public String getMethodName() {
			return methodName;
		}

		public void setMethodName(String methodName) {
			this.methodName = methodName;
		}

		public Map<Long,Integer> getmMqPeptideIonSelLevelById() {
			return mqPeptideIonSelLevelById;
		}

		public void setMqPeptideIonSelLevelById(Map<Long,Integer> mqPepIonSelLevelByIds) {
			mqPeptideIonSelLevelById = mqPepIonSelLevelByIds;
		}

		public List<Long> getSelectedMasterQuantPeptideIonIds() {
			ArrayList<Long> selectedMQPepIonIds = new ArrayList<>();
			Iterator<Long>  mqPepIonSelectionlevelIt= mqPeptideIonSelLevelById.keySet().iterator();
			while (mqPepIonSelectionlevelIt.hasNext()){
				Long nextMQPId = mqPepIonSelectionlevelIt.next();
				if(mqPeptideIonSelLevelById.get(nextMQPId) >= 2)
					selectedMQPepIonIds.add(nextMQPId);
			}
			return selectedMQPepIonIds;
		}

	}
}

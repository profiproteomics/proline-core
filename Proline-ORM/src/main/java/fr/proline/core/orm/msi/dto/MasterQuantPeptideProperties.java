package fr.proline.core.orm.msi.dto;

import java.util.HashMap;
import java.util.List;


public class MasterQuantPeptideProperties {
	
	public MasterQuantPeptideProperties() {
		super();
	}

	List<Long> m_mqProtSetIds;
	HashMap<String, List<MasterQuantPeptideProfile>>  m_mqPepProfileByGroupSetupNumber ;
	
	
	public List<Long> getMQProtSetIds() {
		return m_mqProtSetIds;
	}

	public void setMQProtSetIds(List<Long> mqProtSetIds) {
		this.m_mqProtSetIds = mqProtSetIds;
	}

	public HashMap<String, List<MasterQuantPeptideProfile>> getMqProtSetProfilesByGroupSetupNumber() {
		return m_mqPepProfileByGroupSetupNumber;
	}

	public void setMqProtSetProfilesByGroupSetupNumber(HashMap<String, List<MasterQuantPeptideProfile>> mqPepProfileByGroupSetupNumber) {
		this.m_mqPepProfileByGroupSetupNumber = mqPepProfileByGroupSetupNumber;
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
}

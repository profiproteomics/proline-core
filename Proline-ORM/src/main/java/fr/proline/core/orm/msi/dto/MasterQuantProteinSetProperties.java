package fr.proline.core.orm.msi.dto;

import java.util.HashMap;
import java.util.List;

public class MasterQuantProteinSetProperties {

	public MasterQuantProteinSetProperties() {
		super();
	}
	
	HashMap<String, List<MasterQuantProteinSetProfile>>  mqProtSetProfilesByGroupSetupNumber ;
	  
	List<Long> selectedMasterQuantPeptideIds;
	           
	List<Long> selectedMasterQuantPeptideIonIds;		
		
	public HashMap<String, List<MasterQuantProteinSetProfile>> getMqProtSetProfilesByGroupSetupNumber() {
		return mqProtSetProfilesByGroupSetupNumber;
	}

	public void setMqProtSetProfilesByGroupSetupNumber(
			HashMap<String, List<MasterQuantProteinSetProfile>> m_mqProtSetProfilesByGroupSetupNumber) {
		this.mqProtSetProfilesByGroupSetupNumber = m_mqProtSetProfilesByGroupSetupNumber;
	}

	public List<Long> getSelectedMasterQuantPeptideIds() {
		return selectedMasterQuantPeptideIds;
	}

	public void setSelectedMasterQuantPeptideIds(List<Long> selectedMasterQuantPeptideIds) {
		this.selectedMasterQuantPeptideIds = selectedMasterQuantPeptideIds;
	}

	public List<Long> getSelectedMasterQuantPeptideIonIds() {
		return selectedMasterQuantPeptideIonIds;
	}

	public void setSelectedMasterQuantPeptideIonIds(List<Long> selectedMasterQuantPeptideIonIds) {
		this.selectedMasterQuantPeptideIonIds = selectedMasterQuantPeptideIonIds;
	}

public static class MasterQuantProteinSetProfile {
		List<Float> abundances;
		List<ComputedRatio> ratios;
		List<Long> mqPeptideIds;
		
		
		
		public MasterQuantProteinSetProfile() {
			super();
			// TODO Auto-generated constructor stub
		}
		public List<Float> getAbundances() {
			return abundances;
		}
		public void setAbundances(List<Float> abundances) {
			this.abundances = abundances;
		}
		public List<ComputedRatio> getRatios() {
			return ratios;
		}
		public void setRatios(List<ComputedRatio> ratios) {
			this.ratios = ratios;
		}
		public List<Long> getMqPeptideIds() {
			return mqPeptideIds;
		}
		public void setMqPeptideIds(List<Long> mqPeptideIds) {
			this.mqPeptideIds = mqPeptideIds;
		}

	}
}
	

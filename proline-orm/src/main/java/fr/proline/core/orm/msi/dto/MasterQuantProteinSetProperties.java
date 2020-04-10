package fr.proline.core.orm.msi.dto;

import java.util.*;

public class MasterQuantProteinSetProperties {

	public MasterQuantProteinSetProperties() {
		super();
	}

	HashMap<String, List<MasterQuantProteinSetProfile>> mqProtSetProfilesByGroupSetupNumber;
	Boolean selectionChanged;
	Map<Long,Integer> mqPeptideIonSelLevelById;
	Map<Long,Integer> mqPeptideSelLevelById;
	List<Long> selectedMasterQuantPeptideIds;
	List<Long> selectedMasterQuantPeptideIonIds;

	public HashMap<String, List<MasterQuantProteinSetProfile>> getMqProtSetProfilesByGroupSetupNumber() {
		return mqProtSetProfilesByGroupSetupNumber;
	}

	public void setMqProtSetProfilesByGroupSetupNumber(
		HashMap<String, List<MasterQuantProteinSetProfile>> m_mqProtSetProfilesByGroupSetupNumber) {
		this.mqProtSetProfilesByGroupSetupNumber = m_mqProtSetProfilesByGroupSetupNumber;
	}

	public Map<Long, Integer> getMqPeptideIonSelLevelById() {
		return mqPeptideIonSelLevelById;
	}

	public void setMqPeptideIonSelLevelById(Map<Long, Integer> mqPeptideIonSelLevelById) {
		this.mqPeptideIonSelLevelById = mqPeptideIonSelLevelById;
	}

	public Map<Long, Integer> getMqPeptideSelLevelById() {
		return mqPeptideSelLevelById;
	}

	public void setMqPeptideSelLevelById(Map<Long, Integer> mqPeptideSelLevelById) {
		this.mqPeptideSelLevelById = mqPeptideSelLevelById;
	}

	public Boolean getSelectionChanged() {
		return selectionChanged;
	}

	public void setSelectionChanged(Boolean selectionChanged) {
		this.selectionChanged = selectionChanged;
	}

	public List<Long> getSelectedMasterQuantPeptideIds() {
		ArrayList<Long> selectedMQPepIds = new ArrayList<>();
		Iterator<Long> mqPepSelectionlevelIt= mqPeptideSelLevelById.keySet().iterator();
		while (mqPepSelectionlevelIt.hasNext()){
			Long nextMQPId = mqPepSelectionlevelIt.next();
			if(mqPeptideSelLevelById.get(nextMQPId) >= 2)
				selectedMQPepIds.add(nextMQPId);
		}
		return selectedMQPepIds;
	}


	public List<Long> getSelectedMasterQuantPeptideIonIds() {
		ArrayList<Long> selectedMQPepIonIds = new ArrayList<>();
		Iterator<Long> mqPepIonSelectionlevelIt= mqPeptideIonSelLevelById.keySet().iterator();
		while (mqPepIonSelectionlevelIt.hasNext()){
			Long nextMQPId = mqPepIonSelectionlevelIt.next();
			if(mqPeptideIonSelLevelById.get(nextMQPId) >= 2)
				selectedMQPepIonIds.add(nextMQPId);
		}
		return selectedMQPepIonIds;
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

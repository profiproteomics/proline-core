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
		return getSelectedMasterQuantObjectIds(mqPeptideSelLevelById);
	}


	public List<Long> getSelectedMasterQuantPeptideIonIds() {
		return getSelectedMasterQuantObjectIds(mqPeptideIonSelLevelById);
	}

	private List<Long> getSelectedMasterQuantObjectIds(Map<Long,Integer> quandCompoSelLevelById) {
		ArrayList<Long> selectedMQCompoIds = new ArrayList<>();
		if(quandCompoSelLevelById != null) {
			Iterator<Long> mqCompoSelectionlevelIt = quandCompoSelLevelById.keySet().iterator();
			while (mqCompoSelectionlevelIt.hasNext()) {
				Long nextMQCompId = mqCompoSelectionlevelIt.next();
				if (quandCompoSelLevelById.get(nextMQCompId) >= 2)
					selectedMQCompoIds.add(nextMQCompId);
			}
		}
		return selectedMQCompoIds;
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

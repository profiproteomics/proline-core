package fr.proline.core.orm.msi.dto;


import fr.proline.core.orm.msi.MasterQuantPeptideIon;

/**
 * MasterQuantPeptideIon with peptideInstance information
 * @author MB243701
 *
 */
public class DMasterQuantPeptideIon extends MasterQuantPeptideIon {

	private static final long serialVersionUID = 1L;

	//DPeptideInstance to provide access to DPeptideMatch
	private DPeptideInstance m_dPeptideInstance;
		
	public DMasterQuantPeptideIon() {
		
	}
	
	public DMasterQuantPeptideIon(MasterQuantPeptideIon o) {
		this.setId(o.getId());
		this.setCharge(o.getCharge());
		this.setMoz(o.getMoz());
		this.setElutionTime(o.getElutionTime());
		this.setScanNumber(o.getScanNumber());
		this.setPeptideMatchCount(o.getPeptideMatchCount());
		this.setSerializedProperties(o.getSerializedProperties());
		this.setLcmsMasterFeatureId(o.getLcmsMasterFeatureId());
		this.setMasterQuantPeptideId(o.getMasterQuantPeptideId());
		this.setPeptideId(o.getPeptideId());
		this.setPeptideInstanceId(o.getPeptideInstanceId());
		this.setBestPeptideMatchId(o.getBestPeptideMatchId());
		this.setUnmodifiedPeptideIonId(o.getUnmodifiedPeptideIonId());
		this.setMasterQuantComponent(o.getMasterQuantComponent());
		this.setMasterQuantReporterIons(o.getMasterQuantReporterIons());
		this.setResultSummary(o.getResultSummary());
	}

	public DPeptideInstance getPeptideInstance() {
		return m_dPeptideInstance;
	}

	public void setPeptideInstance(DPeptideInstance peptideInstance) {
		this.m_dPeptideInstance = peptideInstance;
	}

}

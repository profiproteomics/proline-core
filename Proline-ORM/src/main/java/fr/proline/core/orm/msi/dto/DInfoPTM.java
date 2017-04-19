package fr.proline.core.orm.msi.dto;

import java.util.HashMap;

public class DInfoPTM {
	
	private long m_idPtmSpecificity;
	private Character m_residueAASpecificity;
	private String m_locationSpecificity;
	private String m_ptmShortName;
	private String m_composition;
	private double m_monoMass;
	
	private static HashMap<Long, DInfoPTM> m_infoPTMMap = null;
	
    public DInfoPTM(long idPtmSpecificity, Character residueAASpecificity, String locationSpecificity, String ptmShortName, String composition, double monoMass) {
    	m_idPtmSpecificity = idPtmSpecificity;
        m_residueAASpecificity = residueAASpecificity;
        m_locationSpecificity = locationSpecificity;
        m_ptmShortName = ptmShortName;
        m_composition = composition;
        m_monoMass = monoMass;
    }
    
	public long getIdPtmSpecificity() {
		return m_idPtmSpecificity;
	}
	
	public String getLocationSpecificity() {
		return m_locationSpecificity;
	}
	
	public Character getRresidueAASpecificity() {
		return m_residueAASpecificity;
	}
	
    public String getPtmShortName() {
    	return m_ptmShortName;
    }

    
    public String getComposition() {
    	return m_composition;
    }
	
    public double getMonoMass() {
    	return m_monoMass;
    }
    
    public static HashMap<Long, DInfoPTM> getInfoPTMMap() {
    	if (m_infoPTMMap == null) {
    		m_infoPTMMap = new HashMap<>();
    	}
    	return m_infoPTMMap;
    }
    
    public static void addInfoPTM(DInfoPTM infoPTM) {
    	if (m_infoPTMMap == null) {
    		m_infoPTMMap = new HashMap<>();
    	}
    	m_infoPTMMap.put(infoPTM.getIdPtmSpecificity(), infoPTM);
    }
    
}

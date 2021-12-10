package fr.proline.core.orm.msi.dto;

import fr.proline.core.orm.msi.PtmSpecificity;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class DInfoPTM {

	private long m_idPtmSpecificity;
	private long m_idPtm;
	private Character m_residueAASpecificity;
	private String m_locationSpecificity;
	private String m_ptmShortName;
	private String m_composition;
	private double m_monoMass;

	private static HashMap<Long, DInfoPTM> m_infoPTMMap = null;

	public DInfoPTM(
		long idPtmSpecificity,
		long idPtm,
		Character residueAASpecificity,
		String locationSpecificity,
		String ptmShortName,
		String composition,
		double monoMass) {
		m_idPtmSpecificity = idPtmSpecificity;
		m_idPtm = idPtm;
		m_residueAASpecificity = residueAASpecificity;
		m_locationSpecificity = locationSpecificity;
		m_ptmShortName = ptmShortName;
		m_composition = composition;
		m_monoMass = monoMass;
	}

	public long getIdPtmSpecificity() {
		return m_idPtmSpecificity;
	}

	public  long getIdPtm(){
		return m_idPtm;
	}

	public String getLocationSpecificity() {
		return m_locationSpecificity;
	}

	public Character getResidueAASpecificity() {
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

	public static List<DInfoPTM> getInfoPTMForPTM(Long ptmId){
		return getInfoPTMMap().values().stream().filter(dInfoPTM -> ptmId.equals(dInfoPTM.m_idPtm)).collect(Collectors.toList());
	}

	public String toReadablePtmString(int position) {
		StringBuilder builder = new StringBuilder(getPtmShortName());
		builder.append(" (");
		if (getLocationSpecificity().equals("Anywhere")) {
			builder.append(getResidueAASpecificity()).append(position).append(')');
		} else {
			builder.append(getLocationSpecificity()).append(')');
		}
		return builder.toString();
	}

	// VDS Workaround test for issue #16643   
	public String toOtherReadablePtmString(int position) {
		StringBuilder builder = new StringBuilder(getPtmShortName());
		builder.append(" (");
		PtmSpecificity.PtmLocation ptmLoc = PtmSpecificity.PtmLocation.withName(getLocationSpecificity());
		if (ptmLoc.equals(PtmSpecificity.PtmLocation.ANYWHERE)) {
			builder.append(getResidueAASpecificity()).append(position).append(')');
		} else {
			switch (ptmLoc) {
			case ANY_C_TERM:
				builder.append(PtmSpecificity.PtmLocation.PROT_C_TERM.toString()).append(')');
				break;
			case PROT_C_TERM:
				builder.append(PtmSpecificity.PtmLocation.ANY_C_TERM.toString()).append(')');
				break;
			case ANY_N_TERM:
				builder.append(PtmSpecificity.PtmLocation.PROT_N_TERM.toString()).append(')');
				break;
			case PROT_N_TERM:
				builder.append(PtmSpecificity.PtmLocation.ANY_N_TERM.toString()).append(')');
				break;
			}
		}
		return builder.toString();
	}

}

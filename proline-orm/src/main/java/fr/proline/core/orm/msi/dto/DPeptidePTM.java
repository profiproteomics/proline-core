package fr.proline.core.orm.msi.dto;

public class DPeptidePTM {

	private long m_idPeptide;
	private long m_idPtmSpecificity;
	private int m_seqPosition;

	public DPeptidePTM(long idPeptide, long idPtmSpecificity, int seqPosition) {
		m_idPeptide = idPeptide;
		m_idPtmSpecificity = idPtmSpecificity;
		m_seqPosition = seqPosition;
	}

	public long getIdPeptide() {
		return m_idPeptide;
	}

	public long getIdPtmSpecificity() {
		return m_idPtmSpecificity;
	}

	public long getSeqPosition() {
		return m_seqPosition;
	}

}

package fr.proline.core.orm.msi.dto;

public class DBioSequence {

	private String m_sequence;
	private double m_mass;
	private double m_pi;

	public DBioSequence(String sequence, double mass, double pi) {
		m_sequence = sequence;
		m_mass = mass;
		m_pi = pi;
	}

	public String getSequence() {
		return m_sequence;
	}

	public double getMass() {
		return m_mass;
	}

	public double getPi() {
		return m_pi;
	}

}

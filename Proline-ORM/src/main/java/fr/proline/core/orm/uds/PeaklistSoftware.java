package fr.proline.core.orm.uds;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

import fr.profi.util.StringUtils;

/**
 * The persistent class for the peaklist_software database table.
 * 
 */
@Entity(name = "fr.proline.core.orm.uds.PeaklistSoftware")
@NamedQueries({
		@NamedQuery(name = "findUdsPeaklistSoftForName", query = "select pls from fr.proline.core.orm.uds.PeaklistSoftware pls"
			+ " where (upper(pls.name) = :name) and (pls.version is null)"),

		@NamedQuery(name = "findUdsPeaklistSoftForNameAndVersion", query = "select pls from fr.proline.core.orm.uds.PeaklistSoftware pls"
			+ " where (upper(pls.name) = :name) and (upper(pls.version) = :version)")

})
@Table(name = "peaklist_software")
public class PeaklistSoftware implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	private String name;

	@Column(name = "serialized_properties")
	private String serializedProperties;

	private String version;

	// bi-directional many-to-one association to SpectrumTitleParsingRule
	@ManyToOne
	@JoinColumn(name = "spec_title_parsing_rule_id")
	private SpectrumTitleParsingRule specTitleParsingRule;

	public PeaklistSoftware() {
	}

	public long getId() {
		return id;
	}

	public void setId(final long pId) {
		id = pId;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public String getVersion() {
		return this.version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public SpectrumTitleParsingRule getSpecTitleParsingRule() {
		return this.specTitleParsingRule;
	}

	public void setSpecTitleParsingRule(SpectrumTitleParsingRule specTitleParsingRule) {
		this.specTitleParsingRule = specTitleParsingRule;
	}

	public enum SoftwareRelease {

		EXTRACT_MSN("extract_msn.exe"),
		DATA_ANALYSIS_4_0("Data Analysis", "4.0"),
		DATA_ANALYSIS_4_1("Data Analysis", "4.1"),
		MASCOT_DLL("mascot.dll"),
		MASCOT_DISTILLER("Mascot Distiller"),
		MAX_QUANT("MaxQuant"),
		PROLINE("Proline"),
		PROTEIN_PILOT("Protein Pilot"),
		PROTEOME_DISCOVER("Proteome Discoverer"),
		PROTEO_WIZARD_2_0("ProteoWizard", "2.0"),
		PROTEO_WIZARD_2_1("ProteoWizard", "2.1"),
		PROTEO_WIZARD_3_0("ProteoWizard", "3.0"),
		SPECTRUM_MILL("Spectrum Mill");

		private final String m_name;
		private final String m_version;

		private SoftwareRelease(final String name, final String version) {
			assert (!StringUtils.isEmpty(name)) : "PeaklistSoftware.SoftwareRelease() invalid name";
			assert (!StringUtils.isEmpty(version)) : "PeaklistSoftware.Definition() invalid version";

			m_name = name;
			m_version = version;
		}

		private SoftwareRelease(final String name) {
			assert (!StringUtils.isEmpty(name)) : "PeaklistSoftware.SoftwareRelease() invalid name";

			m_name = name;
			m_version = "";
		}

		public String getName() {
			return m_name;
		}

		public String getVersion() {
			return m_version;
		}

		@Override
		public String toString() {
			if (StringUtils.isEmpty(m_version))
				return m_name;
			else
				return m_name + " " + m_version;
		}

	}

}

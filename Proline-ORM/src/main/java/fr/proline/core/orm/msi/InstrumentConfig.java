package fr.proline.core.orm.msi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

import fr.proline.core.orm.uds.InstrumentConfiguration;
import fr.proline.core.orm.utils.StringUtils;

/**
 * The persistent class for the instrument_config database table.
 * 
 */
@Entity
@NamedQueries({
	@NamedQuery(name = "findMsiInstrumConfForNameAndMs1AndMsn", query = "select ic from fr.proline.core.orm.msi.InstrumentConfig ic"
		+ " where (upper(ic.name) = :name) and (upper(ic.ms1Analyzer) = :ms1Analyzer) and (upper(ic.msnAnalyzer) = :msnAnalyzer)"),

	@NamedQuery(name = "findMsiInstrumConfForNameAndMs1", query = "select ic from fr.proline.core.orm.msi.InstrumentConfig ic"
		+ " where (upper(ic.name) = :name) and (upper(ic.ms1Analyzer) = :ms1Analyzer) and (ic.msnAnalyzer is null)")

})
@Table(name = "instrument_config")
public class InstrumentConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    // MSI InstrumentConfig Id are not generated (taken from Uds InstrumentConfig entity)
    private Integer id;

    @Column(name = "ms1_analyzer")
    private String ms1Analyzer;

    @Column(name = "msn_analyzer")
    private String msnAnalyzer;

    private String name;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    public InstrumentConfig() {
    }

    /**
     * Create a Msi InstrumentConfig entity from an Uds InstrumentConfiguration entity. Created Msi
     * InstrumentConfig entity shares the same Id with given Uds InstrumentConfiguration.
     * 
     * @param udsInstrumentConfig
     *            InstrumentConfiguration entity from udsDb used to initialize Msi InstrumentConfig fields
     *            (must not be <code>null</code> )
     */
    public InstrumentConfig(final InstrumentConfiguration udsInstrumentConfig) {

	if (udsInstrumentConfig == null) {
	    throw new IllegalArgumentException("UdsInstrumentConfig is null");
	}

	setId(udsInstrumentConfig.getId());
	setMs1Analyzer(udsInstrumentConfig.getMs1Analyzer());

	final String msnAnalyzer = udsInstrumentConfig.getMsnAnalyzer();

	if (StringUtils.isEmpty(msnAnalyzer)) {
	    setMsnAnalyzer(null);
	} else {
	    setMsnAnalyzer(msnAnalyzer);
	}

	setName(udsInstrumentConfig.getName());
	setSerializedProperties(udsInstrumentConfig.getSerializedProperties());
    }

    public Integer getId() {
	return this.id;
    }

    public void setId(Integer id) {
	this.id = id;
    }

    public String getMs1Analyzer() {
	return this.ms1Analyzer;
    }

    public void setMs1Analyzer(String ms1Analyzer) {
	this.ms1Analyzer = ms1Analyzer;
    }

    public String getMsnAnalyzer() {
	return this.msnAnalyzer;
    }

    public void setMsnAnalyzer(String msnAnalyzer) {
	this.msnAnalyzer = msnAnalyzer;
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

}
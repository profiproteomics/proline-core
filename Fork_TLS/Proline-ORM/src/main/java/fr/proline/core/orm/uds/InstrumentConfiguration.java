package fr.proline.core.orm.uds;

import java.io.Serializable;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

/**
 * The persistent class for the instrument_config database table.
 * 
 */
@Entity
@NamedQueries({
	@NamedQuery(name = "findUdsInstrumConfForNameAndMs1AndMsn", query = "select ic from fr.proline.core.orm.uds.InstrumentConfiguration ic"
		+ " where (upper(ic.name) = :name) and (upper(ic.ms1Analyzer) = :ms1Analyzer) and (upper(ic.msnAnalyzer) = :msnAnalyzer)"),

	@NamedQuery(name = "findUdsInstrumConfForNameAndMs1", query = "select ic from fr.proline.core.orm.uds.InstrumentConfiguration ic"
		+ " where (upper(ic.name) = :name) and (upper(ic.ms1Analyzer) = :ms1Analyzer) and (ic.msnAnalyzer is null)")

})
@Table(name = "instrument_config")
public class InstrumentConfiguration implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column(name = "ms1_analyzer")
    private String ms1Analyzer;

    @Column(name = "msn_analyzer")
    private String msnAnalyzer;

    private String name;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    // uni-directional many-to-one association to Activation
    @ManyToOne
    private Activation activation;

    // bi-directional many-to-one association to Instrument
    @ManyToOne
    private Instrument instrument;

    // uni-directional many-to-many association to FragmentationRule
    @ManyToMany
    @JoinTable(name = "instrument_config_fragmentation_rule_map", joinColumns = { @JoinColumn(name = "instrument_config_id") }, inverseJoinColumns = { @JoinColumn(name = "fragmentation_rule_id") })
    private Set<FragmentationRule> fragmentationRules;

    public InstrumentConfiguration() {
    }

    public long getId() {
	return id;
    }

    public void setId(final long pId) {
	id = pId;
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

    public Activation getActivation() {
	return this.activation;
    }

    public void setActivation(Activation activation) {
	this.activation = activation;
    }

    public Instrument getInstrument() {
	return this.instrument;
    }

    public void setInstrument(Instrument instrument) {
	this.instrument = instrument;
    }

    public Set<FragmentationRule> getFragmentationRules() {
	return this.fragmentationRules;
    }

    public void setFragmentationRules(Set<FragmentationRule> fragmentationRules) {
	this.fragmentationRules = fragmentationRules;
    }

}

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

}

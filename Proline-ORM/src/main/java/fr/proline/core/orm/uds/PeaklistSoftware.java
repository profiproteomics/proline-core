package fr.proline.core.orm.uds;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the peaklist_software database table.
 * 
 */
@Entity(name="fr.proline.core.orm.uds.PeaklistSoftware")
@Table(name="peaklist_software")
public class PeaklistSoftware implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	private String name;

	@Column(name="serialized_properties")
	private String serializedProperties;

	private String version;

	//bi-directional many-to-one association to SpectrumTitleParsingRule
    @ManyToOne
	@JoinColumn(name="spec_title_parsing_rule_id")
	private SpectrumTitleParsingRule specTitleParsingRule;

    public PeaklistSoftware() {
    }

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
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
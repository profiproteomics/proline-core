package fr.proline.core.orm.uds;

import java.io.Serializable;
import javax.persistence.*;
import java.util.Set;


/**
 * The persistent class for the instrument database table.
 * 
 */
@Entity
public class Instrument implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	private String name;

	@Column(name="serialized_properties")
	private String serializedProperties;

	private String source;

	//bi-directional many-to-one association to InstrumentConfiguration
	@OneToMany(mappedBy="instrument")
	private Set<InstrumentConfiguration> instrumentConfigurations;

    public Instrument() {
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

	public String getSource() {
		return this.source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public Set<InstrumentConfiguration> getInstrumentConfigurations() {
		return this.instrumentConfigurations;
	}

	public void setInstrumentConfigurations(Set<InstrumentConfiguration> instrumentConfigurations) {
		this.instrumentConfigurations = instrumentConfigurations;
	}
	
}
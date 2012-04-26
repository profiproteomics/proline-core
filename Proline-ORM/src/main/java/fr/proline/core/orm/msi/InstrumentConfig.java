package fr.proline.core.orm.msi;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the instrument_config database table.
 * 
 */
@Entity
@Table(name="instrument_config")
public class InstrumentConfig implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	@Column(name="ms1_analyzer")
	private String ms1Analyzer;

	@Column(name="msn_analyzer")
	private String msnAnalyzer;

	private String name;

	@Column(name="serialized_properties")
	private String serializedProperties;

    public InstrumentConfig() {
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
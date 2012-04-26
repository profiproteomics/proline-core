package fr.proline.core.orm.uds;

import java.io.Serializable;
import javax.persistence.*;
import java.util.Set;


/**
 * The persistent class for the quant_method database table.
 * 
 */
@Entity
@Table(name="quant_method")
public class QuantitationMethod implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	@Column(name="abundance_unit")
	private String abundanceUnit;

	private String name;

	@Column(name="serialized_properties")
	private String serializedProperties;

	private String type;

	//bi-directional many-to-one association to QuantLabel
	@OneToMany(mappedBy="method")
	private Set<QuantitationLabel> labels;

    public QuantitationMethod() {
    }

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getAbundanceUnit() {
		return this.abundanceUnit;
	}

	public void setAbundanceUnit(String abundanceUnit) {
		this.abundanceUnit = abundanceUnit;
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

	public String getType() {
		return this.type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public Set<QuantitationLabel> getLabels() {
		return this.labels;
	}

	public void setLabels(Set<QuantitationLabel> labels) {
		this.labels = labels;
	}
	
}
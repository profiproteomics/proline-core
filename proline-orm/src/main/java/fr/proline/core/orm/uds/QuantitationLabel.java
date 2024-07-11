package fr.proline.core.orm.uds;

import fr.proline.core.orm.util.JsonSerializer;

import java.io.Serializable;
import java.util.Map;

import javax.persistence.*;

/**
 * The persistent class for the quant_label database table.
 * 
 */
@Entity
@Table(name = "quant_label")
public class QuantitationLabel implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	@Column(name = "number")
	private int number;

	private String type;

	private String name;

	@Column(name = "serialized_properties")
	private String serializedProperties;

	// bi-directional many-to-one association to QuantMethod
	@ManyToOne
	@JoinColumn(name = "quant_method_id")
	private QuantitationMethod method;

	@Transient
	private Map<String, Object> serializedPropertiesMap = null;

	public QuantitationLabel() {
	}

	public long getId() {
		return id;
	}

	public void setId(final long pId) {
		id = pId;
	}

	public int getNumber() {
		return number;
	}

	public void setNumber(int number) {
		this.number = number;
	}

	public String getType() {
		return this.type;
	}

	public void setType(String type) {
		this.type = type;
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

	public QuantitationMethod getMethod() {
		return this.method;
	}

	public void setMethod(QuantitationMethod method) {
		this.method = method;
	}

	public java.util.Map<String, Object> getSerializedPropertiesAsMap() throws Exception {
		if ((serializedPropertiesMap == null) && (serializedProperties != null)) {
			serializedPropertiesMap = JsonSerializer.getMapper().readValue(
					getSerializedProperties(), java.util.Map.class
			);
		}
		return serializedPropertiesMap;
	}

	@Override
	public int hashCode() {
		return Long.valueOf(getId()).hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		QuantitationLabel that = (QuantitationLabel) o;
		return id == that.id;
	}

}

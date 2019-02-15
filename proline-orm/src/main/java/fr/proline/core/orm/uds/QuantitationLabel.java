package fr.proline.core.orm.uds;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

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

}

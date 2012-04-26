package fr.proline.core.orm.uds;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the quant_label database table.
 * 
 */
@Entity
@Table(name="quant_label")
public class QuantitationLabel implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	private String name;

	@Column(name="serialized_properties")
	private String serializedProperties;

	private String type;

	//bi-directional many-to-one association to QuantMethod
    @ManyToOne
	@JoinColumn(name="quant_method_id")
	private QuantitationMethod method;

    public QuantitationLabel() {
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

	public String getType() {
		return this.type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public QuantitationMethod getMethod() {
		return this.method;
	}

	public void setMethod(QuantitationMethod method) {
		this.method = method;
	}
	
}
package fr.proline.core.orm.uds;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the theoretical_fragment database table.
 * 
 */
@Entity
@Table(name="theoretical_fragment")
public class TheoreticalFragment implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	@Column(name="neutral_loss")
	private String neutralLoss;

	@Column(name="serialized_properties")
	private String serializedProperties;

	private String type;

    public TheoreticalFragment() {
    }

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getNeutralLoss() {
		return this.neutralLoss;
	}

	public void setNeutralLoss(String neutralLoss) {
		this.neutralLoss = neutralLoss;
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

}
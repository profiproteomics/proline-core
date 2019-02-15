package fr.proline.core.orm.uds;

import java.io.Serializable;
import java.util.List;

import javax.persistence.*;

/**
 * The persistent class for the quant_method database table.
 * 
 */
@Entity
@NamedQueries({
	@NamedQuery(
		name = "findQuantMethodForTypeAndUnit",
		query = "select qm from fr.proline.core.orm.uds.QuantitationMethod qm"
			+ " where (upper(qm.type) = upper(:searchType)) and (upper(qm.abundanceUnit) = upper(:searchAbundanceUnit))"
	),
		@NamedQuery(
				name = "findQuantMethodForType",
				query = "select qm from fr.proline.core.orm.uds.QuantitationMethod qm where (upper(qm.type) = upper(:searchType))"
		)
})
@Table(name = "quant_method")
public class QuantitationMethod implements Serializable {

	private static final long serialVersionUID = 1L;

	public enum Type {
		ATOM_LABELING("atom_labeling"),
		ISOBARIC_TAG("isobaric_tag"),
		LABEL_FREE("label_free"),
		RESIDUE_LABELING("residue_labeling");

		private final String type;

		private Type(final String type) {
			this.type = type;
		}

		@Override
		public String toString() {
			return type;
		}
	};

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	@Column(name = "abundance_unit")
	private String abundanceUnit;

	private String name;

	@Column(name = "serialized_properties")
	private String serializedProperties;

	private String type;

	// bi-directional many-to-one association to QuantLabel
	@OneToMany(mappedBy = "method")
	@OrderBy("number")
	private List<QuantitationLabel> labels;

	public QuantitationMethod() {
	}

	public long getId() {
		return id;
	}

	public void setId(final long pId) {
		id = pId;
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

	public List<QuantitationLabel> getLabels() {
		return this.labels;
	}

	public void setLabels(List<QuantitationLabel> labels) {
		this.labels = labels;
	}

	@Override public String toString() {
		return this.name;
	}
}

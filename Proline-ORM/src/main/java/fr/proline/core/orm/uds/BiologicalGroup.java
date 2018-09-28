package fr.proline.core.orm.uds;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OrderBy;
import javax.persistence.Table;

/**
 * The persistent class for the biological_group database table.
 * 
 */
@Entity
@Table(name = "biological_group")
public class BiologicalGroup implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	private String name;

	private int number;

	// bi-directional many-to-one association to Dataset
	@ManyToOne
	@JoinColumn(name = "quantitation_id")
	private Dataset dataset;

	@Column(name = "serialized_properties")
	private String serializedProperties;

	// bi-directional many-to-many association to GroupSetup
	@ManyToMany
	@JoinTable(
		name = "group_setup_biological_group_map",
		joinColumns = { @JoinColumn(name = "biological_group_id") },
		inverseJoinColumns = { @JoinColumn(name = "group_setup_id") }
	)
	private Set<GroupSetup> groupSetups;

	// uni-directional many-to-many association to BiologicalSample
	@ManyToMany
	@JoinTable(
		name = "biological_group_biological_sample_item",
		joinColumns = { @JoinColumn(name = "biological_group_id") },
		inverseJoinColumns = {@JoinColumn(name = "biological_sample_id")}
	)
	@OrderBy("number")
	private List<BiologicalSample> biologicalSamples;

	public BiologicalGroup() {
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

	public int getNumber() {
		return number;
	}

	public void setNumber(final int pNumber) {
		number = pNumber;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public Set<GroupSetup> getGroupSetups() {
		return this.groupSetups;
	}

	public void setGroupSetups(Set<GroupSetup> groupSetups) {
		this.groupSetups = groupSetups;
	}

	public List<BiologicalSample> getBiologicalSamples() {
		return biologicalSamples;
	}

	public void setBiologicalSamples(final List<BiologicalSample> biologicalSamples) {
		this.biologicalSamples = biologicalSamples;
	}

	// TODO: return a true QuantitationDataset object when it is implemented
	public Dataset getQuantitationDataset() {
		return this.dataset;
	}

	public void setQuantitationDataset(Dataset dataset) {
		this.dataset = dataset;
	}

}

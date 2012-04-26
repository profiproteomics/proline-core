package fr.proline.core.orm.uds;

import java.io.Serializable;
import javax.persistence.*;
import java.util.Set;


/**
 * The persistent class for the biological_group database table.
 * 
 */
@Entity
@Table(name="biological_group")
public class BiologicalGroup implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	private String name;

	private Integer number;

	@Column(name="serialized_properties")
	private String serializedProperties;

	//bi-directional many-to-one association to GroupSetup
    @ManyToOne
	@JoinColumn(name="group_setup_id")
	private GroupSetup groupSetup;

	//uni-directional many-to-many association to BiologicalSample
    @ManyToMany
	@JoinTable(
		name="biological_group_biological_sample_item"
		, joinColumns={
			@JoinColumn(name="biological_group_id")
			}
		, inverseJoinColumns={
			@JoinColumn(name="biological_sample_id")
			}
		)
	private Set<BiologicalSample> biologicalSamples;

    public BiologicalGroup() {
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

	public Integer getNumber() {
		return this.number;
	}

	public void setNumber(Integer number) {
		this.number = number;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public GroupSetup getGroupSetup() {
		return this.groupSetup;
	}

	public void setGroupSetup(GroupSetup groupSetup) {
		this.groupSetup = groupSetup;
	}
	
	public Set<BiologicalSample> getBiologicalSamples() {
		return this.biologicalSamples;
	}

	public void setBiologicalSamples(Set<BiologicalSample> biologicalSamples) {
		this.biologicalSamples = biologicalSamples;
	}
	
}
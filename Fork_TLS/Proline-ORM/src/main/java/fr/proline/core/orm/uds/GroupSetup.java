package fr.proline.core.orm.uds;

import java.io.Serializable;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Table;

/**
 * The persistent class for the group_setup database table.
 * 
 */
@Entity
@Table(name = "group_setup")
public class GroupSetup implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    private String name;
    
    private int number;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    // bi-directional many-to-many association to BiologicalGroup
    @ManyToMany(mappedBy = "groupSetups")
    @OrderBy("number")
    private List<BiologicalGroup> biologicalGroups;

    // bi-directional many-to-one association to Dataset
    @ManyToOne
    @JoinColumn(name = "quantitation_id")
    private Dataset dataset;

    // bi-directional many-to-one association to RatioDefinition
    @OneToMany(mappedBy = "groupSetup")
    @OrderBy("number")
    private List<RatioDefinition> ratioDefinitions;

    public GroupSetup() {
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

    public List<BiologicalGroup> getBiologicalGroups() {
	return biologicalGroups;
    }

    public void setBiologicalGroups(final List<BiologicalGroup> biologicalGroups) {
	this.biologicalGroups = biologicalGroups;
    }

    // TODO: return a true QuantitationDataset object when it is implemented
    public Dataset getQuantitationDataset() {
	return this.dataset;
    }

    public void setQuantitationDataset(Dataset dataset) {
	this.dataset = dataset;
    }

    public List<RatioDefinition> getRatioDefinitions() {
	return ratioDefinitions;
    }

    public void setRatioDefinitions(final List<RatioDefinition> ratioDefinitions) {
	this.ratioDefinitions = ratioDefinitions;
    }

}

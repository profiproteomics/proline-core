package fr.proline.core.orm.uds;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * The persistent class for the theoretical_fragment database table.
 * 
 */
@Entity
@Table(name = "fragmentation_series")
public class FragmentationSeries implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column(name = "neutral_loss")
    private String neutralLoss;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    private String name;

    public FragmentationSeries() {
    }

    public long getId() {
	return id;
    }

    public void setId(final long pId) {
	id = pId;
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

    public String getName() {
	return this.name;
    }

    public void setName(String name) {
	this.name = name;
    }

}

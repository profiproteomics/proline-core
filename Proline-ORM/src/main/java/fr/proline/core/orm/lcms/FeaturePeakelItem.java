package fr.proline.core.orm.lcms;

import java.io.Serializable;

import javax.persistence.*;

/**
 * The persistent class for the feature_peakel_item database table.
 * 
 */
@Entity
@Table(name="feature_peakel_item")
@NamedQuery(name="FeaturePeakelItem.findAll", query="SELECT f FROM FeaturePeakelItem f")
public class FeaturePeakelItem implements Serializable {
	private static final long serialVersionUID = 1L;

	@EmbeddedId
	private FeaturePeakelItemPK id;

	@Column(name="isotope_index")
	private Integer isotopeIndex;
	
	@Column(name="is_base_peakel")
	private boolean isBasePeakel;

	@Column(name="serialized_properties")
	private String serializedProperties;
	 
	//bi-directional many-to-one association to Feature
	@ManyToOne
	@JoinColumn(name="feature_id")
	@MapsId("featureId")
	private Feature feature;

	//bi-directional many-to-one association to Map
	@ManyToOne
	@JoinColumn(name = "map_id")
	private Map map;

	//bi-directional many-to-one association to Peakel
	@ManyToOne
	@JoinColumn(name="peakel_id")
	@MapsId("peakelId")
	private Peakel peakel;

	public FeaturePeakelItem() {
	}

	public FeaturePeakelItemPK getId() {
		return this.id;
	}

	public void setId(FeaturePeakelItemPK id) {
		this.id = id;
	}

	public Integer getIsotopeIndex() {
		return this.isotopeIndex;
	}

	public void setIsotopeIndex(Integer isotopeIndex) {
		this.isotopeIndex = isotopeIndex;
	}
	
	public boolean getIsBasePeakel() {
		return isBasePeakel;
	}

	public void setIsBasePeakel(boolean isBasePeakel) {
		this.isBasePeakel = isBasePeakel;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public Feature getFeature() {
		return this.feature;
	}

	public void setFeature(Feature feature) {
		this.feature = feature;
	}

	public Map getMap() {
		return this.map;
	}

	public void setMap(Map map) {
		this.map = map;
	}

	public Peakel getPeakel() {
		return this.peakel;
	}

	public void setPeakel(Peakel peakel) {
		this.peakel = peakel;
	}

}
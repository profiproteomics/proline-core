package fr.proline.core.orm.lcms;

import java.io.Serializable;

import javax.persistence.*;

/**
 * The persistent class for the master_feature_item database table.
 * 
 */
@Entity
@Table(name = "master_feature_item")
@NamedQuery(name = "MasterFeatureItem.findAll", query = "SELECT m FROM MasterFeatureItem m")
public class MasterFeatureItem implements Serializable {
	private static final long serialVersionUID = 1L;

	@EmbeddedId
	private MasterFeatureItemPK id;

	@Column(name = "is_best_child")
	private Boolean isBestChild;

	//uni-directional many-to-one association to Feature
	@ManyToOne
	@JoinColumn(name = "master_feature_id")
	@MapsId("masterFeatureId")
	private Feature masterFeature;

	//uni-directional many-to-one association to Feature
	@ManyToOne
	@JoinColumn(name = "child_feature_id")
	@MapsId("childFeatureId")
	private Feature childFeature;

	//bi-directional many-to-one association to ProcessedMap
	@ManyToOne
	@JoinColumn(name = "master_map_id")
	private ProcessedMap masterMap;

	public MasterFeatureItem() {
	}

	public MasterFeatureItemPK getId() {
		return this.id;
	}

	public void setId(MasterFeatureItemPK id) {
		this.id = id;
	}

	public Boolean getIsBestChild() {
		return this.isBestChild;
	}

	public void setIsBestChild(Boolean isBestChild) {
		this.isBestChild = isBestChild;
	}

	public Feature getMasterFeature() {
		return this.masterFeature;
	}

	public void setMasterFeature(Feature masterFeature) {
		this.masterFeature = masterFeature;
	}

	public Feature getChildFeature() {
		return this.childFeature;
	}

	public void setChildFeature(Feature childFeature) {
		this.childFeature = childFeature;
	}

	public ProcessedMap getMasterMap() {
		return this.masterMap;
	}

	public void setMasterMap(ProcessedMap masterMap) {
		this.masterMap = masterMap;
	}

}
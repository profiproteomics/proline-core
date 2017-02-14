package fr.proline.core.orm.lcms;

import java.io.Serializable;

import javax.persistence.*;


/**
 * The persistent class for the feature_cluster_item database table.
 * 
 */
@Entity
@Table(name="feature_cluster_item")
@NamedQuery(name="FeatureClusterItem.findAll", query="SELECT f FROM FeatureClusterItem f")
public class FeatureClusterItem implements Serializable {
	private static final long serialVersionUID = 1L;

	@EmbeddedId
	private FeatureClusterItemPK id;

	//bi-directional many-to-one association to Feature
	@ManyToOne
	@JoinColumn(name="cluster_feature_id")
	@MapsId("clusterFeatureId")
	private Feature clusterFeature;

	//uni-directional many-to-one association to Feature
	@ManyToOne
	@JoinColumn(name="sub_feature_id")
	@MapsId("subFeatureId")
	private Feature subFeature;

	//bi-directional many-to-one association to ProcessedMap
	@ManyToOne
	@JoinColumn(name="processed_map_id")
	private ProcessedMap processedMap;

	public FeatureClusterItem() {
	}

	public FeatureClusterItemPK getId() {
		return this.id;
	}

	public void setId(FeatureClusterItemPK id) {
		this.id = id;
	}

	public Feature getClusterFeature() {
		return this.clusterFeature;
	}

	public void setClusterFeature(Feature clusterFeature) {
		this.clusterFeature = clusterFeature;
	}

	public Feature getSubFeature() {
		return this.subFeature;
	}

	public void setSubFeature(Feature subFeature) {
		this.subFeature = subFeature;
	}

	public ProcessedMap getProcessedMap() {
		return this.processedMap;
	}

	public void setProcessedMap(ProcessedMap processedMap) {
		this.processedMap = processedMap;
	}

}
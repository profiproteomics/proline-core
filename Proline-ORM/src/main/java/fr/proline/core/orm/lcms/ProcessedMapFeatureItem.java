package fr.proline.core.orm.lcms;

import java.io.Serializable;

import javax.persistence.*;

/**
 * The persistent class for the processed_map_feature_item database table.
 * 
 */
@Entity
@Table(name = "processed_map_feature_item")
@NamedQuery(name = "ProcessedMapFeatureItem.findAll", query = "SELECT p FROM ProcessedMapFeatureItem p")
public class ProcessedMapFeatureItem implements Serializable {
	private static final long serialVersionUID = 1L;

	@EmbeddedId
	private ProcessedMapFeatureItemPK id;

	@Column(name = "calibrated_moz")
	private double calibratedMoz;

	@Column(name = "corrected_elution_time")
	private float correctedElutionTime;

	@Column(name = "is_clusterized")
	private Boolean isClusterized;

	@Column(name = "normalized_intensity")
	private float normalizedIntensity;

	@Column(name = "selection_level")
	private Integer selectionLevel;

	@Column(name = "serialized_properties")
	private String serializedProperties;

	//uni-directional many-to-one association to Feature
	@ManyToOne
	@JoinColumn(name = "feature_id")
	@MapsId("featureId")
	private Feature feature;

	//bi-directional many-to-one association to ProcessedMap
	@ManyToOne
	@JoinColumn(name = "processed_map_id")
	@MapsId("processedMapId")
	private ProcessedMap processedMap;

	public ProcessedMapFeatureItem() {
	}

	public ProcessedMapFeatureItemPK getId() {
		return this.id;
	}

	public void setId(ProcessedMapFeatureItemPK id) {
		this.id = id;
	}

	public double getCalibratedMoz() {
		return this.calibratedMoz;
	}

	public void setCalibratedMoz(double calibratedMoz) {
		this.calibratedMoz = calibratedMoz;
	}

	public float getCorrectedElutionTime() {
		return this.correctedElutionTime;
	}

	public void setCorrectedElutionTime(float correctedElutionTime) {
		this.correctedElutionTime = correctedElutionTime;
	}

	public Boolean getIsClusterized() {
		return this.isClusterized;
	}

	public void setIsClusterized(Boolean isClusterized) {
		this.isClusterized = isClusterized;
	}

	public float getNormalizedIntensity() {
		return this.normalizedIntensity;
	}

	public void setNormalizedIntensity(float normalizedIntensity) {
		this.normalizedIntensity = normalizedIntensity;
	}

	public Integer getSelectionLevel() {
		return this.selectionLevel;
	}

	public void setSelectionLevel(Integer selectionLevel) {
		this.selectionLevel = selectionLevel;
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

	public ProcessedMap getProcessedMap() {
		return this.processedMap;
	}

	public void setProcessedMap(ProcessedMap processedMap) {
		this.processedMap = processedMap;
	}

	@Override
	public String toString() {
		return new StringBuilder("FeatureItem ").append(id).append(" to ").append(feature.toString()).toString();
	}

}
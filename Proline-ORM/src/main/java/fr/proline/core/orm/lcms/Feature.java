package fr.proline.core.orm.lcms;

import javax.persistence.*;

import fr.proline.core.orm.util.JsonSerializer;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.List;

/**
 * The persistent class for the feature database table.
 * 
 */
@Entity
@NamedQuery(name = "Feature.findAll", query = "SELECT f FROM Feature f")
public class Feature implements Serializable {
	private static final long serialVersionUID = 1L;

	private static DecimalFormat df = new DecimalFormat("#.000");

	@Id
	private Long id;

	@Column(name = "apex_intensity")
	private float apexIntensity;

	private Integer charge;

	@Column(name = "compound_id")
	private Long compoundId;

	private float duration;

	@Column(name = "elution_time")
	private float elutionTime;

	private float intensity;

	@Column(name = "is_cluster")
	private Boolean isCluster;

	@Column(name = "is_overlapping")
	private Boolean isOverlapping;

	@Column(name = "map_layer_id")
	private Long mapLayerId;

	private double moz;

	@Column(name = "ms1_count")
	private Integer ms1Count;

	@Column(name = "ms2_count")
	private Integer ms2Count;

	@Column(name = "peakel_count")
	private int peakelCount;

	@Column(name = "quality_score")
	private float qualityScore;

	@Column(name = "serialized_properties")
	private String serializedProperties;

	@Column(name = "theoretical_feature_id")
	private Long theoreticalFeatureId;

	//bi-directional many-to-one association to Map
	@ManyToOne
	@JoinColumn(name = "map_id")
	private Map map;

	//uni-directional many-to-one association to Scan
	@ManyToOne
	@JoinColumn(name = "first_scan_id")
	private Scan firstScan;

	//uni-directional many-to-one association to Scan
	@ManyToOne
	@JoinColumn(name = "last_scan_id")
	private Scan lastScan;

	//uni-directional many-to-one association to Scan
	@ManyToOne
	@JoinColumn(name = "apex_scan_id")
	private Scan apexScan;

	//bi-directional many-to-one association to FeatureClusterItem
	@OneToMany(mappedBy = "clusterFeature")
	private List<FeatureClusterItem> featureClusterItems;

	//bi-directional many-to-one association to FeatureMs2Event
	@OneToMany(mappedBy = "feature")
	private List<FeatureMs2Event> featureMs2Events;

	//bi-directional many-to-one association to FeaturePeakelItem
	@OneToMany(mappedBy = "feature")
	private List<FeaturePeakelItem> featurePeakelItems;

	// serializedProperties as a map
	@Transient
	private java.util.Map<String, Object> serializedPropertiesMap;

	public Feature() {
	}

	public Long getId() {
		return this.id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public float getApexIntensity() {
		return this.apexIntensity;
	}

	public void setApexIntensity(float apexIntensity) {
		this.apexIntensity = apexIntensity;
	}

	public Integer getCharge() {
		return this.charge;
	}

	public void setCharge(Integer charge) {
		this.charge = charge;
	}

	public Long getCompoundId() {
		return this.compoundId;
	}

	public void setCompoundId(Long compoundId) {
		this.compoundId = compoundId;
	}

	public float getDuration() {
		return this.duration;
	}

	public void setDuration(float duration) {
		this.duration = duration;
	}

	public float getElutionTime() {
		return this.elutionTime;
	}

	public void setElutionTime(float elutionTime) {
		this.elutionTime = elutionTime;
	}

	public float getIntensity() {
		return this.intensity;
	}

	public void setIntensity(float intensity) {
		this.intensity = intensity;
	}

	public Boolean getIsCluster() {
		return this.isCluster;
	}

	public void setIsCluster(Boolean isCluster) {
		this.isCluster = isCluster;
	}

	public Boolean getIsOverlapping() {
		return this.isOverlapping;
	}

	public void setIsOverlapping(Boolean isOverlapping) {
		this.isOverlapping = isOverlapping;
	}

	public Long getMapLayerId() {
		return this.mapLayerId;
	}

	public void setMapLayerId(Long mapLayerId) {
		this.mapLayerId = mapLayerId;
	}

	public double getMoz() {
		return this.moz;
	}

	public void setMoz(double moz) {
		this.moz = moz;
	}

	public Integer getMs1Count() {
		return this.ms1Count;
	}

	public void setMs1Count(Integer ms1Count) {
		this.ms1Count = ms1Count;
	}

	public Integer getMs2Count() {
		return this.ms2Count;
	}

	public void setMs2Count(Integer ms2Count) {
		this.ms2Count = ms2Count;
	}

	public int getPeakelCount() {
		return peakelCount;
	}

	public void setPeakelCount(int peakelCount) {
		this.peakelCount = peakelCount;
	}

	public float getQualityScore() {
		return this.qualityScore;
	}

	public void setQualityScore(float qualityScore) {
		this.qualityScore = qualityScore;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public Long getTheoreticalFeatureId() {
		return this.theoreticalFeatureId;
	}

	public void setTheoreticalFeatureId(Long theoreticalFeatureId) {
		this.theoreticalFeatureId = theoreticalFeatureId;
	}

	public Map getMap() {
		return this.map;
	}

	public void setMap(Map map) {
		this.map = map;
	}

	public Scan getFirstScan() {
		return this.firstScan;
	}

	public void setFirstScan(Scan firstScan) {
		this.firstScan = firstScan;
	}

	public Scan getLastScan() {
		return this.lastScan;
	}

	public void setLastScan(Scan lastScan) {
		this.lastScan = lastScan;
	}

	public Scan getApexScan() {
		return this.apexScan;
	}

	public void setApexScan(Scan apexScan) {
		this.apexScan = apexScan;
	}

	public List<FeatureClusterItem> getFeatureClusterItems() {
		return this.featureClusterItems;
	}

	public void setFeatureClusterItems(List<FeatureClusterItem> featureClusterItems) {
		this.featureClusterItems = featureClusterItems;
	}

	public FeatureClusterItem addFeatureClusterItem(FeatureClusterItem featureClusterItem) {
		getFeatureClusterItems().add(featureClusterItem);
		featureClusterItem.setClusterFeature(this);

		return featureClusterItem;
	}

	public FeatureClusterItem removeFeatureClusterItem(FeatureClusterItem featureClusterItem) {
		getFeatureClusterItems().remove(featureClusterItem);
		featureClusterItem.setClusterFeature(null);

		return featureClusterItem;
	}

	public List<FeatureMs2Event> getFeatureMs2Events() {
		return this.featureMs2Events;
	}

	public void setFeatureMs2Events(List<FeatureMs2Event> featureMs2Events) {
		this.featureMs2Events = featureMs2Events;
	}

	public FeatureMs2Event addFeatureMs2Event(FeatureMs2Event featureMs2Event) {
		getFeatureMs2Events().add(featureMs2Event);
		featureMs2Event.setFeature(this);

		return featureMs2Event;
	}

	public FeatureMs2Event removeFeatureMs2Event(FeatureMs2Event featureMs2Event) {
		getFeatureMs2Events().remove(featureMs2Event);
		featureMs2Event.setFeature(null);

		return featureMs2Event;
	}

	public List<FeaturePeakelItem> getFeaturePeakelItems() {
		return this.featurePeakelItems;
	}

	public void setFeaturePeakelItems(List<FeaturePeakelItem> featurePeakelItems) {
		this.featurePeakelItems = featurePeakelItems;
	}

	public FeaturePeakelItem addFeaturePeakelItem(FeaturePeakelItem featurePeakelItem) {
		getFeaturePeakelItems().add(featurePeakelItem);
		featurePeakelItem.setFeature(this);

		return featurePeakelItem;
	}

	public FeaturePeakelItem removeFeaturePeakelItem(FeaturePeakelItem featurePeakelItem) {
		getFeaturePeakelItems().remove(featurePeakelItem);
		featurePeakelItem.setFeature(null);

		return featurePeakelItem;
	}

	@Override
	public String toString() {
		return new StringBuilder("Feature ").append(id).append(" (").append(df.format(moz)).append(',').append(df.format(elutionTime)).toString();
	}

	public java.util.Map<String, Object> getSerializedPropertiesMap() {
		return serializedPropertiesMap;
	}

	public void setSerializedPropertiesMap(
		java.util.Map<String, Object> serializedPropertiesMap) {
		this.serializedPropertiesMap = serializedPropertiesMap;
	}

	@SuppressWarnings("unchecked")
	public java.util.Map<String, Object> getSerializedPropertiesAsMap() throws Exception {
		if ((serializedPropertiesMap == null) && (serializedProperties != null)) {
			serializedPropertiesMap = JsonSerializer.getMapper().readValue(
				getSerializedProperties(), java.util.Map.class
			);
		}
		return serializedPropertiesMap;
	}

	public void setSerializedPropertiesAsMap(java.util.Map<String, Object> serializedPropertiesMap) throws Exception {
		this.serializedPropertiesMap = serializedPropertiesMap;
		this.serializedProperties = JsonSerializer.getMapper()
			.writeValueAsString(serializedPropertiesMap);
	}

}
package fr.proline.core.orm.lcms;

import java.io.Serializable;

import javax.persistence.*;

import java.sql.Timestamp;
import java.util.List;


/**
 * The persistent class for the map database table.
 * 
 */
@Entity
@NamedQuery(name="Map.findAll", query="SELECT m FROM Map m")
public class Map  {
	private static final long serialVersionUID = 1L;

	@Id
	private Long id;

	@Column(name="creation_timestamp")
	private Timestamp creationTimestamp;

	private String description;

	@Column(name="feature_scoring_id")
	private Long featureScoringId;

	@Column(name="modification_timestamp")
	private Timestamp modificationTimestamp;

	private String name;

	@Column(name="serialized_properties")
	private String serializedProperties;

	private Integer type;

	//bi-directional many-to-one association to Feature
	@OneToMany(mappedBy="map")
	private List<Feature> features;

	//bi-directional many-to-one association to FeaturePeakelItem
	@OneToMany(mappedBy="map")
	private List<FeaturePeakelItem> featurePeakelItems;

	//bi-directional many-to-one association to Peakel
	@OneToMany(mappedBy="map")
	private List<Peakel> peakels;

	//bi-directional one-to-one association to ProcessedMap
	@OneToOne(mappedBy="map")
	private ProcessedMap processedMap;

	//bi-directional one-to-one association to RawMap
	@OneToOne(mappedBy="map")
	private RawMap rawMap;

	public Map() {
	}

	public Long getId() {
		return this.id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Timestamp getCreationTimestamp() {
		return this.creationTimestamp;
	}

	public void setCreationTimestamp(Timestamp creationTimestamp) {
		this.creationTimestamp = creationTimestamp;
	}

	public String getDescription() {
		return this.description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Long getFeatureScoringId() {
		return this.featureScoringId;
	}

	public void setFeatureScoringId(Long featureScoringId) {
		this.featureScoringId = featureScoringId;
	}

	public Timestamp getModificationTimestamp() {
		return this.modificationTimestamp;
	}

	public void setModificationTimestamp(Timestamp modificationTimestamp) {
		this.modificationTimestamp = modificationTimestamp;
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

	public Integer getType() {
		return this.type;
	}

	public void setType(Integer type) {
		this.type = type;
	}

	public List<Feature> getFeatures() {
		return this.features;
	}

	public void setFeatures(List<Feature> features) {
		this.features = features;
	}

	public Feature addFeature(Feature feature) {
		getFeatures().add(feature);
		feature.setMap(this);

		return feature;
	}

	public Feature removeFeature(Feature feature) {
		getFeatures().remove(feature);
		feature.setMap(null);

		return feature;
	}

	public List<FeaturePeakelItem> getFeaturePeakelItems() {
		return this.featurePeakelItems;
	}

	public void setFeaturePeakelItems(List<FeaturePeakelItem> featurePeakelItems) {
		this.featurePeakelItems = featurePeakelItems;
	}

	public FeaturePeakelItem addFeaturePeakelItem(FeaturePeakelItem featurePeakelItem) {
		getFeaturePeakelItems().add(featurePeakelItem);
		featurePeakelItem.setMap(this);

		return featurePeakelItem;
	}

	public FeaturePeakelItem removeFeaturePeakelItem(FeaturePeakelItem featurePeakelItem) {
		getFeaturePeakelItems().remove(featurePeakelItem);
		featurePeakelItem.setMap(null);

		return featurePeakelItem;
	}

	public List<Peakel> getPeakels() {
		return this.peakels;
	}

	public void setPeakels(List<Peakel> peakels) {
		this.peakels = peakels;
	}

	public Peakel addPeakel(Peakel peakel) {
		getPeakels().add(peakel);
		peakel.setMap(this);

		return peakel;
	}

	public Peakel removePeakel(Peakel peakel) {
		getPeakels().remove(peakel);
		peakel.setMap(null);

		return peakel;
	}

	public ProcessedMap getProcessedMap() {
		return this.processedMap;
	}

	public void setProcessedMap(ProcessedMap processedMap) {
		this.processedMap = processedMap;
	}

	public RawMap getRawMap() {
		return this.rawMap;
	}

	public void setRawMap(RawMap rawMap) {
		this.rawMap = rawMap;
	}

	@Override
	public String toString() {
		return new StringBuilder("Map ").append(name).append(" (").append(id).append(')').toString();
	}

}
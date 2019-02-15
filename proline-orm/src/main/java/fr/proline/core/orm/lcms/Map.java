package fr.proline.core.orm.lcms;

import javax.persistence.*;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.List;

/**
 * The persistent class for the map database table.
 * 
 */
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@NamedQuery(name = "Map.findAll", query = "SELECT m FROM Map m")
public class Map implements Serializable {
	private static final long serialVersionUID = 1L;

	public enum Type { RAW_MAP , PROCESSED_MAP }

	@Id
	private Long id;

	@Column(name = "creation_timestamp")
	private Timestamp creationTimestamp;

	private String description;

	@Column(name = "feature_scoring_id")
	private Long featureScoringId;

	@Column(name = "modification_timestamp")
	private Timestamp modificationTimestamp;

	private String name;

	@Column(name = "serialized_properties")
	private String serializedProperties;

	@Enumerated(EnumType.ORDINAL)
	private Type type;

	//bi-directional many-to-one association to Feature
	@OneToMany(mappedBy = "map")
	private List<Feature> features;

	//bi-directional many-to-one association to FeaturePeakelItem
	@OneToMany(mappedBy = "map")
	private List<FeaturePeakelItem> featurePeakelItems;

	//bi-directional many-to-one association to Peakel
	@OneToMany(mappedBy = "map")
	private List<Peakel> peakels;

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

	public Type getType() {
		return this.type;
	}

	public void setType(Type type) {
		this.type = type;
	}

	public List<Feature> getFeatures() {
		return this.features;
	}

	public void setFeatures(List<Feature> features) {
		this.features = features;
	}

	public List<Peakel> getPeakels() {
		return this.peakels;
	}

	public void setPeakels(List<Peakel> peakels) {
		this.peakels = peakels;
	}

	@Override
	public String toString() {
		return new StringBuilder("Map ").append(name).append(" (").append(id).append(')').toString();
	}

}
package fr.proline.core.orm.lcms;

import javax.persistence.*;

import java.io.Serializable;
import java.util.List;

/**
 * The persistent class for the processed_map database table.
 * 
 */
@Entity
@Table(name = "processed_map")
@NamedQuery(name = "ProcessedMap.findAll", query = "SELECT p FROM ProcessedMap p")
public class ProcessedMap extends Map implements Serializable {
	private static final long serialVersionUID = 1L;

	@Column(name = "is_aln_reference")
	private Boolean isAlnReference;

	@Column(name = "is_locked")
	private Boolean isLocked;

	@Column(name = "is_master")
	private Boolean isMaster;

	@Column(name = "normalization_factor")
	private float normalizationFactor;

	private Integer number;

	//bi-directional many-to-one association to FeatureClusterItem
	@OneToMany(mappedBy = "processedMap")
	private List<FeatureClusterItem> featureClusterItems;

	//bi-directional many-to-one association to MasterFeatureItem
	@OneToMany(mappedBy = "masterMap")
	private List<MasterFeatureItem> masterFeatureItems;

	@OneToMany
	@JoinTable(name = "processed_map_raw_map_mapping",
			joinColumns = {@JoinColumn(name = "processed_map_id")},
			inverseJoinColumns = {@JoinColumn(name = "raw_map_id")}
	)
	private List<RawMap> rawMaps;

	//bi-directional many-to-one association to MapSet
	@ManyToOne
	@JoinColumn(name = "map_set_id")
	private MapSet mapSet;

	//bi-directional many-to-one association to ProcessedMapFeatureItem
	@OneToMany(mappedBy = "processedMap")
	private List<ProcessedMapFeatureItem> processedMapFeatureItems;


	//bi-directional many-to-one association to ProcessedMapMozCalibration
	@OneToMany(mappedBy = "processedMap")
	private List<ProcessedMapMozCalibration> processedMapMozCalibration;

	public ProcessedMap() {
	}

	public Boolean getIsAlnReference() {
		return this.isAlnReference;
	}

	public void setIsAlnReference(Boolean isAlnReference) {
		this.isAlnReference = isAlnReference;
	}

	public Boolean getIsLocked() {
		return this.isLocked;
	}

	public void setIsLocked(Boolean isLocked) {
		this.isLocked = isLocked;
	}

	public Boolean getIsMaster() {
		return this.isMaster;
	}

	public void setIsMaster(Boolean isMaster) {
		this.isMaster = isMaster;
	}

	public float getNormalizationFactor() {
		return this.normalizationFactor;
	}

	public void setNormalizationFactor(float normalizationFactor) {
		this.normalizationFactor = normalizationFactor;
	}

	public Integer getNumber() {
		return this.number;
	}

	public void setNumber(Integer number) {
		this.number = number;
	}

	public List<FeatureClusterItem> getFeatureClusterItems() {
		return this.featureClusterItems;
	}

	public void setFeatureClusterItems(List<FeatureClusterItem> featureClusterItems) {
		this.featureClusterItems = featureClusterItems;
	}

	public FeatureClusterItem addFeatureClusterItem(FeatureClusterItem featureClusterItem) {
		getFeatureClusterItems().add(featureClusterItem);
		featureClusterItem.setProcessedMap(this);

		return featureClusterItem;
	}

	public FeatureClusterItem removeFeatureClusterItem(FeatureClusterItem featureClusterItem) {
		getFeatureClusterItems().remove(featureClusterItem);
		featureClusterItem.setProcessedMap(null);

		return featureClusterItem;
	}

	public List<MasterFeatureItem> getMasterFeatureItems() {
		return this.masterFeatureItems;
	}

	public void setMasterFeatureItems(List<MasterFeatureItem> masterFeatureItems) {
		this.masterFeatureItems = masterFeatureItems;
	}

	public MapSet getMapSet() {
		return this.mapSet;
	}

	public void setMapSet(MapSet mapSet) {
		this.mapSet = mapSet;
	}

	public List<ProcessedMapFeatureItem> getProcessedMapFeatureItems() {
		return this.processedMapFeatureItems;
	}

	public void setProcessedMapFeatureItems(List<ProcessedMapFeatureItem> processedMapFeatureItems) {
		this.processedMapFeatureItems = processedMapFeatureItems;
	}

	public List<RawMap> getRawMaps() {
		return rawMaps;
	}

	public RawMap getRawMap() {
		if (isMaster) {
			throw new UnsupportedOperationException("getRawMap is not supported for master maps");
		} else {
			return rawMaps.get(0);
		}
	}

	public List<ProcessedMapMozCalibration> getProcessedMapMozCalibration() {
		return processedMapMozCalibration;
	}

	public void setProcessedMapMozCalibration(List<ProcessedMapMozCalibration> processedMapMozCalibration) {
		this.processedMapMozCalibration = processedMapMozCalibration;
	}

	@Override
	public String toString() {
		return new StringBuilder("ProcessedMap ").append(getId()).toString();
	}
}
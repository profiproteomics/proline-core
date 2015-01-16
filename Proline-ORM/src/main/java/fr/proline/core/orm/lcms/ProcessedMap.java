package fr.proline.core.orm.lcms;

import javax.persistence.*;

import java.io.Serializable;
import java.util.List;


/**
 * The persistent class for the processed_map database table.
 * 
 */
@Entity
@Table(name="processed_map")
@NamedQuery(name="ProcessedMap.findAll", query="SELECT p FROM ProcessedMap p")
public class ProcessedMap implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	private Long id;

	@Column(name="is_aln_reference")
	private Boolean isAlnReference;

	@Column(name="is_locked")
	private Boolean isLocked;

	@Column(name="is_master")
	private Boolean isMaster;

	@Column(name="normalization_factor")
	private float normalizationFactor;

	private Integer number;

	//bi-directional many-to-one association to FeatureClusterItem
	@OneToMany(mappedBy="processedMap")
	private List<FeatureClusterItem> featureClusterItems;

	//bi-directional many-to-one association to MasterFeatureItem
	@OneToMany(mappedBy="masterMap")
	private List<MasterFeatureItem> masterFeatureItems;

	//bi-directional one-to-one association to Map
	@OneToOne
	@JoinColumn(name="id")
	private Map map;

	//bi-directional many-to-one association to MapSet
	@ManyToOne
	@JoinColumn(name="map_set_id")
	private MapSet mapSet;

	//bi-directional many-to-one association to ProcessedMapFeatureItem
	@OneToMany(mappedBy="processedMap")
	private List<ProcessedMapFeatureItem> processedMapFeatureItems;

	public ProcessedMap() {
	}

	public Long getId() {
		return this.id;
	}

	public void setId(Long id) {
		this.id = id;
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

	public MasterFeatureItem addMasterFeatureItem(MasterFeatureItem masterFeatureItem) {
		getMasterFeatureItems().add(masterFeatureItem);
		masterFeatureItem.setMasterMap(this);

		return masterFeatureItem;
	}

	public MasterFeatureItem removeMasterFeatureItem(MasterFeatureItem masterFeatureItem) {
		getMasterFeatureItems().remove(masterFeatureItem);
		masterFeatureItem.setMasterMap(null);

		return masterFeatureItem;
	}

	public Map getMap() {
		return this.map;
	}

	public void setMap(Map map) {
		this.map = map;
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

	public ProcessedMapFeatureItem addProcessedMapFeatureItem(ProcessedMapFeatureItem processedMapFeatureItem) {
		getProcessedMapFeatureItems().add(processedMapFeatureItem);
		processedMapFeatureItem.setProcessedMap(this);

		return processedMapFeatureItem;
	}

	public ProcessedMapFeatureItem removeProcessedMapFeatureItem(ProcessedMapFeatureItem processedMapFeatureItem) {
		getProcessedMapFeatureItems().remove(processedMapFeatureItem);
		processedMapFeatureItem.setProcessedMap(null);

		return processedMapFeatureItem;
	}

	@Override
	public String toString() {
		return new StringBuilder("ProcessedMap ").append(id).toString();
	}
}
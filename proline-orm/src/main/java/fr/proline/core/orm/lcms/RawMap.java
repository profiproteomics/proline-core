package fr.proline.core.orm.lcms;

import javax.persistence.*;

import java.io.Serializable;
import java.util.List;

/**
 * The persistent class for the raw_map database table.
 * 
 */
@Entity
@Table(name = "raw_map")
@NamedQuery(name = "RawMap.findAll", query = "SELECT r FROM RawMap r")
public class RawMap extends Map implements Serializable {
	private static final long serialVersionUID = 1L;

	@Column(name = "peak_picking_software_id")
	private Long peakPickingSoftwareId;

	@Column(name = "peakel_fitting_model_id")
	private Long peakelFittingModelId;

	//bi-directional many-to-one association to FeatureMs2Event
	@OneToMany(mappedBy = "rawMap")
	private List<FeatureMs2Event> featureMs2Events;

	//uni-directional many-to-one association to ScanSequence
	@ManyToOne
	@JoinColumn(name = "scan_sequence_id")
	private ScanSequence scanSequence;

	@OneToMany
	@JoinTable(name = "processed_map_raw_map_mapping",
			joinColumns = {@JoinColumn(name = "raw_map_id")},
			inverseJoinColumns = {@JoinColumn(name = "processed_map_id")}
	)
	private List<ProcessedMap> processedMaps;

	public RawMap() {
	}

	public Long getPeakPickingSoftwareId() {
		return this.peakPickingSoftwareId;
	}

	public void setPeakPickingSoftwareId(Long peakPickingSoftwareId) {
		this.peakPickingSoftwareId = peakPickingSoftwareId;
	}

	public Long getPeakelFittingModelId() {
		return this.peakelFittingModelId;
	}

	public void setPeakelFittingModelId(Long peakelFittingModelId) {
		this.peakelFittingModelId = peakelFittingModelId;
	}

	public List<FeatureMs2Event> getFeatureMs2Events() {
		return this.featureMs2Events;
	}

	public void setFeatureMs2Events(List<FeatureMs2Event> featureMs2Events) {
		this.featureMs2Events = featureMs2Events;
	}

	public ScanSequence getScanSequence() {
		return this.scanSequence;
	}

	public void setScanSequence(ScanSequence scanSequence) {
		this.scanSequence = scanSequence;
	}

	public ProcessedMap getProcessedMap() {
		for (ProcessedMap pm : processedMaps) {
			if (!pm.getIsMaster()) return pm;
		}
		return null;
	}

	public ProcessedMap getMasterMap() {
		for (ProcessedMap pm : processedMaps) {
			if (pm.getIsMaster()) return pm;
		}
		return null;
	}

	@Override
	public String toString() {
		return new StringBuilder("RawMap ").append(getId()).append(" of ").append(scanSequence.toString()).toString();
	}
}
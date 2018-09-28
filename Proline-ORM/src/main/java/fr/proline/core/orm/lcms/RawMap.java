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
public class RawMap implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	private Long id;

	@Column(name = "peak_picking_software_id")
	private Long peakPickingSoftwareId;

	@Column(name = "peakel_fitting_model_id")
	private Long peakelFittingModelId;

	//bi-directional many-to-one association to FeatureMs2Event
	@OneToMany(mappedBy = "rawMap")
	private List<FeatureMs2Event> featureMs2Events;

	//bi-directional one-to-one association to Map
	@OneToOne
	@JoinColumn(name = "id")
	private Map map;

	//uni-directional many-to-one association to ScanSequence
	@ManyToOne
	@JoinColumn(name = "scan_sequence_id")
	private ScanSequence scanSequence;

	public RawMap() {
	}

	public Long getId() {
		return this.id;
	}

	public void setId(Long id) {
		this.id = id;
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

	public FeatureMs2Event addFeatureMs2Event(FeatureMs2Event featureMs2Event) {
		getFeatureMs2Events().add(featureMs2Event);
		featureMs2Event.setRawMap(this);

		return featureMs2Event;
	}

	public FeatureMs2Event removeFeatureMs2Event(FeatureMs2Event featureMs2Event) {
		getFeatureMs2Events().remove(featureMs2Event);
		featureMs2Event.setRawMap(null);

		return featureMs2Event;
	}

	public Map getMap() {
		return this.map;
	}

	public void setMap(Map map) {
		this.map = map;
	}

	public ScanSequence getScanSequence() {
		return this.scanSequence;
	}

	public void setScanSequence(ScanSequence scanSequence) {
		this.scanSequence = scanSequence;
	}

	@Override
	public String toString() {
		return new StringBuilder("RawMap ").append(id).append(" of ").append(scanSequence.toString()).toString();
	}
}
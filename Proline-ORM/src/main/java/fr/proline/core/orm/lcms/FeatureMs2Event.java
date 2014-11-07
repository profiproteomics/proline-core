package fr.proline.core.orm.lcms;

import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQuery;
import javax.persistence.MapsId;
import javax.persistence.Table;


/**
 * The persistent class for the feature_ms2_event database table.
 * 
 */
@Entity
@Table(name="feature_ms2_event")
@NamedQuery(name="FeatureMs2Event.findAll", query="SELECT f FROM FeatureMs2Event f")
public class FeatureMs2Event  {
	private static final long serialVersionUID = 1L;

	@EmbeddedId
	private FeatureMs2EventPK id;

	//bi-directional many-to-one association to Feature
	@ManyToOne
	@JoinColumn(name="feature_id")
	@MapsId("featureId")
	private Feature feature;

	//bi-directional many-to-one association to RawMap
	@ManyToOne
	@JoinColumn(name="run_map_id")
	private RawMap rawMap;

	//uni-directional many-to-one association to Scan
	@ManyToOne
	@JoinColumn(name="ms2_event_id")
	@MapsId("ms2_event_id")
	private Scan scan;

	public FeatureMs2Event() {
	}

	public FeatureMs2EventPK getId() {
		return this.id;
	}

	public void setId(FeatureMs2EventPK id) {
		this.id = id;
	}

	public Feature getFeature() {
		return this.feature;
	}

	public void setFeature(Feature feature) {
		this.feature = feature;
	}

	public RawMap getRawMap() {
		return this.rawMap;
	}

	public void setRawMap(RawMap rawMap) {
		this.rawMap = rawMap;
	}

	public Scan getScan() {
		return this.scan;
	}

	public void setScan(Scan scan) {
		this.scan = scan;
	}

	@Override
	public String toString() {
		return new StringBuilder("MS2FeatureEvent").append(id).toString();
	}
}
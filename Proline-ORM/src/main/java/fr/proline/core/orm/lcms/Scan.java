package fr.proline.core.orm.lcms;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the scan database table.
 * 
 */
@Entity
@NamedQuery(name="Scan.findAll", query="SELECT s FROM Scan s")
public class Scan  {
	private static final long serialVersionUID = 1L;

	@Id
	private Long id;

	@Column(name="base_peak_intensity")
	private double basePeakIntensity;

	@Column(name="base_peak_moz")
	private double basePeakMoz;

	private Integer cycle;

	@Column(name="initial_id")
	private Integer initialId;

	@Column(name="ms_level")
	private Integer msLevel;

	@Column(name="precursor_charge")
	private Integer precursorCharge;

	@Column(name="precursor_moz")
	private Double precursorMoz;

	@Column(name="serialized_properties")
	private String serializedProperties;

	private double tic;

	private float time;

	//bi-directional many-to-one association to ScanSequence
	@ManyToOne
	@JoinColumn(name="scan_sequence_id")
	private ScanSequence scanSequence;

	public Scan() {
	}

	public Long getId() {
		return this.id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public double getBasePeakIntensity() {
		return this.basePeakIntensity;
	}

	public void setBasePeakIntensity(double basePeakIntensity) {
		this.basePeakIntensity = basePeakIntensity;
	}

	public double getBasePeakMoz() {
		return this.basePeakMoz;
	}

	public void setBasePeakMoz(double basePeakMoz) {
		this.basePeakMoz = basePeakMoz;
	}

	public Integer getCycle() {
		return this.cycle;
	}

	public void setCycle(Integer cycle) {
		this.cycle = cycle;
	}

	public Integer getInitialId() {
		return this.initialId;
	}

	public void setInitialId(Integer initialId) {
		this.initialId = initialId;
	}

	public Integer getMsLevel() {
		return this.msLevel;
	}

	public void setMsLevel(Integer msLevel) {
		this.msLevel = msLevel;
	}

	public Integer getPrecursorCharge() {
		return this.precursorCharge;
	}

	public void setPrecursorCharge(Integer precursorCharge) {
		this.precursorCharge = precursorCharge;
	}

	public double getPrecursorMoz() {
		return this.precursorMoz;
	}

	public void setPrecursorMoz(double precursorMoz) {
		this.precursorMoz = precursorMoz;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public double getTic() {
		return this.tic;
	}

	public void setTic(double tic) {
		this.tic = tic;
	}

	public float getTime() {
		return this.time;
	}

	public void setTime(float time) {
		this.time = time;
	}

	public ScanSequence getScanSequence() {
		return this.scanSequence;
	}

	public void setScanSequence(ScanSequence scanSequence) {
		this.scanSequence = scanSequence;
	}

}
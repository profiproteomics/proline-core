package fr.proline.core.orm.lcms;

import javax.persistence.*;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;


/**
 * The persistent class for the scan_sequence database table.
 * 
 */
@Entity
@Table(name="scan_sequence")
@NamedQuery(name="ScanSequence.findAll", query="SELECT s FROM ScanSequence s")
public class ScanSequence implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	private Long id;

	@Column(name="max_intensity")
	private double maxIntensity;

	@Column(name="min_intensity")
	private double minIntensity;

	@Column(name="ms1_scan_count")
	private Integer ms1ScanCount;

	@Column(name="ms2_scan_count")
	private Integer ms2ScanCount;

	@Column(name="raw_file_identifier")
	private String rawFileIdentifier;

	@Column(name="serialized_properties")
	private String serializedProperties;

	//bi-directional many-to-one association to Scan
	@OneToMany(mappedBy="scanSequence")
	private List<Scan> scans;

	//uni-directional many-to-one association to Instrument
	@ManyToOne
	@JoinColumn(name = "instrument_id")
	private Instrument instrument;

	public ScanSequence() {
	}

	public Long getId() {
		return this.id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public double getMaxIntensity() {
		return this.maxIntensity;
	}

	public void setMaxIntensity(double maxIntensity) {
		this.maxIntensity = maxIntensity;
	}

	public double getMinIntensity() {
		return this.minIntensity;
	}

	public void setMinIntensity(double minIntensity) {
		this.minIntensity = minIntensity;
	}

	public Integer getMs1ScanCount() {
		return this.ms1ScanCount;
	}

	public void setMs1ScanCount(Integer ms1ScanCount) {
		this.ms1ScanCount = ms1ScanCount;
	}

	public Integer getMs2ScanCount() {
		return this.ms2ScanCount;
	}

	public void setMs2ScanCount(Integer ms2ScanCount) {
		this.ms2ScanCount = ms2ScanCount;
	}

	public String getRawFileIdentifier() {
		return this.rawFileIdentifier;
	}

	public void setRawFileName(String rawFileIdentifier) {
		this.rawFileIdentifier = rawFileIdentifier;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public List<Scan> getScans() {
		return this.scans;
	}

	public void setScans(List<Scan> scans) {
		this.scans = scans;
	}

	public Scan addScan(Scan scan) {
		if (scans == null) {
			scans = new ArrayList<>();
		}
		scans.add(scan);
		scan.setScanSequence(this);
		return scan;
	}

	public Scan removeScan(Scan scan) {
		if (scans != null) {
			scans.remove(scan);
			scan.setScanSequence(null);
			return scan;
		}
		return null;
	}

	public Instrument getInstrument() {
		return this.instrument;
	}

	public void setInstrument(Instrument instrument) {
		this.instrument = instrument;
	}

	@Override
	public String toString() {
		return rawFileIdentifier;
	}
}
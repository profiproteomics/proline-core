package fr.proline.core.orm.lcms;

import java.io.Serializable;
import javax.persistence.*;
import java.util.List;


/**
 * The persistent class for the peakel database table.
 * 
 */
@Entity
@NamedQuery(name="Peakel.findAll", query="SELECT p FROM Peakel p")
public class Peakel  {
	private static final long serialVersionUID = 1L;

	@Id
	private Long id;

	@Column(name="apex_intensity")
	private float apexIntensity;

	private float area;

	private float duration;

	@Column(name="elution_time")
	private float elutionTime;

	@Column(name="feature_count")
	private Integer featureCount;

	private float fwhm;

	@Column(name="is_overlapping")
	private Boolean isOverlapping;

	private double moz;

	@Column(name="peak_count")
	private Integer peakCount;

	private byte[] peaks;

	@Column(name="serialized_properties")
	private String serializedProperties;

	//bi-directional many-to-one association to FeaturePeakelItem
	@OneToMany(mappedBy="peakel")
	private List<FeaturePeakelItem> featurePeakelItems;

	//bi-directional many-to-one association to Map
	@ManyToOne
	private Map map;

	//uni-directional many-to-one association to Scan
	@ManyToOne
	@JoinColumn(name="apex_scan_id")
	private Scan apexScan;

	//uni-directional many-to-one association to Scan
	@ManyToOne
	@JoinColumn(name="first_scan_id")
	private Scan firstScan;

	//uni-directional many-to-one association to Scan
	@ManyToOne
	@JoinColumn(name="last_scan_id")
	private Scan lastScan;

	public Peakel() {
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

	public float getArea() {
		return this.area;
	}

	public void setArea(float area) {
		this.area = area;
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

	public Integer getFeatureCount() {
		return this.featureCount;
	}

	public void setFeatureCount(Integer featureCount) {
		this.featureCount = featureCount;
	}

	public float getFwhm() {
		return this.fwhm;
	}

	public void setFwhm(float fwhm) {
		this.fwhm = fwhm;
	}

	public Boolean getIsOverlapping() {
		return this.isOverlapping;
	}

	public void setIsOverlapping(Boolean isOverlapping) {
		this.isOverlapping = isOverlapping;
	}

	public double getMoz() {
		return this.moz;
	}

	public void setMoz(double moz) {
		this.moz = moz;
	}

	public Integer getPeakCount() {
		return this.peakCount;
	}

	public void setPeakCount(Integer peakCount) {
		this.peakCount = peakCount;
	}

	public byte[] getPeaks() {
		return this.peaks;
	}

	public void setPeaks(byte[] peaks) {
		this.peaks = peaks;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public List<FeaturePeakelItem> getFeaturePeakelItems() {
		return this.featurePeakelItems;
	}

	public void setFeaturePeakelItems(List<FeaturePeakelItem> featurePeakelItems) {
		this.featurePeakelItems = featurePeakelItems;
	}

	public FeaturePeakelItem addFeaturePeakelItem(FeaturePeakelItem featurePeakelItem) {
		getFeaturePeakelItems().add(featurePeakelItem);
		featurePeakelItem.setPeakel(this);

		return featurePeakelItem;
	}

	public FeaturePeakelItem removeFeaturePeakelItem(FeaturePeakelItem featurePeakelItem) {
		getFeaturePeakelItems().remove(featurePeakelItem);
		featurePeakelItem.setPeakel(null);

		return featurePeakelItem;
	}

	public Map getMap() {
		return this.map;
	}

	public void setMap(Map map) {
		this.map = map;
	}

	public Scan getApexScan() {
		return this.apexScan;
	}

	public void setApexScan(Scan apexScan) {
		this.apexScan = apexScan;
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

}
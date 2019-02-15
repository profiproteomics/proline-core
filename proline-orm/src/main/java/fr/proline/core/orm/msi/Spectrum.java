package fr.proline.core.orm.msi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

/**
 * The persistent class for the spectrum database table.
 * 
 */
@Entity
public class Spectrum implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	@Column(name = "initial_id")
	private int initialId;

	@Column(name = "first_cycle")
	private Integer firstCycle;

	@Column(name = "first_scan")
	private Integer firstScan;

	@Column(name = "first_time")
	private Float firstTime;

	@Column(name = "fragmentation_rule_set_id")
	private Long fragmentationRuleSetId;

	@Column(name = "intensity_list")
	private byte[] intensityList;

	@Column(name = "is_summed")
	private boolean isSummed;

	@Column(name = "last_cycle")
	private Integer lastCycle;

	@Column(name = "last_scan")
	private Integer lastScan;

	@Column(name = "last_time")
	private Float lastTime;

	@Column(name = "moz_list")
	private byte[] mozList;

	@Column(name = "peak_count")
	private int peakCount;

	@Column(name = "peaklist_id")
	private long peaklistId;

	@Column(name = "precursor_charge")
	private Integer precursorCharge;

	@Column(name = "precursor_intensity")
	private Float precursorIntensity;

	@Column(name = "precursor_moz")
	private Double precursorMoz;

	@Column(name = "serialized_properties")
	private String serializedProperties;

	private String title;

	public Spectrum() {
	}

	public long getId() {
		return id;
	}

	public void setId(final long pId) {
		id = pId;
	}

	public int getInitialId() {
		return initialId;
	}

	public void setInitialId(final int pInitialId) {
		initialId = pInitialId;
	}

	public Integer getFirstCycle() {
		return this.firstCycle;
	}

	public void setFirstCycle(Integer firstCycle) {
		this.firstCycle = firstCycle;
	}

	public Integer getFirstScan() {
		return this.firstScan;
	}

	public void setFirstScan(Integer firstScan) {
		this.firstScan = firstScan;
	}

	public Float getFirstTime() {
		return firstTime;
	}

	public void setFirstTime(final Float pFirstTime) {
		firstTime = pFirstTime;
	}

	public long getFragmentationRuleSetId() {
		return fragmentationRuleSetId;
	}

	public void setFragmentationRuleSetId(Long pFragmentationRuleSetId) {
		fragmentationRuleSetId = pFragmentationRuleSetId;
	}

	public byte[] getIntensityList() {
		return this.intensityList;
	}

	public void setIntensityList(byte[] intensityList) {
		this.intensityList = intensityList;
	}

	public boolean getIsSummed() {
		return this.isSummed;
	}

	public void setIsSummed(boolean isSummed) {
		this.isSummed = isSummed;
	}

	public Integer getLastCycle() {
		return this.lastCycle;
	}

	public void setLastCycle(Integer lastCycle) {
		this.lastCycle = lastCycle;
	}

	public Integer getLastScan() {
		return this.lastScan;
	}

	public void setLastScan(Integer lastScan) {
		this.lastScan = lastScan;
	}

	public Float getLastTime() {
		return lastTime;
	}

	public void setLastTime(final Float pLastTime) {
		lastTime = pLastTime;
	}

	public byte[] getMozList() {
		return this.mozList;
	}

	public void setMozList(byte[] mozList) {
		this.mozList = mozList;
	}

	public int getPeakCount() {
		return peakCount;
	}

	public void setPeakCount(final int pPeakCount) {
		peakCount = pPeakCount;
	}

	public long getPeaklistId() {
		return peaklistId;
	}

	public void setPeaklistId(final long pPeaklistId) {
		peaklistId = pPeaklistId;
	}

	public Integer getPrecursorCharge() {
		return this.precursorCharge;
	}

	public void setPrecursorCharge(Integer precursorCharge) {
		this.precursorCharge = precursorCharge;
	}

	public Float getPrecursorIntensity() {
		return this.precursorIntensity;
	}

	public void setPrecursorIntensity(Float precursorIntensity) {
		this.precursorIntensity = precursorIntensity;
	}

	public Double getPrecursorMoz() {
		return precursorMoz;
	}

	public void setPrecursorMoz(final Double pPrecursorMoz) {
		precursorMoz = pPrecursorMoz;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public String getTitle() {
		return this.title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

}

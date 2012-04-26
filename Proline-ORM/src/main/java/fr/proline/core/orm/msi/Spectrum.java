package fr.proline.core.orm.msi;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the spectrum database table.
 * 
 */
@Entity
public class Spectrum implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	@Column(name="first_cycle")
	private Integer firstCycle;

	@Column(name="first_scan")
	private Integer firstScan;

	@Column(name="first_time")
	private Float firstTime;

	@Column(name="instrument_config_id")
	private Integer instrumentConfigId;

	@Column(name="intensity_list")
	private byte[] intensityList;

	@Column(name="is_summed")
	private Boolean isSummed;

	@Column(name="last_cycle")
	private Integer lastCycle;

	@Column(name="last_scan")
	private Integer lastScan;

	@Column(name="last_time")
	private Float lastTime;

	@Column(name="moz_list")
	private byte[] mozList;

	@Column(name="peak_count")
	private Integer peakCount;

	@Column(name="peaklist_id")
	private Integer peaklistId;

	@Column(name="precursor_charge")
	private Integer precursorCharge;

	@Column(name="precursor_intensity")
	private Float precursorIntensity;

	@Column(name="precursor_moz")
	private Double precursorMoz;

	@Column(name="serialized_properties")
	private String serializedProperties;

	private String title;

    public Spectrum() {
    }

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
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

	public float getFirstTime() {
		return this.firstTime;
	}

	public void setFirstTime(float firstTime) {
		this.firstTime = firstTime;
	}

	public Integer getInstrumentConfigId() {
		return this.instrumentConfigId;
	}

	public void setInstrumentConfigId(Integer instrumentConfigId) {
		this.instrumentConfigId = instrumentConfigId;
	}

	public byte[] getIntensityList() {
		return this.intensityList;
	}

	public void setIntensityList(byte[] intensityList) {
		this.intensityList = intensityList;
	}

	public Boolean getIsSummed() {
		return this.isSummed;
	}

	public void setIsSummed(Boolean isSummed) {
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

	public float getLastTime() {
		return this.lastTime;
	}

	public void setLastTime(float lastTime) {
		this.lastTime = lastTime;
	}

	public byte[] getMozList() {
		return this.mozList;
	}

	public void setMozList(byte[] mozList) {
		this.mozList = mozList;
	}

	public Integer getPeakCount() {
		return this.peakCount;
	}

	public void setPeakCount(Integer peakCount) {
		this.peakCount = peakCount;
	}

	public Integer getPeaklistId() {
		return this.peaklistId;
	}

	public void setPeaklistId(Integer peaklistId) {
		this.peaklistId = peaklistId;
	}

	public Integer getPrecursorCharge() {
		return this.precursorCharge;
	}

	public void setPrecursorCharge(Integer precursorCharge) {
		this.precursorCharge = precursorCharge;
	}

	public float getPrecursorIntensity() {
		return this.precursorIntensity;
	}

	public void setPrecursorIntensity(float precursorIntensity) {
		this.precursorIntensity = precursorIntensity;
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

	public String getTitle() {
		return this.title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

}
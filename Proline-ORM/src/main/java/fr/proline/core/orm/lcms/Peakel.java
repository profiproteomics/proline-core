package fr.proline.core.orm.lcms;

import javax.persistence.*;

import org.msgpack.MessagePack;
import org.msgpack.annotation.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 * The persistent class for the peakel database table.
 * 
 */
@Entity
@NamedQuery(name="Peakel.findAll", query="SELECT p FROM Peakel p")
public class Peakel implements Serializable {
	private static final Logger LOG = LoggerFactory.getLogger(Peakel.class);
	
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
	
	@Transient
	private List<Peak> peakList;
	
	@Transient
	private Integer isotopeIndex;

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
	
	public void setPeakList(List<Peak> peakList) {
		this.peakList = peakList;
	}

	@SuppressWarnings("unchecked")
	public List<Peak> getPeakList() {
		if (this.peakList == null) {
			try {
				MessagePack msgpack = new MessagePack();
				List<Peak> pl = (List<Peak>) msgpack.read(this.getPeaks());
				// the return list is not a Peak list: ArrayValueImpl object instead of Peak
				this.peakList = new ArrayList<Peak>();
				for (Iterator iterator = pl.iterator(); iterator.hasNext();) {
					AbstractList p = (AbstractList) iterator.next();
					Peak peak = new Peak(new Double(p.get(0).toString()), new Float(p.get(1).toString()), new Float(p.get(2).toString()));
					this.peakList.add(peak);
				}
			} catch (Exception e) {
				LOG.warn("Error Parsing PeakList ",e);
				this.peakList = null;
			} 
		}
		return this.peakList;
	}
	

	public Integer getIsotopeIndex() {
		return isotopeIndex;
	}

	public void setIsotopeIndex(Integer isotopeIndex) {
		this.isotopeIndex = isotopeIndex;
	}


	/* peak represented in the peaks BLOB of Peakel*/
	@Message
	public class Peak {
		
		private Double  moz;
		private Float elutionTime;
		private Float intensity;
		
		public Peak() {
			super();
		}

		public Peak(Double moz, Float elutionTime, Float intensity) {
			super();
			this.moz = moz;
			this.elutionTime = elutionTime;
			this.intensity = intensity;
		}

		public Double getMoz() {
			return moz;
		}

		public void setMoz(Double moz) {
			this.moz = moz;
		}

		public Float getElutionTime() {
			return elutionTime;
		}

		public void setElutionTime(Float elutionTime) {
			this.elutionTime = elutionTime;
		}

		public Float getIntensity() {
			return intensity;
		}

		public void setIntensity(Float intensity) {
			this.intensity = intensity;
		}
		
		
	}

}

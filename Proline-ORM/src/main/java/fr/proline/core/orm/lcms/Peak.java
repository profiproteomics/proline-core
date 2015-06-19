package fr.proline.core.orm.lcms;

/* peak represented in the peaks BLOB of Peakel*/
public class Peak {
	private Long scanId;
	private Double  moz;
	private Float elutionTime;
	private Float intensity;
	
	public Peak() {
		super();
	}

	

	public Peak(Long scanId, Double moz, Float elutionTime,
			Float intensity) {
		super();
		this.scanId = scanId;
		this.moz = moz;
		this.elutionTime = elutionTime;
		this.intensity = intensity;
	}



	public Long getScanId() {
		return scanId;
	}

	public void setScanId(Long scanId) {
		this.scanId = scanId;
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

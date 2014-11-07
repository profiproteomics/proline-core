package fr.proline.core.orm.lcms;

import java.io.Serializable;

import javax.persistence.*;

/**
 * The primary key class for the feature_ms2_event database table.
 * 
 */
@Embeddable
public class FeatureMs2EventPK implements Serializable {
	//default serial version id, required for serializable classes.
	private static final long serialVersionUID = 1L;

	@Column(name="feature_id", insertable=false, updatable=false)
	private Long featureId;

	@Column(name="ms2_event_id", insertable=false, updatable=false)
	private Long ms2EventId;

	public FeatureMs2EventPK() {
	}
	public Long getFeatureId() {
		return this.featureId;
	}
	public void setFeatureId(Long featureId) {
		this.featureId = featureId;
	}
	public Long getMs2EventId() {
		return this.ms2EventId;
	}
	public void setMs2EventId(Long ms2EventId) {
		this.ms2EventId = ms2EventId;
	}

	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof FeatureMs2EventPK)) {
			return false;
		}
		FeatureMs2EventPK castOther = (FeatureMs2EventPK)other;
		return 
			this.featureId.equals(castOther.featureId)
			&& this.ms2EventId.equals(castOther.ms2EventId);
	}

	public int hashCode() {
		final int prime = 31;
		int hash = 17;
		hash = hash * prime + this.featureId.hashCode();
		hash = hash * prime + this.ms2EventId.hashCode();
		
		return hash;
	}
	
	@Override
	public String toString() {
		return new StringBuilder("ft ").append(featureId).append(" -> ms2 ").append(ms2EventId).append(')').toString();
	}
}
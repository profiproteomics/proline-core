package fr.proline.core.orm.lcms;

import java.io.Serializable;
import javax.persistence.*;

/**
 * The primary key class for the feature_peakel_item database table.
 * 
 */
@Embeddable
public class FeaturePeakelItemPK implements Serializable {
	//default serial version id, required for serializable classes.
	private static final long serialVersionUID = 1L;

	@Column(name="feature_id", insertable=false, updatable=false)
	private Long featureId;

	@Column(name="peakel_id", insertable=false, updatable=false)
	private Long peakelId;

	public FeaturePeakelItemPK() {
	}
	public Long getFeatureId() {
		return this.featureId;
	}
	public void setFeatureId(Long featureId) {
		this.featureId = featureId;
	}
	public Long getPeakelId() {
		return this.peakelId;
	}
	public void setPeakelId(Long peakelId) {
		this.peakelId = peakelId;
	}

	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof FeaturePeakelItemPK)) {
			return false;
		}
		FeaturePeakelItemPK castOther = (FeaturePeakelItemPK)other;
		return 
			this.featureId.equals(castOther.featureId)
			&& this.peakelId.equals(castOther.peakelId);
	}

	public int hashCode() {
		final int prime = 31;
		int hash = 17;
		hash = hash * prime + this.featureId.hashCode();
		hash = hash * prime + this.peakelId.hashCode();
		
		return hash;
	}
}
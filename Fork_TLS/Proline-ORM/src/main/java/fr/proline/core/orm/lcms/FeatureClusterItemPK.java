package fr.proline.core.orm.lcms;

import java.io.Serializable;

import javax.persistence.*;

/**
 * The primary key class for the feature_cluster_item database table.
 * 
 */
@Embeddable
public class FeatureClusterItemPK implements Serializable {
	//default serial version id, required for serializable classes.
	private static final long serialVersionUID = 1L;

	@Column(name="cluster_feature_id", insertable=false, updatable=false)
	private Long clusterFeatureId;

	@Column(name="sub_feature_id", insertable=false, updatable=false)
	private Long subFeatureId;

	public FeatureClusterItemPK() {
	}
	public Long getClusterFeatureId() {
		return this.clusterFeatureId;
	}
	public void setClusterFeatureId(Long clusterFeatureId) {
		this.clusterFeatureId = clusterFeatureId;
	}
	public Long getSubFeatureId() {
		return this.subFeatureId;
	}
	public void setSubFeatureId(Long subFeatureId) {
		this.subFeatureId = subFeatureId;
	}

	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof FeatureClusterItemPK)) {
			return false;
		}
		FeatureClusterItemPK castOther = (FeatureClusterItemPK)other;
		return 
			this.clusterFeatureId.equals(castOther.clusterFeatureId)
			&& this.subFeatureId.equals(castOther.subFeatureId);
	}

	public int hashCode() {
		final int prime = 31;
		int hash = 17;
		hash = hash * prime + this.clusterFeatureId.hashCode();
		hash = hash * prime + this.subFeatureId.hashCode();
		
		return hash;
	}
	
	@Override
	public String toString() {
		return new StringBuilder("cft ").append(clusterFeatureId).append(" -> ft ").append(subFeatureId).append(')').toString();
	}
}
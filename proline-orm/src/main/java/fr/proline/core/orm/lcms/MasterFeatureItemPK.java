package fr.proline.core.orm.lcms;

import java.io.Serializable;

import javax.persistence.*;

/**
 * The primary key class for the master_feature_item database table.
 * 
 */
@Embeddable
public class MasterFeatureItemPK implements Serializable {
	//default serial version id, required for serializable classes.
	private static final long serialVersionUID = 1L;

	@Column(name = "master_feature_id", insertable = false, updatable = false)
	private Long masterFeatureId;

	@Column(name = "child_feature_id", insertable = false, updatable = false)
	private Long childFeatureId;

	public MasterFeatureItemPK() {
	}

	public Long getMasterFeatureId() {
		return this.masterFeatureId;
	}

	public void setMasterFeatureId(Long masterFeatureId) {
		this.masterFeatureId = masterFeatureId;
	}

	public Long getChildFeatureId() {
		return this.childFeatureId;
	}

	public void setChildFeatureId(Long childFeatureId) {
		this.childFeatureId = childFeatureId;
	}

	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof MasterFeatureItemPK)) {
			return false;
		}
		MasterFeatureItemPK castOther = (MasterFeatureItemPK) other;
		return this.masterFeatureId.equals(castOther.masterFeatureId)
			&& this.childFeatureId.equals(castOther.childFeatureId);
	}

	public int hashCode() {
		final int prime = 31;
		int hash = 17;
		hash = hash * prime + this.masterFeatureId.hashCode();
		hash = hash * prime + this.childFeatureId.hashCode();

		return hash;
	}

	@Override
	public String toString() {
		return new StringBuilder("ft ").append(masterFeatureId).append(" -> ft ").append(childFeatureId).append(')').toString();
	}
}
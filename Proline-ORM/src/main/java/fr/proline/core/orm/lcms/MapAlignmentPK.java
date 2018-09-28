package fr.proline.core.orm.lcms;

import java.io.Serializable;
import javax.persistence.*;

/**
 * The primary key class for the map_alignment database table.
 * 
 */
@Embeddable
public class MapAlignmentPK implements Serializable {
	//default serial version id, required for serializable classes.
	private static final long serialVersionUID = 1L;

	@Column(name = "from_map_id", insertable = false, updatable = false)
	private Long fromMapId;

	@Column(name = "to_map_id", insertable = false, updatable = false)
	private Long toMapId;

	@Column(name = "mass_start")
	private float massStart;

	@Column(name = "mass_end")
	private float massEnd;

	public MapAlignmentPK() {
	}

	public Long getFromMapId() {
		return this.fromMapId;
	}

	public void setFromMapId(Long fromMapId) {
		this.fromMapId = fromMapId;
	}

	public Long getToMapId() {
		return this.toMapId;
	}

	public void setToMapId(Long toMapId) {
		this.toMapId = toMapId;
	}

	public float getMassStart() {
		return this.massStart;
	}

	public void setMassStart(float massStart) {
		this.massStart = massStart;
	}

	public float getMassEnd() {
		return this.massEnd;
	}

	public void setMassEnd(float massEnd) {
		this.massEnd = massEnd;
	}

	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof MapAlignmentPK)) {
			return false;
		}
		MapAlignmentPK castOther = (MapAlignmentPK) other;
		return this.fromMapId.equals(castOther.fromMapId)
			&& this.toMapId.equals(castOther.toMapId)
			&& (this.massStart == castOther.massStart)
			&& (this.massEnd == castOther.massEnd);
	}

	public int hashCode() {
		final int prime = 31;
		int hash = 17;
		hash = hash * prime + this.fromMapId.hashCode();
		hash = hash * prime + this.toMapId.hashCode();
		hash = hash * prime + java.lang.Float.floatToIntBits(this.massStart);
		hash = hash * prime + java.lang.Float.floatToIntBits(this.massEnd);

		return hash;
	}
}
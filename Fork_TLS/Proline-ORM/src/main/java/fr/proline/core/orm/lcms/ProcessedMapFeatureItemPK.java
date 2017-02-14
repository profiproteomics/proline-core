package fr.proline.core.orm.lcms;

import java.io.Serializable;
import javax.persistence.*;

/**
 * The primary key class for the processed_map_feature_item database table.
 * 
 */
@Embeddable
public class ProcessedMapFeatureItemPK implements Serializable {
	//default serial version id, required for serializable classes.
	private static final long serialVersionUID = 1L;

	@Column(name="processed_map_id", insertable=false, updatable=false)
	private Long processedMapId;

	@Column(name="feature_id", insertable=false, updatable=false)
	private Long featureId;

	public ProcessedMapFeatureItemPK() {
	}
	public Long getProcessedMapId() {
		return this.processedMapId;
	}
	public void setProcessedMapId(Long processedMapId) {
		this.processedMapId = processedMapId;
	}
	public Long getFeatureId() {
		return this.featureId;
	}
	public void setFeatureId(Long featureId) {
		this.featureId = featureId;
	}

	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof ProcessedMapFeatureItemPK)) {
			return false;
		}
		ProcessedMapFeatureItemPK castOther = (ProcessedMapFeatureItemPK)other;
		return 
			this.processedMapId.equals(castOther.processedMapId)
			&& this.featureId.equals(castOther.featureId);
	}

	public int hashCode() {
		final int prime = 31;
		int hash = 17;
		hash = hash * prime + this.processedMapId.hashCode();
		hash = hash * prime + this.featureId.hashCode();
		
		return hash;
	}
}
package fr.proline.core.orm.lcms;

import java.io.Serializable;
import javax.persistence.*;

/**
 * The primary key class for the processed_map_raw_map_mapping database table.
 * 
 */
@Embeddable
public class ProcessedMapRawMapMappingPK implements Serializable {
	//default serial version id, required for serializable classes.
	private static final long serialVersionUID = 1L;

	@Column(name = "processed_map_id", insertable = false, updatable = false)
	private Long processedMapId;

	@Column(name = "raw_map_id", insertable = false, updatable = false)
	private Long rawMapId;

	public ProcessedMapRawMapMappingPK() {
	}

	public Long getProcessedMapId() {
		return this.processedMapId;
	}

	public void setProcessedMapId(Long processedMapId) {
		this.processedMapId = processedMapId;
	}

	public Long getRawMapId() {
		return this.rawMapId;
	}

	public void setRawMapId(Long rawMapId) {
		this.rawMapId = rawMapId;
	}

	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof ProcessedMapRawMapMappingPK)) {
			return false;
		}
		ProcessedMapRawMapMappingPK castOther = (ProcessedMapRawMapMappingPK) other;
		return this.processedMapId.equals(castOther.processedMapId)
			&& this.rawMapId.equals(castOther.rawMapId);
	}

	public int hashCode() {
		final int prime = 31;
		int hash = 17;
		hash = hash * prime + this.processedMapId.hashCode();
		hash = hash * prime + this.rawMapId.hashCode();

		return hash;
	}
}
package fr.proline.core.orm.lcms;

import java.io.Serializable;

import javax.persistence.*;

/**
 * The persistent class for the processed_map_raw_map_mapping database table.
 * 
 */
@Entity
@Table(name = "processed_map_raw_map_mapping")
@NamedQuery(name = "ProcessedMapRawMapMapping.findAll", query = "SELECT p FROM ProcessedMapRawMapMapping p")
public class ProcessedMapRawMapMapping {
	private static final long serialVersionUID = 1L;

	@EmbeddedId
	private ProcessedMapRawMapMappingPK id;

	// bi-directional many-to-one association to RawMap
	@ManyToOne
	@JoinColumn(name = "raw_map_id")
	@MapsId("rawMapId")
	private RawMap rawMap;

	// bi-directional many-to-one association to RawMap
	@ManyToOne
	@JoinColumn(name = "processed_map_id")
	@MapsId("processedMapId")
	private ProcessedMap processedMap;

	public ProcessedMapRawMapMapping() {
	}

	public ProcessedMapRawMapMappingPK getId() {
		return this.id;
	}

	public void setId(ProcessedMapRawMapMappingPK id) {
		this.id = id;
	}

}
package fr.proline.core.orm.lcms;

import javax.persistence.*;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.List;

/**
 * The persistent class for the map_set database table.
 * 
 */
@Entity
@Table(name = "map_set")
@NamedQuery(name = "MapSet.findAll", query = "SELECT m FROM MapSet m")
public class MapSet implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	private Long id;

	@Column(name = "creation_timestamp")
	private Timestamp creationTimestamp;

	@Column(name = "map_count")
	private Integer mapCount;

	private String name;

	@Column(name = "serialized_properties")
	private String serializedProperties;

	//bi-directional many-to-one association to MapAlignment
	@OneToMany(mappedBy = "mapSet")
	private List<MapAlignment> mapAlignments;

	//uni-directional many-to-one association to ProcessedMap
	@ManyToOne
	@JoinColumn(name = "master_map_id")
	private ProcessedMap masterMap;

	//uni-directional many-to-one association to ProcessedMap
	@ManyToOne
	@JoinColumn(name = "aln_reference_map_id")
	private ProcessedMap alnReferenceMap;

	//bi-directional many-to-one association to ProcessedMap
	@OneToMany(mappedBy = "mapSet")
	private List<ProcessedMap> processedMaps;

	public MapSet() {
	}

	public Long getId() {
		return this.id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Timestamp getCreationTimestamp() {
		return this.creationTimestamp;
	}

	public void setCreationTimestamp(Timestamp creationTimestamp) {
		this.creationTimestamp = creationTimestamp;
	}

	public Integer getMapCount() {
		return this.mapCount;
	}

	public void setMapCount(Integer mapCount) {
		this.mapCount = mapCount;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public List<MapAlignment> getMapAlignments() {
		return this.mapAlignments;
	}

	public void setMapAlignments(List<MapAlignment> mapAlignments) {
		this.mapAlignments = mapAlignments;
	}

	public MapAlignment addMapAlignment(MapAlignment mapAlignment) {
		getMapAlignments().add(mapAlignment);
		mapAlignment.setMapSet(this);

		return mapAlignment;
	}

	public MapAlignment removeMapAlignment(MapAlignment mapAlignment) {
		getMapAlignments().remove(mapAlignment);
		mapAlignment.setMapSet(null);

		return mapAlignment;
	}

	public ProcessedMap getMasterMap() {
		return this.masterMap;
	}

	public void setMasterMap(ProcessedMap masterMap) {
		this.masterMap = masterMap;
	}

	public ProcessedMap getAlnReferenceMap() {
		return this.alnReferenceMap;
	}

	public void setAlnReferenceMap(ProcessedMap alnReferenceMap) {
		this.alnReferenceMap = alnReferenceMap;
	}

	public List<ProcessedMap> getProcessedMaps() {
		return this.processedMaps;
	}

	public void setProcessedMaps(List<ProcessedMap> processedMaps) {
		this.processedMaps = processedMaps;
	}

	public ProcessedMap addProcessedMap(ProcessedMap processedMap) {
		getProcessedMaps().add(processedMap);
		processedMap.setMapSet(this);

		return processedMap;
	}

	public ProcessedMap removeProcessedMap(ProcessedMap processedMap) {
		getProcessedMaps().remove(processedMap);
		processedMap.setMapSet(null);

		return processedMap;
	}

}
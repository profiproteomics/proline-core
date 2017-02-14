package fr.proline.core.orm.lcms;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * The persistent class for the map_alignment database table.
 * 
 */
@Entity
@Table(name="map_alignment")
@NamedQuery(name="MapAlignment.findAll", query="SELECT m FROM MapAlignment m")
public class MapAlignment implements Serializable {
	private static final long serialVersionUID = 1L;
	
	private static final Logger LOG = LoggerFactory.getLogger(MapAlignment.class);

	@EmbeddedId
	private MapAlignmentPK id;

	@Column(name="delta_time_list")
	private String deltaTimeList;

	@Column(name="serialized_properties")
	private String serializedProperties;

	@Column(name="time_list")
	private String timeList;

	//bi-directional many-to-one association to MapSet
	@ManyToOne
	@JoinColumn(name="map_set_id")
	private MapSet mapSet;

	//uni-directional many-to-one association to ProcessedMap
	@ManyToOne
	@JoinColumn(name="from_map_id")
	@MapsId("fromMapId")
	private ProcessedMap sourceMap;

	//uni-directional many-to-one association to ProcessedMap
	@ManyToOne
	@JoinColumn(name="to_map_id")
	@MapsId("toMapId")
	private ProcessedMap destinationMap;
	
	@Transient
	private List<MapTime> mapTimeList;

	public MapAlignment() {
	}

	public MapAlignmentPK getId() {
		return this.id;
	}

	public void setId(MapAlignmentPK id) {
		this.id = id;
	}

	public String getDeltaTimeList() {
		return this.deltaTimeList;
	}

	public void setDeltaTimeList(String deltaTimeList) {
		this.deltaTimeList = deltaTimeList;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public String getTimeList() {
		return this.timeList;
	}

	public void setTimeList(String timeList) {
		this.timeList = timeList;
	}

	public MapSet getMapSet() {
		return this.mapSet;
	}

	public void setMapSet(MapSet mapSet) {
		this.mapSet = mapSet;
	}

	public ProcessedMap getSourceMap() {
		return this.sourceMap;
	}

	public void setSourceMap(ProcessedMap sourceMap) {
		this.sourceMap = sourceMap;
	}

	public ProcessedMap getDestinationMap() {
		return this.destinationMap;
	}

	public void setDestinationMap(ProcessedMap destinationMap) {
		this.destinationMap = destinationMap;
	}

	public void setMapTimeList(List<MapTime> mapTimeList) {
		this.mapTimeList = mapTimeList;
	}

	public List<MapTime> getMapTimeList() {
		if (this.mapTimeList == null) {
			try {
				this.mapTimeList = new ArrayList<MapTime>();
				String[] timeSplited = this.timeList.split(" ");
				String[] deltaTimeSplited = this.deltaTimeList.split(" ");
				if (timeSplited.length != deltaTimeSplited.length){
					LOG.error("Error Parsing TimeList: time and delta time are different ");
				}else{
					for (int i=0; i<timeSplited.length; i++){
						Double time = Double.parseDouble(timeSplited[i]);
						Double deltaTime = Double.parseDouble(deltaTimeSplited[i]);
						this.mapTimeList.add(new MapTime(time, deltaTime));
					}
				}
				
			} catch (Exception e) {
				LOG.error("Error Parsing TimeList ",e);
				this.mapTimeList = null;
			} 
		}
		return this.mapTimeList;
	}
}
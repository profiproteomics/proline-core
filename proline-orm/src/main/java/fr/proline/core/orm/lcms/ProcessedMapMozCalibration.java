package fr.proline.core.orm.lcms;


import fr.proline.core.orm.util.JsonSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The persistent class for the processed_map_moz_calibration database table.
 *
 */
@Entity
@Table(name = "processed_map_moz_calibration")
@NamedQuery(name = "ProcessedMapMozCalibration.findAll", query = "SELECT p FROM ProcessedMapMozCalibration p")
public class ProcessedMapMozCalibration {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(ProcessedMapMozCalibration.class);

    @EmbeddedId
    private ProcessedMapMozCalibrationPK id;

    @Column(name = "delta_moz_list")
    private String deltaMozList;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    // WARNING VDS: Currently the way moz_calibration is used : this contains time list !
    @Column(name = "moz_list")
    private String mozList;

    //uni-directional many-to-one association to ProcessedMap
    @ManyToOne
    @JoinColumn(name = "processed_map_id")
    @MapsId("processedMapId")
    private ProcessedMap processedMap;

    @Transient
    private List<MapTime> processedMapMozList;

    @Transient
    private Map<String, Object> serializedPropertiesMap = null;


    public ProcessedMapMozCalibration() {
    }

    public ProcessedMapMozCalibrationPK getId() {
        return id;
    }

    public void setId(ProcessedMapMozCalibrationPK id) {
        this.id = id;
    }

    public String getDeltaMozList() {
        return deltaMozList;
    }

    public void setDeltaMozList(String deltaMozList) {
        this.deltaMozList = deltaMozList;
    }

    public String getSerializedProperties() {
        return serializedProperties;
    }

    public void setSerializedProperties(String serializedProperties) {
        this.serializedProperties = serializedProperties;
    }

    /**
     * Return the list of value of this object : Currently time is returned here !
     * @return
     */
    public String getMozList() {
        return mozList;
    }

    public void setMozList(String mozList) {
        this.mozList = mozList;
    }

    public ProcessedMap getProcessedMap() {
        return processedMap;
    }

    public void setProcessedMap(ProcessedMap processedMap) {
        this.processedMap = processedMap;
    }


    public void setProcessedMapMozList(List<MapTime> pmapTimeist) {
        this.processedMapMozList = pmapTimeist;
    }

    public List<MapTime> getProcessedMapMozList() {
        if (this.processedMapMozList == null) {
            try {
                this.processedMapMozList = new ArrayList<>();
                String[] timeSplited = this.mozList.split(" ");
                String[] deltaMozSplited = this.deltaMozList.split(" ");
                if (timeSplited.length != deltaMozSplited.length) {
                    LOG.error("Error Parsing MoZ List : time and delta moz are different ");
                } else {
                    for (int i = 0; i < timeSplited.length; i++) {
                        Double time = Double.parseDouble(timeSplited[i]);
                        Double deltaMoz = Double.parseDouble(deltaMozSplited[i]);
                        this.processedMapMozList.add(new MapTime(time, deltaMoz));
                    }
                }

            } catch (Exception e) {
                LOG.error("Error Parsing processedMapMozList ", e);
                this.processedMapMozList = null;
            }
        }
        return this.processedMapMozList;
    }


    public java.util.Map<String, Object> getSerializedPropertiesAsMap() throws Exception {
        if ((serializedPropertiesMap == null) && (serializedProperties != null)) {
            serializedPropertiesMap = JsonSerializer.getMapper().readValue(getSerializedProperties(),
                    Map.class);
        }
        return serializedPropertiesMap;
    }


}

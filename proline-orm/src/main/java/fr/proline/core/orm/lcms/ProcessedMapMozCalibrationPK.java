package fr.proline.core.orm.lcms;

import javax.persistence.*;
import java.io.Serializable;

/**
 * The primary key class for the processed_map_moz_calibration database table.
 *
 */
@Embeddable
public class ProcessedMapMozCalibrationPK implements Serializable  {
    //default serial version id, required for serializable classes.
    private static final long serialVersionUID = 1L;

    @Column(name = "processed_map_id", insertable = false, updatable = false)
    private Long processedMapId;

    @Column(name = "scan_id", insertable = false, updatable = false)
    private Long scanMapId;


    public ProcessedMapMozCalibrationPK() {
    }

    public Long getProcessedMapId() {
        return processedMapId;
    }

    public void setProcessedMapId(Long processedMapId) {
        this.processedMapId = processedMapId;
    }

    public Long getScanMapId() {
        return scanMapId;
    }

    public void setScanMapId(Long scanMapId) {
        this.scanMapId = scanMapId;
    }

    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProcessedMapMozCalibrationPK)) {
            return false;
        }
        ProcessedMapMozCalibrationPK castOther = (ProcessedMapMozCalibrationPK) other;
        return this.processedMapId.equals(castOther.processedMapId)
                && this.scanMapId.equals(castOther.scanMapId);
    }

    public int hashCode() {
        final int prime = 31;
        int hash = 17;
        hash = hash * prime + this.processedMapId.hashCode();
        hash = hash * prime + this.scanMapId.hashCode();

        return hash;
    }
}

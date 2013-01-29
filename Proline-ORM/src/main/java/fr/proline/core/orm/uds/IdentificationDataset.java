package fr.proline.core.orm.uds;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.PrimaryKeyJoinColumn;
import javax.persistence.Table;

/**
 * The persistent class for the run_identification database table.
 * 
 */
@Entity
@Table(name = "run_identification")
@PrimaryKeyJoinColumn(name="id", referencedColumnName="id")
public class IdentificationDataset extends Dataset implements Serializable {
    private static final long serialVersionUID = 1L;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    // uni-directional many-to-one association to Run
    @ManyToOne
    @JoinColumn(name = "raw_file_name")
    private RawFile rawFile;

    // uni-directional many-to-one association to Run
    @ManyToOne
    @JoinColumn(name = "run_id")
    private Run run;

    public IdentificationDataset() {
    }


    public String getSerializedProperties() {
	return this.serializedProperties;
    }

    public void setSerializedProperties(String serializedProperties) {
	this.serializedProperties = serializedProperties;
    }

    public RawFile getRawFile() {
	return rawFile;
    }

    public void setRawFile(RawFile rawfile) {
	this.rawFile = rawfile;
    }

    public Run getRun() {
	return run;
    }

    public void setRun(Run run) {
	this.run = run;
    }

}
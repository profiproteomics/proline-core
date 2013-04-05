package fr.proline.core.orm.uds;

import java.io.Serializable;
import javax.persistence.*;

import java.sql.Timestamp;
import java.util.Set;


/**
 * The persistent class for the raw_file database table.
 * 
 */
@Entity
@Table(name="raw_file")
public class RawFile implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	@Column(name="name")
	private String rawFileName;

	@Column(name="creation_timestamp")
	private Timestamp creationTimestamp;

	private String directory;

	private String extension;

	@Column(name="instrument_id")
	private Integer instrumentId;

	@Column(name="owner_id")
	private Integer ownerId;

	@Column(name="serialized_properties")
	private String serializedProperties;

	//bi-directional many-to-one association to Run
	@OneToMany(mappedBy="rawFile")
	private Set<Run> runs;

    public RawFile() {
    }

	public String getRawFileName() {
		return this.rawFileName;
	}

	public void setRawFileName(String rawFileName) {
		this.rawFileName = rawFileName;
	}

	public Timestamp getCreationTimestamp() {
		return this.creationTimestamp;
	}

	public void setCreationTimestamp(Timestamp creationTimestamp) {
		this.creationTimestamp = creationTimestamp;
	}

	public String getDirectory() {
		return this.directory;
	}

	public void setDirectory(String directory) {
		this.directory = directory;
	}

	public String getExtension() {
		return this.extension;
	}

	public void setExtension(String extension) {
		this.extension = extension;
	}

	public Integer getInstrumentId() {
		return this.instrumentId;
	}

	public void setInstrumentId(Integer instrumentId) {
		this.instrumentId = instrumentId;
	}

	public Integer getOwnerId() {
		return this.ownerId;
	}

	public void setOwnerId(Integer ownerId) {
		this.ownerId = ownerId;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public Set<Run> getRuns() {
		return this.runs;
	}

	public void setRuns(Set<Run> runs) {
		this.runs = runs;
	}
	
}
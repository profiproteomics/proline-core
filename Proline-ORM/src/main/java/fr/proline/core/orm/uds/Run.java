package fr.proline.core.orm.uds;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the run database table.
 * 
 */
@Entity
public class Run implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	private String analyst;

	private float duration;

	@Column(name="lc_method")
	private String lcMethod;

	@Column(name="ms_method")
	private String msMethod;

	private Integer number;

	@Column(name="run_start")
	private float runStart;

	@Column(name="run_stop")
	private float runStop;

	@Column(name="serialized_properties")
	private String serializedProperties;

	//bi-directional many-to-one association to RawFile
    @ManyToOne
	@JoinColumn(name="raw_file_name")
	private RawFile rawFile;

    public Run() {
    }

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getAnalyst() {
		return this.analyst;
	}

	public void setAnalyst(String analyst) {
		this.analyst = analyst;
	}

	public float getDuration() {
		return this.duration;
	}

	public void setDuration(float duration) {
		this.duration = duration;
	}

	public String getLcMethod() {
		return this.lcMethod;
	}

	public void setLcMethod(String lcMethod) {
		this.lcMethod = lcMethod;
	}

	public String getMsMethod() {
		return this.msMethod;
	}

	public void setMsMethod(String msMethod) {
		this.msMethod = msMethod;
	}

	public Integer getNumber() {
		return this.number;
	}

	public void setNumber(Integer number) {
		this.number = number;
	}

	public float getRunStart() {
		return this.runStart;
	}

	public void setRunStart(float runStart) {
		this.runStart = runStart;
	}

	public float getRunStop() {
		return this.runStop;
	}

	public void setRunStop(float runStop) {
		this.runStop = runStop;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public RawFile getRawFile() {
		return this.rawFile;
	}

	public void setRawFile(RawFile rawFile) {
		this.rawFile = rawFile;
	}
	
}
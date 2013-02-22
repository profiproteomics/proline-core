package fr.proline.core.orm.uds;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the spec_title_parsing_rule database table.
 * 
 */
@Entity
@Table(name="spec_title_parsing_rule")
public class SpectrumTitleParsingRule implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	@Column(name="first_cycle")
	private String firstCycle;

	@Column(name="first_scan")
	private String firstScan;

	@Column(name="first_time")
	private String firstTime;

	@Column(name="last_cycle")
	private String lastCycle;

	@Column(name="last_scan")
	private String lastScan;

	@Column(name="last_time")
	private String lastTime;

	@Column(name="raw_file_name")
	private String rawFileName;
	
	@Column(name="name")
	private String name;

    public SpectrumTitleParsingRule() {
    }

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getFirstCycle() {
		return this.firstCycle;
	}

	public void setFirstCycle(String firstCycle) {
		this.firstCycle = firstCycle;
	}

	public String getFirstScan() {
		return this.firstScan;
	}

	public void setFirstScan(String firstScan) {
		this.firstScan = firstScan;
	}

	public String getFirstTime() {
		return this.firstTime;
	}

	public void setFirstTime(String firstTime) {
		this.firstTime = firstTime;
	}

	public String getLastCycle() {
		return this.lastCycle;
	}

	public void setLastCycle(String lastCycle) {
		this.lastCycle = lastCycle;
	}

	public String getLastScan() {
		return this.lastScan;
	}

	public void setLastScan(String lastScan) {
		this.lastScan = lastScan;
	}

	public String getLastTime() {
		return this.lastTime;
	}

	public void setLastTime(String lastTime) {
		this.lastTime = lastTime;
	}

	public String getRawFileName() {
		return this.rawFileName;
	}

	public void setRawFileName(String rawFileName) {
		this.rawFileName = rawFileName;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

}
package fr.proline.core.orm.uds;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the admin_infos database table.
 * 
 */
@Entity
@Table(name="admin_infos")
public class AdminInformation implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	@Column(name="model_version")
	private String modelVersion;

	private String configuration;

	@Column(name="db_creation_date")
	private Integer dbCreationDate;

	@Column(name="model_update_date")
	private Integer modelUpdateDate;

    public AdminInformation() {
    }

	public String getModelVersion() {
		return this.modelVersion;
	}

	public void setModelVersion(String modelVersion) {
		this.modelVersion = modelVersion;
	}

	public String getConfiguration() {
		return this.configuration;
	}

	public void setConfiguration(String configuration) {
		this.configuration = configuration;
	}

	public Integer getDbCreationDate() {
		return this.dbCreationDate;
	}

	public void setDbCreationDate(Integer dbCreationDate) {
		this.dbCreationDate = dbCreationDate;
	}

	public Integer getModelUpdateDate() {
		return this.modelUpdateDate;
	}

	public void setModelUpdateDate(Integer modelUpdateDate) {
		this.modelUpdateDate = modelUpdateDate;
	}

}
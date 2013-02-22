package fr.proline.core.orm.msi;

import java.io.Serializable;
import java.sql.Timestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;


/**
 * The persistent class for the admin_infos database table.
 * 
 */
@Entity(name="fr.proline.core.orm.msi.AdminInformation")
@Table(name="admin_infos")
public class AdminInformation implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@Column(name="model_version")
	private String modelVersion;

	@Column(name="db_creation_date")
	private Timestamp dbCreationDate;

	@Column(name="model_update_date")
	private Timestamp modelUpdateDate;

    public AdminInformation() {
    }

	public String getModelVersion() {
		return this.modelVersion;
	}

	public void setModelVersion(String modelVersion) {
		this.modelVersion = modelVersion;
	}

	public Timestamp getDbCreationDate() {
		return this.dbCreationDate;
	}

	public void setDbCreationDate(Timestamp dbCreationDate) {
		this.dbCreationDate = dbCreationDate;
	}

	public Timestamp getModelUpdateDate() {
		return this.modelUpdateDate;
	}

	public void setModelUpdateDate(Timestamp modelUpdateDate) {
		this.modelUpdateDate = modelUpdateDate;
	}

}
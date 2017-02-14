package fr.proline.core.orm.ps;

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
@Entity(name = "fr.proline.core.orm.ps.AdminInformation")
@Table(name = "admin_infos")
public class AdminInformation implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "model_version")
    private String modelVersion;

    @Column(name = "db_creation_date")
    private Timestamp dbCreationDate;

    @Column(name = "model_update_date")
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
	Timestamp result = null;

	if (dbCreationDate != null) {
	    result = (Timestamp) dbCreationDate.clone();
	}

	return result;
    }

    public void setDbCreationDate(final Timestamp pDbCreationDate) {

	if (pDbCreationDate == null) {
	    dbCreationDate = null;
	} else {
	    dbCreationDate = (Timestamp) pDbCreationDate.clone();
	}

    }

    public Timestamp getModelUpdateDate() {
	Timestamp result = null;

	if (modelUpdateDate != null) {
	    result = (Timestamp) modelUpdateDate.clone();
	}

	return result;
    }

    public void setModelUpdateDate(final Timestamp pModelUpdateDate) {

	if (pModelUpdateDate == null) {
	    modelUpdateDate = null;
	} else {
	    modelUpdateDate = (Timestamp) pModelUpdateDate.clone();
	}

    }

}

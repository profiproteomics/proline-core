package fr.proline.core.orm.msi;

import java.io.Serializable;
import javax.persistence.*;
import java.util.Date;


/**
 * The persistent class for the admin_infos database table.
 * 
 */
@Entity
@Table(name="admin_infos")
public class AdminInfo implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	@Column(name="model_version")
	private String modelVersion;

    @Temporal( TemporalType.DATE)
	@Column(name="db_creation_date")
	private Date dbCreationDate;

    @Temporal( TemporalType.DATE)
	@Column(name="model_update_date")
	private Date modelUpdateDate;

    public AdminInfo() {
    }

	public String getModelVersion() {
		return this.modelVersion;
	}

	public void setModelVersion(String modelVersion) {
		this.modelVersion = modelVersion;
	}

	public Date getDbCreationDate() {
		return this.dbCreationDate;
	}

	public void setDbCreationDate(Date dbCreationDate) {
		this.dbCreationDate = dbCreationDate;
	}

	public Date getModelUpdateDate() {
		return this.modelUpdateDate;
	}

	public void setModelUpdateDate(Date modelUpdateDate) {
		this.modelUpdateDate = modelUpdateDate;
	}

}
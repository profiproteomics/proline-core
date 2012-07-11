package fr.proline.core.orm.pdi;

import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * The persistent class for the seq_db_config database table.
 * 
 */
@Entity
@Table(name = "database_type")
public class DatabaseType implements Serializable {

	private static final long serialVersionUID = 1L;

	
	@Id
	private String type;


	protected DatabaseType() {

	}
	
	public DatabaseType(String type) {
		setType(type);
	}

	public String getType() {
		return this.type;
	}

	public void setType(String type) {
		this.type = type;
	}

}
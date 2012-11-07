package fr.proline.core.orm.uds;

import java.io.Serializable;
import javax.persistence.*;

/**
 * The persistent class for the activation database table.
 * 
 */
@Entity(name = "fr.proline.core.orm.uds.Activation")
@Table(name="activation")
public class Activation implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@Column(name="type")
	private String type;
	
    public Activation() {
    }

	public String getType() {
		return this.type;
	}

	public void setType(String type) {
		this.type = type;
	}

}
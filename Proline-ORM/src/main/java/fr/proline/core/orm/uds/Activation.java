package fr.proline.core.orm.uds;

import java.io.Serializable;
import javax.persistence.*;

/**
 * The persistent class for the activation database table.
 * 
 */
@Entity(name = "fr.proline.core.orm.uds.Activation")
@NamedQueries({
  @NamedQuery(
    name = "findActivationByType",
    query = "SELECT activ FROM fr.proline.core.orm.uds.Activation activ WHERE activ.type = :type"
  )
})
@Table(name="activation")
public class Activation implements Serializable {
	private static final long serialVersionUID = 1L;
	
    public enum ActivationType {
        CID, ECD, ETD, HCD, PSD
    };

	@Id
	@Column(name="type")
	@Enumerated(value = EnumType.STRING)
	private ActivationType type;
	
    public Activation() {
    }

	public ActivationType getType() {
		return this.type;
	}

	public void setType(ActivationType type) {
		this.type = type;
	}

}
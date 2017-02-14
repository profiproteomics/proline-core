package fr.proline.core.orm.uds;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;


/**
 * The persistent class for the fractionation database table.
 * 
 */
@Entity
@NamedQueries({
  @NamedQuery(
    name = "findFractionationByType",
    query = "SELECT frac FROM fr.proline.core.orm.uds.Fractionation frac WHERE frac.type = :type"
  )
})
@Table(name="fractionation")
public class Fractionation implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    public enum FractionationType {
	    PROTEIN, PEPTIDE, OTHER, NONE
    };

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private long id;

    @Column(name = "type")
    @Enumerated(value = EnumType.STRING)    
    private FractionationType type;
    
    public long getId() {
        return id;
    }
    
    public void setId(final long pId) {
        id = pId;
    }

    public FractionationType getType() {
        return type;
    }

    public void setType(FractionationType fractionationType) {
        this.type = fractionationType;
    }
    
        
}

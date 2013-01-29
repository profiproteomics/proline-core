package fr.proline.core.orm.uds;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;


/**
 * The persistent class for the fractionation database table.
 * 
 */
@Entity
@Table(name="fractionation")
public class Fractionation implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public enum FractionationType {
	PROTEIN, PEPTIDE, OTHER, NONE
    };


    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Integer id;

    @Column(name = "type")
    @Enumerated(value = EnumType.STRING)    
    private FractionationType fractionationType;
    
    public Integer getId() {
        return id;
    }
    
    public void setId(Integer id) {
        this.id = id;
    }

    public FractionationType getFractionationType() {
        return fractionationType;
    }

    public void setFractionationType(FractionationType fractionationType) {
        this.fractionationType = fractionationType;
    }
    
        
}

package fr.proline.core.orm.uds;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the ratio_definition database table.
 * 
 */
@Entity
@Table(name="ratio_definition")
public class RatioDefinition implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	private Integer number;

	//uni-directional many-to-one association to BiologicalGroup
    @ManyToOne
	private BiologicalGroup numerator;

	//uni-directional many-to-one association to BiologicalGroup
    @ManyToOne
	private BiologicalGroup denominator;

	//bi-directional many-to-one association to GroupSetup
    @ManyToOne
	@JoinColumn(name="group_setup_id")
	private GroupSetup groupSetup;

    public RatioDefinition() {
    }

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Integer getNumber() {
		return this.number;
	}

	public void setNumber(Integer number) {
		this.number = number;
	}

	public BiologicalGroup getNumerator() {
		return this.numerator;
	}

	public void setNumerator(BiologicalGroup numerator) {
		this.numerator = numerator;
	}
	
	public BiologicalGroup getDenominator() {
		return this.denominator;
	}

	public void setDenominator(BiologicalGroup denominator) {
		this.denominator = denominator;
	}
	
	public GroupSetup getGroupSetup() {
		return this.groupSetup;
	}

	public void setGroupSetup(GroupSetup groupSetup) {
		this.groupSetup = groupSetup;
	}
	
}
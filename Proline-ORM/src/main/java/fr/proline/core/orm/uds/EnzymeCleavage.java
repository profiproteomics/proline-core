package fr.proline.core.orm.uds;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the enzyme_cleavage database table.
 * 
 */
@Entity
@Table(name="enzyme_cleavage")
public class EnzymeCleavage implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	
	private String site;

	private String residues;

	@Column(name="restrictive_residues")
	private String restrictiveResidues;

	//bi-directional many-to-one association to Enzyme
    @ManyToOne
	private Enzyme enzyme;

    public EnzymeCleavage() {
    }

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getSite() {
		return this.site;
	}

	public void setSite(String site) {
		this.site = site;
	}

	public String getResidues() {
		return this.residues;
	}

	public void setResidues(String residues) {
		this.residues = residues;
	}

	public String getRestrictiveResidues() {
		return this.restrictiveResidues;
	}

	public void setRestrictiveResidues(String restrictiveResidues) {
		this.restrictiveResidues = restrictiveResidues;
	}

	public Enzyme getEnzyme() {
		return this.enzyme;
	}

	public void setEnzyme(Enzyme enzyme) {
		this.enzyme = enzyme;
	}
	
}